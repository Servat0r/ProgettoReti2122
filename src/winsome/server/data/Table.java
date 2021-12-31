package winsome.server.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.locks.*;

import winsome.util.Common;

public class Table<T extends Comparable<T>, V extends Indexable<T>> {
	
	private final NavigableMap<T, V> map;
	private transient ReentrantReadWriteLock lock;
	private boolean prettyStrPrint;
	
	public Table(boolean prettyStrPrint) {
		this.map = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
		this.prettyStrPrint = prettyStrPrint;
	}
	
	public Table() { this(false); }
	
	public V put(V elem) {
		Common.notNull(elem);
		T key = elem.key();
		V oldVal = null;
		try {
			lock.writeLock().lock();
			if (this.contains(key)) oldVal = this.map.remove(key);
			this.map.put(key, elem);
			return oldVal;
		} finally { lock.writeLock().unlock(); }
	}
		
	public V putIfAbsent(V elem) {
		Common.notNull(elem);
		T key = elem.key();
		try {
			lock.writeLock().lock();
			return this.map.putIfAbsent(key, elem);
		} finally { lock.writeLock().unlock(); }
	}
	
	public V get(T key) {
		Common.notNull(key);
		try {
			lock.readLock().lock();
			return this.map.get(key);
		} finally { lock.readLock().unlock(); }
	}
	
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
	
	public V remove(T key) {
		Common.notNull(key);
		try {
			lock.writeLock().lock();
			return this.map.remove(key);
		} finally { lock.writeLock().unlock(); }
	}
	
	public Set<T> keySet(){
		try { lock.readLock().lock(); return this.map.keySet(); } finally { lock.readLock().unlock(); }
	}
	
	public Collection<V> getAll(){
		try { lock.readLock().lock(); return this.map.values(); } finally { lock.readLock().unlock(); }
	}
	
	public boolean contains(T key) {
		Common.notNull(key);
		try { lock.readLock().lock(); return this.map.containsKey(key); } finally { lock.readLock().unlock(); }
	}

	public synchronized boolean deserialize() {
		if (lock == null) lock = new ReentrantReadWriteLock();
		return true;
	}
	
	public NavigableSet<V> get(SortedSet<T> ext) {
		Common.notNull(ext);
		NavigableSet<V> result = new TreeSet<>();
		try {
			lock.readLock().lock();
			ext.retainAll(map.keySet());
			for (T key : ext) result.add(map.get(key));
			return result;
		} finally { lock.readLock().unlock(); }
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		String newl = (prettyStrPrint ? "\n" : "");
		try {
			lock.readLock().lock();
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					sb.append( (first ? ", " : "") + newl + f.getName() + " = " + f.get(this) );
					if (!first) first = true;
				}
			}
			sb.append(newl + "]");
			return sb.toString();		
		} catch (IllegalAccessException ex) { return null; }
		finally { lock.readLock().unlock(); }
	}
}