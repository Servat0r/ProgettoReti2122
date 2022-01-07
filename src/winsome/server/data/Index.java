package winsome.server.data;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;

import winsome.annotations.NotNull;
import winsome.util.Common;

public final class Index<T extends Comparable<T>, V extends Indexable<T>> {
		
	private final NavigableSet<T> keys; //TODO Mutable
	private transient Table<T, V> table;
	private transient ReentrantReadWriteLock lock;
	
	public Index(Table<T,V> table) {
		Common.notNull(table);
		this.keys = new TreeSet<>();
		this.table = table;
		this.lock = new ReentrantReadWriteLock();
	}

	public synchronized boolean isDeserialized() { return (table != null && lock != null); }
	
	public synchronized void deserialize(Table<T, V> table) throws DeserializationException {
		Common.notNull(table);
		if (!table.isDeserialized()) throw new DeserializationException();
		if (this.table == null) this.table = table;
		this.keys.retainAll(table.keySet());
		if (lock == null) lock = new ReentrantReadWriteLock();
	}
	
	public V get(T key) {
		Common.notNull(key);
		try {
			lock.readLock().lock();
			return (keys.contains(key) ? this.table.get(key) : null);
		} finally { lock.readLock().unlock(); }
	}
		
	public boolean add(T key) {
		Common.notNull(key);
		try {
			lock.writeLock().lock();
			return this.keys.add(key);
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
			return this.keys.remove(key);
		} finally {lock.writeLock().unlock();}
	}
	
	@NotNull
	public NavigableSet<V> getAll(){
		NavigableSet<V> result = new TreeSet<>();
		try {
			lock.readLock().lock();
			result.addAll(this.table.get(keys));
			return result;
		} finally { lock.readLock().unlock(); }
	}
	
	
	@NotNull
	public NavigableSet<T> unmodifiableKeySet(){
		try {lock.readLock().lock(); return Collections.unmodifiableNavigableSet(this.keys);}
		finally {lock.readLock().unlock();}
	}
	
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

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append( this.getClass().getSimpleName() + " [");
		try {
			lock.readLock().lock();
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			Object currObj;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					try {currObj = f.get(this); } catch (Exception ex) { continue; }
					sb.append( (first ? ", " : "") + f.getName() + " = " + currObj );
					if (!first) first = true;
				}
			}
			sb.append("]");
			return sb.toString();
		} finally { lock.readLock().unlock(); }
	}
}