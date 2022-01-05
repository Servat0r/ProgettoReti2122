package winsome.test.action;

import java.util.*;

import winsome.util.Common;

final class ThrExcTest {

	public static int pippo = 90;
	
	public static boolean mySleep(long millis) {
		try { Thread.sleep(2000); return true; } catch (InterruptedException ex) { ex.printStackTrace(); return false; }
	}
	
	public static void main(String[] args) {
		/*Thread t = new Thread() {
			public void run() {
				System.out.println("Hello, this is the thread " + this.getName());
				Common.andAllArgs(mySleep(2000));
				throw new IllegalStateException(
					Common.excStr("%s <%d, %s, %d>%n", "Pippo", this.getId(), this.getName(), pippo)
				);
			}
		};
		t.setName("CROZZA");
		t.start();*/
		Integer[] pippo = new Integer[] {2, 4, 6, null};
		List<Integer> pluto = Arrays.asList(pippo);
		for (Integer p : pippo) Common.println(p);
		for (Integer q : pluto) Common.println(q);
		//System.out.println(mySleep(10_000));
		System.out.println("Main finished, exception did not interrupt main!");
	}
}