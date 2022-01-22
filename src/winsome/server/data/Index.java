package winsome.server.data;

import java.util.*;
import java.util.concurrent.locks.*;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * A class for "indexing" items in a table.
 * @author Salvatore Correnti
 *
 * @param <T> Type of key.
 * @param <V> type of item.
 */
public final class Index<T extends Comparable<T>, V extends Indexable<T>> {
		
	@NotNull
	private final NavigableSet<T> keys;
	private transient Table<T, V> table;
	private transient ReentrantReadWriteLock lock;
	
	public Index(Table<T,V> table) {
		this.keys = new TreeSet<>();
		this.table = table;
		this.lock = new ReentrantReadWriteLock();
	}
		
	/**
	 * @param key The key.
	 * @return The item in the table with the given key if present, null otherwise.
	 */
	public V get(T key) {
		Common.notNull(key);
		try {
			lock.readLock().lock();
			return (keys.contains(key) ? this.table.get(key) : null);
		} finally { lock.readLock().unlock(); }
	}
	
	/**
	 * Tries to add the given key to the set of keys of the index.
	 * @param key The key.
	 * @return true on success, false otherwise.
	 */
	public boolean add(T key) {
		Common.notNull(key);
		try {
			lock.writeLock().lock();
			return this.keys.add(key);
		} finally { lock.writeLock().unlock(); }
	}
	
	/**
	 * @param key The key.
	 * @return true if {@link #keys} contains key, false otherwise.
	 */
	public boolean contains(T key) {
		Common.notNull(key);
		try {lock.readLock().lock(); return this.keys.contains(key);} finally {lock.readLock().unlock();}
	}
	
	/**
	 * Attempts to remove the given key from the maintained keys of this index.
	 * @param key The key to remove.
	 * @return true if the key was contained in {@link #keys} and removed, false otherwise.
	 */
	public boolean remove(T key) {
		Common.notNull(key);
		try {
			lock.writeLock().lock();
			return this.keys.remove(key);
		} finally {lock.writeLock().unlock();}
	}
	
	/**
	 * @return A set of all items in the associated table whose key is contained in {@link #keys},
	 *  and automatically updates it eliminating the other ones as specified in {@link Table#get(SortedSet)}.
	 */
	@NotNull
	public NavigableSet<V> getAll(){
		NavigableSet<V> result = new TreeSet<>();
		try {
			lock.readLock().lock();
			result.addAll(this.table.get(keys));
			return result;
		} finally { lock.readLock().unlock(); }
	}
	
	
	/** @return An unmodifiable set containing all the keys of this index. */
	@NotNull
	public NavigableSet<T> unmodifiableKeySet(){
		try {lock.readLock().lock(); return Collections.unmodifiableNavigableSet(this.keys);}
		finally {lock.readLock().unlock();}
	}
	
	/** @return A modifiable set containing all the keys of this index. */
	@NotNull
	public NavigableSet<T> keySet(){
		NavigableSet<T> result = new TreeSet<>();
		try {
			lock.readLock().lock();
			result.addAll(this.keys);
			return result;
		} finally {lock.readLock().unlock();}
	}
	
	public boolean equals(Object obj) { return (this == obj); }
	
	public String toString() { return Common.jsonString(this); }
}