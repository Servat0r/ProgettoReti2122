package winsome.server.data;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;

import com.google.gson.reflect.TypeToken;

import winsome.annotations.NotNull;
import winsome.util.*;

public final class Wallet implements Indexable<String> {
	
	private static final String TRANSMARK = "#", SEPAR = " : ";	
	
	public static final Type TYPE = new TypeToken<Wallet>() {}.getType();
	
	private String owner;
	private double value;
	private NavigableMap<Long, Double> history;
	private transient ReentrantReadWriteLock lock;
		
	public synchronized void deserialize() throws DeserializationException {
		if (lock == null) lock = new ReentrantReadWriteLock();
	}
	
	public synchronized boolean isDeserialized() { return (lock != null); }
	
	public Wallet(String owner) {
		Common.notNull(owner);
		this.owner = new String(owner);
		this.value = 0.0;
		this.history = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
	}
	
	public String key() { return owner; }
	
	@NotNull
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
		Common.andAllArgs(time > 0, value >= 0.0);
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
	
	@NotNull
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			if (lock != null) lock.readLock().lock();
			return String.format("%s : %s", this.getClass().getSimpleName(), Serialization.GSON.toJson(this));
		} finally { if (lock != null) lock.readLock().unlock(); }
	}
}