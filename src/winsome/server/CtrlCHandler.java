package winsome.server;

import winsome.util.Debug;

final class CtrlCHandler implements Runnable {

	public static final String
		DFLNAME = "ShutdownHandler",
		DFLMSG = "Interrupt signal received",
		TERM = "InterruptHandler terminating";
	
	private final WinsomeServer server;
	private final String interruptMsg;
	private boolean received;
	
	public CtrlCHandler(WinsomeServer server, String fmt, Object...objects) {
		this.server = server;
		this.interruptMsg = String.format(fmt, objects);
		this.received = false;
	}
	
	public CtrlCHandler(WinsomeServer server) { this(server, DFLMSG); }
	
	public void run() {
		synchronized (this) {
			if (!received) { server.logger().log(interruptMsg); received = true; } else return;
		}
		try {  server.close(); }
		catch (Exception ex) { Debug.debugExc(ex); ex.printStackTrace(); }
		finally { server.logger().log(TERM); }
	}
}