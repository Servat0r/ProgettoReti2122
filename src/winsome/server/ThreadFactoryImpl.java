package winsome.server;

import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Custom implementation of thread factory for {@link WinsomeServer} workers pool.
 * It provides a list for tracking all generated threads and methods for interrupt them all and join them all.
 * @author Salvatore Correnti
 * @see WinsomeServer
 */
final class ThreadFactoryImpl implements ThreadFactory {

	private final AtomicLong gen = new AtomicLong(1);
	private final List<Thread> workers = new ArrayList<>();
	
	public ThreadFactoryImpl() { }
	
	public synchronized Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setName("Worker #" + gen.getAndIncrement());
		workers.add(t);
		return t;
	}
	
	/** Interrupts all alive workers (used in server for interrupt suspended workers) */
	public synchronized final void interruptAll() {
		Iterator<Thread> iter = workers.iterator();
		Thread t;
		while (iter.hasNext()) {
			t = iter.next();
			if (t.isAlive()) t.interrupt(); else iter.remove();
		}
	}
	
	/** Joins all alive workers (used in server to guarantee to wait for termination of all workers) */
	public synchronized final void joinAll() throws InterruptedException {
		for (Thread t : workers) t.join();
	}
}