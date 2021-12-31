package winsome.server.data;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;

import winsome.util.Common;

public final class Index<T extends Comparable<T>, V extends Indexable<T>> {
		
	private final NavigableSet<T> keys; //TODO Mutable
	private transient Map<T, V> map;
	private transient ReentrantReadWriteLock lock;
	
	public Index() {
		this.keys = new TreeSet<>();
		this.map = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
	}

	public synchronized boolean deserialize(Table<T, V> src) {
		Common.notNull(src);
		if (this.map == null) {
			NavigableSet<V> vals = src.get(keys);
			for (V val : vals) this.map.put(val.key(), val);
		}
		if (lock == null) lock = new ReentrantReadWriteLock();
		return true;
	}
	
	public V get(T key) {
		Common.notNull(key);
		try {
			lock.readLock().lock();
			return (keys.contains(key) ? this.map.get(key) : null);
		} finally { lock.readLock().unlock(); }
	}
		
	public boolean add(V val) {
		Common.notNull(val);
		try {
			lock.writeLock().lock();
			T key = val.key();
			boolean b = this.keys.add(key);
			return (b ? (this.map.putIfAbsent(val.key(), val) == null) : b);
		} finally { lock.writeLock().unlock(); }
	}
	
	public boolean contains(T key) {
		Common.notNull(key);
		try {lock.readLock().lock(); return this.keys.contains(key);} finally {lock.readLock().unlock();}
	}
	
	public boolean remove(T key) {
		Common.notNull(key);
		try {
			lock.writeLock().lock();
			boolean b = this.keys.remove(key);
			return (b ? (this.map.remove(key) != null) : b); 
		} finally {lock.writeLock().unlock();}
	}
	
	public List<V> getAll(){
		List<V> result = new ArrayList<>();
		try {
			lock.readLock().lock();
			for (V val : this.map.values()) result.add(val);
			return result;
		} finally { lock.readLock().unlock(); }
	}
		
	public NavigableSet<T> keySet(){
		try {lock.readLock().lock(); return Collections.unmodifiableNavigableSet(this.keys);}
		finally {lock.readLock().unlock();}
	}
	
	public boolean equals(Object obj) { return (this == obj); }

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append( this.getClass().getSimpleName() + " [");
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