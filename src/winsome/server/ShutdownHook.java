package winsome.server;

/**
 * Shutdown hook for the server.
 * @author Salvatore Correnti
 * @see WinsomeServer
 */
final class ShutdownHook implements Runnable {

	public static final String
		DFLNAME = "ShutdownHandler",
		DFLMSG = "Interrupt signal received",
		TERM = "InterruptHandler terminating";
	
	private final String interruptMsg;
	private boolean received;
	
	public ShutdownHook(String fmt, Object...objects) {
		this.interruptMsg = String.format(fmt, objects);
		this.received = false;
	}
	
	public ShutdownHook() { this(DFLMSG); }
	
	public synchronized void run() {
		WinsomeServer server = WinsomeServer.getServer();
		if (server == null || received) return;
		server.logger().log(interruptMsg);
		received = true;
		try { server.close(); }
		catch (Exception ex) { server.logger().logStackTrace(ex); ex.printStackTrace(); }
	}
}