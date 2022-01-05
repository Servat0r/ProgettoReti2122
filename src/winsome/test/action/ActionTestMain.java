package winsome.test.action;

import java.util.*;
import java.util.concurrent.TimeUnit;

import winsome.server.action.*;
import winsome.util.*;

public final class ActionTestMain {

	private static long timeout = 5, conv = 500, nwrite = 2;
	
	private static long nread() { return 2 * timeout * nwrite; }
	
	private static ActionRegistry registry = new ActionRegistry( new Pair<>(timeout, TimeUnit.SECONDS) );
	
	private static IDGen gen = new IDGen();
		
	private static Runnable read = new Runnable() {
		public void run() {
			String name = Thread.currentThread().getName();
			Common.println(name + " started @ " + new Date());
			Action a = Action.newLike("user", gen.nextId());
			try { Thread.sleep(conv * timeout); registry.putAction(a); }
			catch (Exception e) { e.printStackTrace(); }
			Common.println(name + " ended @ " + new Date());
		}
	};
	
	private static Runnable write = new Runnable() {
		public void run() {
			try {
				String name = Thread.currentThread().getName();
				Common.println(name + " started @ " + new Date());
				List<Action> l = new ArrayList<>();
				for (int i = 0; i < nwrite; i++) {
					Common.println(name + " iteration #" + i + " started @ " + new Date());
					System.out.println("scan = " + registry.getActions(l) + "\n" + l);
					l.clear();
					Common.println(name + " iteration #" + i + " ended @ " + new Date());
				}
			} catch (Exception ex) { ex.printStackTrace(); }
		}
	};
	
	public static void main(String[] args) throws Exception {
		final long N = nread();
		Thread[] readers = new Thread[(int)N];
		Thread writer = new Thread(write);
		writer.setName("Writer");
		registry.open();
		Common.println(registry);
		writer.start();
		for (int i = 0; i < N; i++) {
			readers[i] = new Thread(read);
			readers[i].setName("Reader #" + i);
			readers[i].start();
			if (i == Math.min(14, N/2)) { registry.close(); Common.println("Closed registry @ " + new Date()); }
			Thread.sleep(1000);
		}
		registry.close();
		Common.println("Joining readers @ " + new Date());
		for (Thread t : readers) t.join();
		Common.println("Joining writer @ " + new Date());
		writer.join();
		Common.println("Ended @ " + new Date());
		Common.println(registry);
	}
}