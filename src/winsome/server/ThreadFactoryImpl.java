package winsome.server;

import java.util.concurrent.ThreadFactory;

import winsome.util.IDGen;

final class ThreadFactoryImpl implements ThreadFactory {

	private final IDGen gen = new IDGen(1);
	
	public ThreadFactoryImpl() {}
	
	public Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setName("Worker #" + gen.nextId());
		return t;
	}
}