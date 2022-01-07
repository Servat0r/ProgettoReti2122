package winsome.server.data;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.locks.*;

import com.google.gson.reflect.TypeToken;

import winsome.annotations.NotNull;
import winsome.util.Common;
import winsome.util.Serialization;

public class Table<T extends Comparable<T>, V extends Indexable<T>> implements Iterable<V> {
	
	private final NavigableMap<T, V> map;
	private transient ReentrantReadWriteLock lock = null;
	private transient Type type = null;
	
	public Table() {
		this.map = new TreeMap<>();
		this.lock = new ReentrantReadWriteLock();
		this.type = new TypeToken<Table<T,V>>(){}.getType();
	}
	
	
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
		
	public boolean putIfAbsent(V elem) {
		Common.notNull(elem);
		T key = elem.key();
		try {
			lock.writeLock().lock();
			return (this.map.putIfAbsent(key, elem) == null);
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
	
	@NotNull
	public Set<T> keySet(){
		try { lock.readLock().lock(); return this.map.keySet(); } finally { lock.readLock().unlock(); }
	}
	
	@NotNull
	public Collection<V> getAll(){
		try { lock.readLock().lock(); return this.map.values(); } finally { lock.readLock().unlock(); }
	}
	
	public boolean contains(T key) {
		Common.notNull(key);
		try { lock.readLock().lock(); return this.map.containsKey(key); } finally { lock.readLock().unlock(); }
	}
	
	public synchronized void deserialize() throws DeserializationException {
		if (lock == null) lock = new ReentrantReadWriteLock();
		if (type == null) type = new TypeToken<Table<T,V>>(){}.getType();
	}
	
	public synchronized boolean isDeserialized() { return (lock != null && type != null); }
	
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
	
	public Iterator<V> iterator() { return this.map.values().iterator(); }
}