package winsome.server.action;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * A registry for monitoring each change in the state of the data hosted by the server that are used
 *  to calculate wincoin rewards, i.e.:
 *  	1) Creation of a post;
 *  	2) Rating of a post;
 *  	3) Comment of a post;
 *  	4) Elimination of a post.
 *  The approach is "transactional", i.e. the server is implemented such that each time a method that
 *  performs any of these actions, first puts the corresponding Action object into this registry 
 *  ({@link #putAction(Action)}, then executes effectively the action and finally: if the method call
 *  succeeded, the action is mark as ended ({@link #endAction(Action)}) and becomes eligible for reward
 *  calculations, otherwise it is removed from the registry ({@link #abortAction(Action)}).
 *  The thread that handles this registry periodically (with a specificable policy) scans the list of
 *  all actions removing the ones that are already marked as ended and calculates the rewards basing
 *  on them.
 * @author Salvatore Correnti
 * @see Action
 * @see WinsomeServer
 */
public final class ActionRegistry {
	/**
	 * State of the registry:
	 * 	1) INIT -> the registry has been initialized but not open, timeout is not set;
	 * 	2) OPEN -> the registry is "running", i.e. threads can invoke the {@link ActionRegistry#putAction(Action)},
	 * 	{@link ActionRegistry#endAction(Action)}, {@link ActionRegistry#endAction(Action)},
	 *  {@link ActionRegistry#abortAction(Action)}, {@link ActionRegistry#getActions(List)};
	 *  3) CLOSED -> the method closed has been invoked and so no more actions are "registerable".
	 */
	private static enum State { INIT, OPEN, CLOSED };
	
	/** Default wait value (1000 milliseconds). */
	private static final long DFLWAIT = 1000;
	
	/** Conversion map from MILLISECONDS to other TimeUnits not "less than" milliseconds. */
	private static final Map<String, Long> convMap = Common.newHashMapFromLists(
		Common.toList("DAYS", "HOURS", "MILLISECONDS", "MINUTES", "SECONDS"),
		Common.toList(86_400_000L, 3_600_000L, 1L, 60_000L, 1000L)
	);
	
	/** Default strategy for suspending a thread executing {@link #getActions(List)} on {@link #writeCond}. */
	public static final Predicate<ActionRegistry> timeoutOnlyWriteWait = (reg) -> (!reg.timeoutElapsed());
	
	/** Built-in strategies for updating timeouts. */
	public static final ToLongFunction<ActionRegistry>
		flatTimeout = (reg) -> (reg.now + reg.period),
		afterWriteTimeout = (reg) -> (System.currentTimeMillis() + reg.period);
	
	@NotNull
	private State state;
	@NotNull
	private long period, wait;
	private TimeUnit periodUnit, waitUnit;
	@NotNull
	private List<Action> registry;
	private long start, now;
	private transient ReentrantLock lock;
	private transient Condition readCond, writeCond;
	private Date timeout;
	@NotNull
	private ToLongFunction<ActionRegistry> timeoutPolicy;
	@NotNull
	private Predicate<ActionRegistry> writeWaitPolicy;
	
	/**
	 * Conversion of a period expressed in a TimeUnit into its equivalent in milliseconds.
	 * @param period Period.
	 * @param unit TimeUnit.
	 * @return The value of period converted in milliseconds.
	 */
	private long normalize(long period, TimeUnit unit) {
		String str = unit.toString();
		Long val = convMap.get(str);
		if (val != null) return period * val;
		else throw new IllegalArgumentException("Invalid TimeUnit");
	}
	
	/** @return true if the current time is greater than the timeout time. */
	private boolean timeoutElapsed() { return (System.currentTimeMillis() > this.now + this.period); }
	
	/** Sets next timeout. */
	private void setTimeout() { this.timeout = new Date(this.now + this.period); }
	
	/** While true, no-one can register any new action. */
	private boolean readerShouldWait() { return ( lock.hasWaiters(writeCond) && timeoutElapsed() ); }
	
	/**
	 * Initializes a new AcionRegistry.
	 * @param periodData Time extension of the timeout period.
	 * @param waitData Timeout for waiting for a {@link #putAction(Action)} call.
	 * @param timeoutPolicy Policy for calculating the next timeout.
	 * @param writeWaitPolicy Policy for waiting for a {@link #getActions(List)} call.
	 * @throws NullPointerException If any of periodData, period, wait is null.
	 * @throws IllegalArgumentException If period <= 0 or wait <= 0.
	 */
	public ActionRegistry(Pair<Long, TimeUnit> periodData, Pair<Long, TimeUnit> waitData, 
		ToLongFunction<ActionRegistry> timeoutPolicy, Predicate<ActionRegistry> writeWaitPolicy) {
		
		Common.notNull(periodData, periodData.getKey(), waitData.getKey());
		
		long period = periodData.getKey().longValue();
		TimeUnit periodUnit = periodData.getValue();
		
		long wait = waitData.getKey().longValue();
		TimeUnit waitUnit = waitData.getValue();
		
		Common.allAndArgs(period > 0, wait > 0);
		this.state = State.INIT;
		this.periodUnit = (periodUnit != null ? periodUnit : TimeUnit.MILLISECONDS);
		this.waitUnit = (waitUnit != null ? waitUnit : TimeUnit.MILLISECONDS);
		this.period = this.normalize(period, this.periodUnit);
		this.wait = this.normalize(wait, this.waitUnit);
		this.registry = new ArrayList<>();
		this.start = -1;
		this.now = -1;
		this.lock = new ReentrantLock();
		this.readCond = this.lock.newCondition();
		this.writeCond = this.lock.newCondition();
		this.timeout = null;
		this.timeoutPolicy = (timeoutPolicy != null ? timeoutPolicy : flatTimeout);
		this.writeWaitPolicy = (writeWaitPolicy != null ? writeWaitPolicy : timeoutOnlyWriteWait);
	}
	
	/** @see #ActionRegistry(Pair, Pair, ToLongFunction, Predicate) */
	public ActionRegistry(Pair<Long, TimeUnit> periodData, ToLongFunction<ActionRegistry> timeoutPolicy,
			Predicate<ActionRegistry> writeWaitPolicy) {
		this(periodData, new Pair<>(DFLWAIT, null), timeoutPolicy, writeWaitPolicy); }
	
	/** @see #ActionRegistry(Pair, Pair, ToLongFunction, Predicate) */
	public ActionRegistry(Pair<Long, TimeUnit> periodData, ToLongFunction<ActionRegistry> timeoutPolicy) {
		this(periodData, new Pair<>(DFLWAIT, null), timeoutPolicy, timeoutOnlyWriteWait);
	}
	
	/** @see #ActionRegistry(Pair, Pair, ToLongFunction, Predicate) */
	public ActionRegistry(Pair<Long, TimeUnit> periodData, Predicate<ActionRegistry> writeWaitPolicy) {
		this(periodData, new Pair<>(DFLWAIT, null), flatTimeout, writeWaitPolicy);
	}
	
	/** @see #ActionRegistry(Pair, Pair, ToLongFunction, Predicate) */
	public ActionRegistry(Pair<Long, TimeUnit> periodData) {
		this(periodData, new Pair<>(DFLWAIT, null), flatTimeout, timeoutOnlyWriteWait);
	}
	
	/** @see #ActionRegistry(Pair, Pair, ToLongFunction, Predicate) */
	public ActionRegistry(long period) { this(new Pair<>(period, null), new Pair<>(DFLWAIT, null), null, null); }
	
	/**
	 * Opens the registry, enabling calls of {@link #putAction(Action)}, {@link #endAction(Action)},
	 *  {@link #abortAction(Action)}, {@link #getActions(List)}. Successive invocations of this method
	 *  will have no effect.
	 * @return true if it is the first time that this method is invoked, false otherwise.
	 */
	public boolean open() {
		try {
			this.lock.lock();
			if (this.state == State.INIT) {
				this.state = State.OPEN;
				this.start = System.currentTimeMillis();
				this.now = this.start;
				this.setTimeout();
				return true;
			} else return false;
		} finally { this.lock.unlock(); }
	}
	
	/**
	 * Closes the registry, such that no new action can be registered. Successive invocations of this method
	 *  will have no effect.
	 * @return true if it is the first time that this method is invoked, false otherwise.
	 */
	public boolean close() {
		try {
			this.lock.lock();
			if (this.state == State.OPEN) {
				this.state = State.CLOSED;
				readCond.signalAll();
				writeCond.signalAll();
				return true;
			} else return false;
		} finally { this.lock.unlock(); }
	}
	
	/**
	 * Register an action before "executing" it on the server.
	 * @param a The action.
	 * @return true if the state is not closed and the action is successfully registered, false otherwise.
	 * @throws InterruptedException If an interruption occurs.
	 * @throws NullPointerException If action is null.
	 */
	public boolean putAction(Action a) throws InterruptedException {
		Common.notNull(a);
		try {
			this.lock.lock();
			while ((this.state != State.CLOSED) && this.readerShouldWait())
				this.readCond.await(this.wait, this.waitUnit);
			if (this.state == State.CLOSED) return false;
			registry.add(a);
			return true;
		}
		finally { this.lock.unlock(); }
	}
	
	/**
	 * Signals that a registered action has successfully completed and so can be considered for rewards calculating.
	 * @param a The action.
	 * @return true if the state is not closed and the action is successfully marked as completed, false otherwise.
	 * @throws NullPointerException If action is null.
	 */
	public boolean endAction(Action a) {
		Common.notNull(a);
		try {
			this.lock.lock();
			if (!registry.contains(a)) return false;
			a.markEnded();
			if (lock.hasWaiters(writeCond)) writeCond.signal();
			else if (lock.hasWaiters(readCond)) readCond.signalAll();
			return true;
		} finally { this.lock.unlock(); }
	}
	
	/**
	 * Signals that a registered action has failed to complete and removes it from the registry.
	 * @param a The action.
	 * @return true if the action was previously registered, false otherwise.
	 * @throws NullPointerException If action is null.
	 */
	public boolean abortAction(Action a) {
		Common.notNull(a);
		try {
			this.lock.lock();
			return registry.remove(a);
		} finally { this.lock.unlock(); }
	}
	
	/**
	 * Retrieves from the action list all the actions that has been marked as ended with {@link #endAction(Action)}
	 *  appending them to the list provided.
	 * @param l List of actions.
	 * @return true on success if the state is not closed, false otherwise.
	 * @throws NullPointerException If l is null.
	 */
	public boolean getActions(List<Action> l) throws InterruptedException {
		Common.notNull(l);
		try {
			this.lock.lock();
			while ((this.state != State.CLOSED) && writeWaitPolicy.test(this)) this.writeCond.awaitUntil(timeout);
			if (this.state == State.CLOSED) {
				l.addAll(registry); registry.clear(); return false; }
			Action a;
			Iterator<Action> iter = registry.iterator();
			while (iter.hasNext()) {
				a = iter.next();
				if (a.isEnded() && a.getEndTime() <= this.now + this.period) {
					iter.remove();
					l.add(a);
				}
			}
			this.now = timeoutPolicy.applyAsLong(this);
			this.setTimeout();
			if (lock.hasWaiters(readCond)) readCond.signalAll();
			return true;
		} finally { this.lock.unlock(); }
	}
	
	@NotNull
	public String toString() { return Common.jsonString(this); }
}