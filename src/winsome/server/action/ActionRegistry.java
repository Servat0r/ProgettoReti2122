package winsome.server.action;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

import winsome.annotations.NotNull;
import winsome.util.*;

public final class ActionRegistry {
	
	private static enum State { INIT, OPEN, CLOSED };
	
	private static final long DFLWAIT = 1000;
	
	private static final Map<String, Long> convMap = Common.newHashMapFromLists(
		Arrays.asList("DAYS", "HOURS", "MILLISECONDS", "MINUTES", "SECONDS"),
		Arrays.asList((long)86_400_000, (long)3_600_000, (long)1, (long)60_000, (long)1000)
	);
	
	public static final Predicate<ActionRegistry> timeoutOnlyWriteWait = (reg) -> (!reg.timeoutElapsed());
	
	public static final ToLongFunction<ActionRegistry>
		flatTimeout = (reg) -> (reg.now + reg.period),
		afterWriteTimeout = (reg) -> (System.currentTimeMillis() + reg.period);
	
	private State state;
	@NotNull
	private long period, wait;
	private TimeUnit periodUnit, waitUnit;
	private List<Action> registry;
	private long start, now;
	private final ReentrantLock lock;
	private final Condition readCond, writeCond;
	private Date timeout;
	@NotNull
	private ToLongFunction<ActionRegistry> timeoutPolicy;
	@NotNull
	private Predicate<ActionRegistry> writeWaitPolicy;
	
	private long normalize(long period, TimeUnit unit) { //unit -> milliseconds (e.g. seconds -> milliseconds)
		String str = unit.toString();
		Long val = convMap.get(str);
		if (val != null) return period * val;
		else throw new IllegalArgumentException();
	}
	
	private long denormalize(long period, TimeUnit unit) { //milliseconds -> unit
		String str = unit.toString();
		Long val = convMap.get(str);
		if (val != null) return period/val;
		else throw new IllegalArgumentException();
	}
	
	private boolean timeoutElapsed() { return (System.currentTimeMillis() > this.now + this.period); }
	
	private void setTimeout() { this.timeout = new Date(this.now + this.period); }
	
	private boolean readerShouldWait() { return ( lock.hasWaiters(writeCond) && timeoutElapsed() ); }
	
	public ActionRegistry(Pair<Long, TimeUnit> periodData, Pair<Long, TimeUnit> waitData, 
		ToLongFunction<ActionRegistry> timeoutPolicy, Predicate<ActionRegistry> writeWaitPolicy) {
		
		Common.notNull(periodData, periodData.getKey(), waitData.getKey());
		
		long period = periodData.getKey().longValue();
		TimeUnit periodUnit = periodData.getValue();
		
		long wait = waitData.getKey().longValue();
		TimeUnit waitUnit = waitData.getValue();
		
		Common.andAllArgs(period > 0, wait > 0);
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
	
	public ActionRegistry(Pair<Long, TimeUnit> periodData, ToLongFunction<ActionRegistry> timeoutPolicy,
			Predicate<ActionRegistry> writeWaitPolicy) {
		this(periodData, new Pair<>(DFLWAIT, null), timeoutPolicy, writeWaitPolicy); }
	
	public ActionRegistry(Pair<Long, TimeUnit> periodData, ToLongFunction<ActionRegistry> timeoutPolicy) {
		this(periodData, new Pair<>(DFLWAIT, null), timeoutPolicy, timeoutOnlyWriteWait);
	}
	
	public ActionRegistry(Pair<Long, TimeUnit> periodData, Predicate<ActionRegistry> writeWaitPolicy) {
		this(periodData, new Pair<>(DFLWAIT, null), flatTimeout, writeWaitPolicy);
	}
	
	public ActionRegistry(Pair<Long, TimeUnit> periodData) {
		this(periodData, new Pair<>(DFLWAIT, null), flatTimeout, timeoutOnlyWriteWait);
	}
	
	public ActionRegistry(long period) { this(new Pair<>(period, null), new Pair<>(DFLWAIT, null), null, null); }
	
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
	
	public boolean close() {
		try {
			this.lock.lock();
			if (this.state == State.OPEN) {
				this.state = State.CLOSED;
				return true;
			} else return false;
		} finally { this.lock.unlock(); }
	}
	
	public boolean putAction(Action a) throws InterruptedException {
		Common.notNull(a);
		try {
			this.lock.lock();
			if (this.state == State.CLOSED) return false;
			while (this.readerShouldWait()) this.readCond.await(this.wait, this.waitUnit);
			if (this.state == State.CLOSED) return false;
			registry.add(a);
			return true;
		} finally { this.lock.unlock(); }
	}
	
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
	
	public boolean abortAction(Action a) {
		Common.notNull(a);
		try {
			this.lock.lock();
			return registry.remove(a);
		} finally { this.lock.unlock(); }
	}
	
	public boolean getActions(List<Action> l) throws InterruptedException {
		Common.notNull(l);
		try {
			this.lock.lock();
			if (this.state == State.CLOSED) { l.addAll(registry); registry.clear(); return false; }
			while (writeWaitPolicy.test(this)) this.writeCond.awaitUntil(timeout);
			if (this.state == State.CLOSED) { l.addAll(registry); registry.clear(); return false; }
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
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		Field[] fields = this.getClass().getDeclaredFields();
		boolean first = false;
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			Object obj;
			if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
				try {obj = f.get(this);}
				catch (IllegalAccessException ex) {continue;}
				String name = f.getName();
				if ( name.equals("start") || name.equals("now") ) obj = ((long)obj) % (now + period);
				else if (name.equals("period")) obj = this.denormalize(period, periodUnit);
				sb.append( (first ? ", " : "") + name + " = " + obj );
				if (!first) first = true;
			}
		}
		sb.append("]");
		return sb.toString();
	}
}