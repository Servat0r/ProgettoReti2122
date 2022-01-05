package winsome.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import winsome.annotations.NotNull;

public final class RWLinkedBlockingQueue<T> {
	
	@NotNull
	private final ReentrantReadWriteLock lock;
	@NotNull
	private final BlockingQueue<T> queue;
	
	public RWLinkedBlockingQueue() {
		this.lock = new ReentrantReadWriteLock();
		this.queue = new LinkedBlockingQueue<>();
	}
	
	public RWLinkedBlockingQueue(Collection<? extends T> coll) {
		this();
		this.queue.addAll(coll);
	}
	
	public void put(T elem) throws InterruptedException {
		try {
			lock.readLock().lock();
			queue.put(elem);
		} finally { lock.readLock().unlock(); }
	}
	
	public T take() throws InterruptedException {
		try {
			lock.readLock().lock();
			return queue.take();
		} finally { lock.readLock().unlock(); }
	}
		
	public int drainTo(Collection<? super T> coll) {
		try {
			lock.writeLock().lock();
			return queue.drainTo(coll);
		} finally { lock.writeLock().unlock(); }
	}
	
	public int size() {
		try {
			lock.readLock().lock();
			return queue.size();
		} finally { lock.readLock().unlock(); }
	}
}