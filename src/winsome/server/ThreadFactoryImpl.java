package winsome.server;

import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantLock;

import winsome.server.data.Logger;
import winsome.util.IDGen;

final class ThreadFactoryImpl implements ThreadFactory {

	private final IDGen gen = new IDGen(1);
	private final List<Thread> workers = new ArrayList<>();
	private final ReentrantLock lock = new ReentrantLock();
	
	private final Thread gc = new Thread() {
		public void run() {
			Logger logger = WinsomeServer.getServer().logger();
			while (true) {
				try {
					Thread.sleep(10_000);
					StringBuilder sb = new StringBuilder(" ");
					lock.lock();
					Iterator<Thread> iter = workers.iterator();
					while (iter.hasNext()) {
						Thread t = iter.next();
						if (!t.isAlive()) {
							sb.append(t.getName().substring(8) + " ");
							iter.remove();
						}
					}
					logger.log("Removed workers: {%s}", sb.toString());
				} catch (InterruptedException ie) { logger.logStackTrace(ie); return; }
				finally { lock.unlock(); }
			}
		}
	};
	
	
	public ThreadFactoryImpl() { this.gc.setName("GC"); this.gc.start(); }
	
	public synchronized Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setName("Worker #" + gen.nextId());
		workers.add(t);
		return t;
	}
	
	public synchronized final void interruptAll() {
		gc.interrupt();
		Iterator<Thread> iter = workers.iterator();
		Thread t;
		while (iter.hasNext()) {
			t = iter.next();
			if (t.isAlive()) t.interrupt(); else iter.remove();
		}
	}
	
	public synchronized final void joinAll() throws InterruptedException {
		gc.join();
		for (Thread t : workers) t.join();
	}
	
	public synchronized final void clearList() { workers.clear(); }
}