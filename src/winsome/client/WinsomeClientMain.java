package winsome.client;

import winsome.common.config.*;
import winsome.util.Debug;

final class WinsomeClientMain {

	public static final String CONFIG = "clientConfig.txt";
	
	public static void main(String[] args) {
		String config = (args.length > 0 ? args[0] : CONFIG);
		Debug.setDebug();
		//Debug.setDbgStream("client.dbg");
		WinsomeClient client = null;
		int exitCode = 0;
		try {
			client = new WinsomeClient(ConfigParser.parseFile(config, ConfigParser.LOWER));
			exitCode = client.mainloop();
		} catch (Exception ex) {
			client.logger().logStackTrace(ex);
			exitCode = 1;
		} finally {
			try { client.close(); }
			catch (Exception exc) {client.logger().logStackTrace(exc); exitCode = 1; }
		}
		Debug.exit(exitCode);
	}
}