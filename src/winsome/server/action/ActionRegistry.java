package winsome.server.action;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import winsome.util.Common;

public final class ActionRegistry {
	
	private static enum State {
		INIT,
		OPEN,
		CLOSED,
	};
	
	private static final long DFLWAIT = 1000;
	
	private static final Map<String, Long> convMap = Common.newHashMapFromLists(
		Arrays.asList("DAYS", "HOURS", "MILLISECONDS", "MINUTES", "SECONDS"),
		Arrays.asList((long)86_400_000, (long)3_600_000, (long)1, (long)60_000, (long)1000)
	);
	
	public static final int
		FLAT = 0,
		AFTERWRITE = 1;
		
	private State state;
	private long period;
	private TimeUnit periodUnit;
	private long wait;
	private TimeUnit waitUnit;
	private List<Action> registry;
	private long start;
	private long now;
	private final ReentrantLock lock;
	private final Condition readCond;
	private final Condition writeCond;
	private int policy;
	private Date timeout;
	
	private boolean timeoutElapsed() { return (System.currentTimeMillis() > this.now + this.period); }
	
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
	
	private void setTimeout() {
		this.timeout = new Date(this.now + this.period);
	}
	
	private boolean readerShouldWait() { return ( lock.hasWaiters(writeCond) && timeoutElapsed() ); }
	private boolean writerShouldWait() { return ( !timeoutElapsed() ); }
	
	public ActionRegistry(long period, TimeUnit periodUnit, long wait, TimeUnit waitUnit, int policy) {
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
	}
	
	public ActionRegistry(long period, TimeUnit periodUnit) { this(period, periodUnit, DFLWAIT, null, FLAT); }
	
	public ActionRegistry(long period) { this(period, null, DFLWAIT, null, FLAT); }
	
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
			a.markEnded();
			registry.add(a);
			if (lock.hasWaiters(writeCond)) writeCond.signal();
			else if (lock.hasWaiters(readCond)) readCond.signalAll();
			return true;
		} finally { this.lock.unlock(); }
	}
		
	public boolean getActions(List<Action> l) throws InterruptedException {
		Common.notNull(l);
		try {
			this.lock.lock();
			if (this.state == State.CLOSED) { l.addAll(registry); registry.clear(); return false; }
			while (writerShouldWait()) this.writeCond.awaitUntil(timeout);
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
			if (policy == FLAT) this.now = this.now + period;
			else if (policy == AFTERWRITE) this.now = System.currentTimeMillis();
			this.setTimeout();
			if (lock.hasWaiters(readCond)) readCond.signalAll();
			return true;
		} finally { this.lock.unlock(); }
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					Object obj = f.get(this);
					String name = f.getName();
					if ( name.equals("start") || name.equals("now") ) obj = ((long)obj) % (now + period);
					else if (name.equals("period")) obj = this.denormalize(period, periodUnit);
					sb.append( (first ? ", " : "") + name + " = " + obj );
					if (!first) first = true;
				}
			}
		} catch (IllegalAccessException ex) { return null; }
		sb.append("]");
		return sb.toString();
	}
}