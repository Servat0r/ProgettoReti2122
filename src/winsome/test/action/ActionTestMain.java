package winsome.test.action;

import java.util.*;
import java.util.concurrent.TimeUnit;

import winsome.server.action.*;
import winsome.util.Common;
import winsome.util.IDGen;

public final class ActionTestMain {

	private static int timeout = 5, conv = 500, nwrite = 2;
	
	private static int nread() { return 2 * timeout * nwrite; }
	
	private static ActionRegistry registry = new ActionRegistry(timeout, TimeUnit.SECONDS);
	
	private static IDGen gen = new IDGen();
		
	private static Runnable read = new Runnable() {
		public void run() {
			String name = Thread.currentThread().getName();
			Common.printLn(name + " started @ " + new Date());
			Action a = Action.newLike("user", gen.nextId());
			try { Thread.sleep(conv * timeout); registry.putAction(a); }
			catch (Exception e) { e.printStackTrace(); }
			Common.printLn(name + " ended @ " + new Date());
		}
	};
	
	private static Runnable write = new Runnable() {
		public void run() {
			try {
				String name = Thread.currentThread().getName();
				Common.printLn(name + " started @ " + new Date());
				List<Action> l = new ArrayList<>();
				for (int i = 0; i < nwrite; i++) {
					Common.printLn(name + " iteration #" + i + " started @ " + new Date());
					System.out.println("scan = " + registry.getActions(l) + "\n" + l);
					l.clear();
					Common.printLn(name + " iteration #" + i + " ended @ " + new Date());
				}
			} catch (Exception ex) { ex.printStackTrace(); }
		}
	};
	
	public static void main(String[] args) throws Exception {
		final int N = nread();
		Thread[] readers = new Thread[N];
		Thread writer = new Thread(write);
		writer.setName("Writer");
		registry.open();
		Common.printLn(registry);
		writer.start();
		for (int i = 0; i < N; i++) {
			readers[i] = new Thread(read);
			readers[i].setName("Reader #" + i);
			readers[i].start();
			if (i == Math.min(14, N/2)) { registry.close(); Common.printLn("Closed registry @ " + new Date()); }
			Thread.sleep(1000);
		}
		registry.close();
		Common.printLn("Joining readers @ " + new Date());
		for (Thread t : readers) t.join();
		Common.printLn("Joining writer @ " + new Date());
		writer.join();
		Common.printLn("Ended @ " + new Date());
		Common.printLn(registry);
	}
}