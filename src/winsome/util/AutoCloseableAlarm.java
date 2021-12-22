package winsome.util;

/**
 * A general task for closing a resource after a timeout.
 * @author Salvatore Correnti.
 *
 */
public final class AutoCloseableAlarm implements Runnable {

	private AutoCloseable resource;
	private int timeval;
	
	
	public AutoCloseableAlarm(AutoCloseable resource, int timeval) {
		if (resource == null || timeval <= 0) throw new IllegalArgumentException(); 
		this.resource = resource;
		this.timeval = timeval;
	}
		
	public void run() {
		try {
			Thread.sleep(this.timeval);
			this.resource.close();
		} catch (Exception e) {}
	}
}