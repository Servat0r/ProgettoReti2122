package winsome.server.data;

import java.util.*;
import java.util.concurrent.locks.*;

import com.google.gson.stream.*;
import com.google.gson.*;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * Wincoin wallet of a user.
 * @author Salvatore Correnti
 */
public final class Wallet implements Indexable<String> {
	
	private static final String TRANSMARK = "#", SEPAR = " : ";	
	
	public static final Gson gson() { return Serialization.GSON; }
	
	@NotNull
	private static final String timeConv(long time) {
		Calendar c = Calendar.getInstance();
		c.setTime(new Date(time));
		return Common.encodeDate(c);		
	}
		
	public static final Exception jsonSerializer(JsonWriter writer, Wallet wallet) {			
		try {
			writer.beginObject();
			Serialization.writeFields(gson(), writer, false, wallet, "owner", "round", "value");				
			
			Serialization.writeMap( writer, false, wallet.history, "history", Wallet::timeConv,
				(wr, d) -> { try { wr.value(d); return null; } catch (Exception ex) { return ex; } }
			);
			
			writer.endObject();
			return null;
		} catch (Exception ex) { return ex; }
	}
	
	public static final Wallet jsonDeserializer (JsonReader reader) {
		try {
			Wallet wallet = new Wallet("");
			
			reader.beginObject();				
			Serialization.readFields(gson(), reader, wallet, "owner", "round", "value");
			
			Serialization.readMap(reader, wallet.history, "history", str -> Common.decodeDate(str).getTimeInMillis(),
				rd -> gson().fromJson(rd, Double.class));
			
			reader.endObject();
			return wallet;
		} catch (Exception ex) { ex.printStackTrace(); return null; }
	}
	
	@NotNull
	private String owner;
	private int round = 4;
	private double value;
	@NotNull
	private NavigableMap<Long, Double> history;
	private transient ReentrantReadWriteLock lock;
		
	/**
	 * @param owner Username of the owner of the wallet.
	 */
	public Wallet(String owner, int round) {
		Common.notNull(owner);
		Common.allAndArgs(round > 0);
		this.owner = new String(owner);
		this.value = 0.0;
		this.round = round;
		this.history = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
	}
	
	public Wallet(String owner) { this(owner, 10); }
	
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
			value = Common.round(value, this.round);
			boolean result = (this.history.putIfAbsent(time, value) == null);
			if (result) this.value = Common.round(this.value + value, this.round);
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
		return Common.toString(this, gson(), Wallet::jsonSerializer, lock.readLock());
	}
}