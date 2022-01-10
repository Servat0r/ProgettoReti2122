package winsome.server.data;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;

import com.google.gson.reflect.TypeToken;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * Wincoin wallet of a user.
 * @author Salvatore Correnti
 */
public final class Wallet implements Indexable<String> {
	
	private static final String TRANSMARK = "#", SEPAR = " : ";	
	
	public static final Type TYPE = new TypeToken<Wallet>() {}.getType();
	
	@NotNull
	private String owner;
	private double value;
	@NotNull
	private NavigableMap<Long, Double> history;
	private transient ReentrantReadWriteLock lock;
	
	/**
	 * Restores transient fields after deserialization from JSON.
	 * @throws DeserializationException On failure.
	 */
	public synchronized void deserialize() throws DeserializationException {
		if (lock == null) lock = new ReentrantReadWriteLock();
	}
	
	/**
	 * @return True if transient fields are not restored after deserialization from JSON.
	 */
	public synchronized boolean isDeserialized() { return (lock != null); }
	
	/**
	 * @param owner Username of the owner of the wallet.
	 */
	public Wallet(String owner) {
		Common.notNull(owner);
		this.owner = new String(owner);
		this.value = 0.0;
		this.history = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
	}
	
	public String key() { return owner; }
	
	/**
	 * @return A list of formatted string from {@link #history} for printing history of transactions.
	 */
	@NotNull
	public List<String> history(){
		List<String> result = new ArrayList<>();
		try {
			lock.readLock().lock();
			NavigableSet<Long> transactions = this.history.descendingKeySet();
			Iterator<Long> iter = transactions.iterator();
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
	
	/**
	 * Adds a new transaction to history.
	 * @param time Time in milliseconds from Jan 01 1970 00:00:00 at which the transaction happened.
	 * @param value Value of the transaction.
	 * @return true on success, false if exists another transaction at the same time.
	 */
	public boolean newTransaction(long time, double value) {
		Common.allAndArgs(time > 0, value >= 0.0);
		try {
			lock.writeLock().lock();
			boolean result = (this.history.putIfAbsent(time, value) == null);
			if (result) this.value += value;
			return result;
		} finally { lock.writeLock().unlock(); }
	}
	
	/**
	 * Same as {@link #newTransaction(long, double)} but using {@link System#currentTimeMillis()}
	 *  for first parameter.
	 * @param value Value of the transaction.
	 * @return true on success, false if exists another transaction at the same time.
	 */
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