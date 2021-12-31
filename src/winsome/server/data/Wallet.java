package winsome.server.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.locks.*;

import winsome.util.Common;

public final class Wallet {

	private static IDGen gen = null;
		
	private static final String TRANSMARK = "#", SEPAR = " : ";	
	
	private final long id; //TODO Immutable
	private double value; //TODO Mutable(s)
	private final NavigableMap<Long, Double> history;
	private transient ReentrantReadWriteLock lock;
	
	public synchronized static void setGen(IDGen gen) {	if (Wallet.gen == null) Wallet.gen = gen; }
	
	public synchronized boolean deserialize() {
		if (lock == null) lock = new ReentrantReadWriteLock();
		return true;
	}
	
	public Wallet() {
		Common.notNull(Wallet.gen);
		this.id = Wallet.gen.nextId();
		this.value = 0.0;
		this.history = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
	}

	public Long key() { return id; }

	public List<String> history(){
		List<String> result = new ArrayList<>();
		try {
			lock.readLock().lock();
			NavigableSet<Long> transactions = this.history.descendingKeySet();
			Iterator<Long> iter = transactions.descendingIterator();
			int num = transactions.size();
			Long next;
			while (iter.hasNext()) {
				next = iter.next();
				result.add(			
					new String(TRANSMARK + num + SEPAR + new Date(next).toString() + SEPAR + "+" + history.get(next))
				);
				num--;
			}
			return result;
		} finally { lock.readLock().unlock(); }
	}
	
	public boolean newTransaction(long time, double value) {
		Common.checkAll(time > 0, value >= 0.0);
		try {
			lock.writeLock().lock();
			boolean result = (this.history.putIfAbsent(time, value) == null);
			if (result) this.value += value;
			return result;
		} finally { lock.writeLock().unlock(); }
	}
	
	public boolean newTransaction(double value) { return newTransaction(System.currentTimeMillis(), value); }
	
	public double value() {
		try { lock.readLock().lock(); return value; } finally { lock.readLock().unlock(); }
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			lock.readLock().lock();
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					sb.append( (first ? ", " : "") + f.getName() + " = " + f.get(this) );
					if (!first) first = true;
				}
			}
			sb.append("]");
			return sb.toString();		
		} catch (IllegalAccessException ex) { return null; }
		finally { lock.readLock().unlock(); }
	}
}