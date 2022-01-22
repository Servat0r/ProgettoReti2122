package winsome.server.data;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

import com.google.gson.*;
import com.google.gson.stream.*;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * A class representing a table of objects indexed by a unique "key", inspired from a database table.
 * @author Salvatore Correnti
 *
 * @param <T> Type of the key.
 * @param <V> Type of the objects.
 */
public class Table<T extends Comparable<T>, V extends Indexable<T>> {
	
	public static final String
		SIZE = "size",
		ITEMS = "items";
	
	@NotNull
	private final NavigableMap<T, V> map;
	private transient ReentrantReadWriteLock lock = null;
	
	public Table() {
		this.map = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
	}
		
	/**
	 * Puts the given element in the table if absent, otherwise returns the already existing
	 *  element with that key.
	 * @param elem The element to add.
	 * @return null if the element was absent, the already existing element as above otherwise.
	 * @throws NullPointerException If elem == null.
	 */
	public boolean add(V elem) {
		Common.notNull(elem);
		T key = elem.key();
		try {
			lock.writeLock().lock();
			return (this.map.putIfAbsent(key, elem) == null);
		} finally { lock.writeLock().unlock(); }
	}
	
	/**
	 * @param key The key.
	 * @return The element with the given key if present, null otherwise.
	 */
	public V get(T key) {
		Common.notNull(key);
		try {
			lock.readLock().lock();
			return this.map.get(key);
		} finally { lock.readLock().unlock(); }
	}
	
	/**
	 * @param key The key.
	 * @return The index in the table of the element with the given key
	 *  if present, -1 otherwise.
	 * @throws NullPointerException If key == null.
	 */
	public int index(T key) {
		Common.notNull(key);
		try {
			lock.readLock().lock();
			Iterator<T> iter = this.map.keySet().iterator();
			int index = 0;
			while (iter.hasNext()) {
				if (iter.next().equals(key)) return index;
				else index++;
			}
			return -1;
		} finally { lock.readLock().unlock(); }
	}
	
	/**
	 * Removes the element with the given key from the table.
	 * @param key The key.
	 * @return The element with the given key if present, null otherwise.
	 * @throws NullPointerException If key == null.
	 */
	public V remove(T key) {
		Common.notNull(key);
		try {
			lock.writeLock().lock();
			return this.map.remove(key);
		} finally { lock.writeLock().unlock(); }
	}
	
	/**
	 * @return An unmodifiable view of the set of keys of the elements stored in the table.
	 */
	@NotNull
	public Set<T> keySet(){
		try { lock.readLock().lock(); return this.map.keySet(); } finally { lock.readLock().unlock(); }
	}
	
	/**
	 * @return An unmodifiable view of the set of values of the elements stored in the table.
	 */
	@NotNull
	public Collection<V> getAll(){
		try { lock.readLock().lock(); return this.map.values(); } finally { lock.readLock().unlock(); }
	}
	
	/**
	 * @param key Given key.
	 * @return true if the table contains an element with given key.
	 * @throws NullPointerException If key == null.
	 */
	public boolean contains(T key) {
		Common.notNull(key);
		try { lock.readLock().lock(); return this.map.containsKey(key); } finally { lock.readLock().unlock(); }
	}
		
	/**
	 * @param ext Sorted set of keys.
	 * @param retain If true, removes from ext all the keys that do not have a corresponding
	 *  element in the table.
	 * @return A sorted set of all the elements contained in the table such that their
	 *  key is contained in ext.
	 */
	@NotNull
	public NavigableSet<V> get(SortedSet<T> ext, boolean retain) {
		Common.notNull(ext);
		NavigableSet<V> result = new TreeSet<>();
		SortedSet<T> set;
		try {
			lock.readLock().lock();
			if (retain) set = ext;
			else { set = new TreeSet<>(); set.addAll(ext); }
			set.retainAll(map.keySet());
			for (T key : set) result.add(map.get(key));
			return result;
		} finally { lock.readLock().unlock(); }
	}
	
	/** The same as {@link #get(SortedSet, boolean)} but with the second parameter as true. */
	@NotNull
	public NavigableSet<V> get(SortedSet<T> ext){ return this.get(ext, true); }
	
	@NotNull
	public String toString() {
		try {
			if (lock != null) lock.readLock().lock();
			String jsond = Serialization.GSON.toJson(this);
			return String.format("%s : %s", this.getClass().getSimpleName(), jsond);
		} finally { if (lock != null) lock.readLock().unlock(); }
	}
	
	/** @return An iterator over the keys in the table, in ascending order. */
	public Iterator<T> keysIterator() { return this.map.keySet().iterator(); }

	/** @return An iterator over the values in the table, in ascending order (by key). */
	public Table.Iter<T, V> valuesIterator() { return new Iter<>(this); }
	
	public static class Iter<T extends Comparable<T>, V extends Indexable<T>> implements Iterator<V> {
		
		private boolean scanning = false;
		private Table<T, V> table;
		private Iterator<V> iterator;
				
		public Iter(Table<T,V> table) { this.table = table; }
		
		public synchronized void open() {
			if (!scanning) {
				scanning = true;
				iterator = table.map.values().iterator();
				table.lock.readLock().lock();
			}
		}
		
		public synchronized boolean hasNext() { return scanning && iterator.hasNext(); }
		
		public synchronized V next() {
			try {
				if (scanning) return iterator.next();
				else return null;
			} catch (NoSuchElementException ex) {
				scanning = false;
				iterator = null;
				table.lock.readLock().unlock();
				throw ex;
			}
		}
		
		public synchronized void close() {
			if (scanning) {
				scanning = false;
				iterator = null;
				table.lock.readLock().unlock();
			}
		}
	}
	
	public static Gson gson() { return Serialization.GSON; }
	
	public void toJson(JsonWriter writer, BiFunction<JsonWriter, V, Exception> itemsSerializer) throws Exception {
		Common.notNull(writer, itemsSerializer);
		try {
			lock.readLock().lock();
			writer.beginObject();
			Serialization.writeColl(writer, false, this.getAll(), ITEMS, itemsSerializer);
			writer.endObject();
		} finally { lock.readLock().unlock(); }
	}
	
	public void toJson(String filename, BiFunction<JsonWriter, V, Exception> itemsSerializer) throws Exception {
		Common.notNull(filename, itemsSerializer);
		try (
			JsonWriter writer = Table.gson().newJsonWriter( new OutputStreamWriter(new FileOutputStream(filename)) )
		){ this.toJson(writer, itemsSerializer); }
	}
	
	public static <T extends Comparable<T>, V extends Indexable<T>> Table<T, V> fromJson(JsonReader reader,
			Function<JsonReader, V> itemsDeserializer) throws IOException, DeserializationException {
		Common.notNull(reader);
		Table<T, V> table = new Table<>();
		reader.beginObject();		
		
		String name = reader.nextName();
		if (!name.equals(ITEMS)) throw new DeserializationException(DeserializationException.JSONREAD);
		
		reader.beginArray();
		while (reader.hasNext()) table.add(itemsDeserializer.apply(reader));
		reader.endArray();
		
		reader.endObject();
		return table;
	}
	
	public static <T extends Comparable<T>, V extends Indexable<T>> Table<T, V> fromJson(String filename,
		Function<JsonReader, V> itemsDeserializer) throws IOException, DeserializationException {
		JsonReader reader = Table.gson().newJsonReader( new InputStreamReader(new FileInputStream(filename)) );
		Table<T,V> table = Table.fromJson(reader, itemsDeserializer);
		reader.close();
		return table;
	}
}