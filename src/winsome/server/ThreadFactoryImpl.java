package winsome.server;

import java.util.*;
import java.util.concurrent.ThreadFactory;

import winsome.util.IDGen;

final class ThreadFactoryImpl implements ThreadFactory {

	private final IDGen gen = new IDGen(1);
	private final List<Thread> workers = new ArrayList<>();
	
	public ThreadFactoryImpl() { }
	
	public synchronized Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setName("Worker #" + gen.nextId());
		workers.add(t);
		return t;
	}
	
	public synchronized final void interruptAll() {
		Iterator<Thread> iter = workers.iterator();
		Thread t;
		while (iter.hasNext()) {
			t = iter.next();
			if (t.isAlive()) t.interrupt(); else iter.remove();
		}
	}
	
	public synchronized final void joinAll() throws InterruptedException {
		for (Thread t : workers) t.join();
	}	
}