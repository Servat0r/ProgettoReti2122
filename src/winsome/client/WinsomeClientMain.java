package winsome.client;

import winsome.common.config.*;
import winsome.util.Debug;

final class WinsomeClientMain {

	public static final String CONFIG_FNAME = "clientConfig.txt";
	
	public static void main(String[] args) {
		Debug.setDebug();
		//Debug.setDbgStream("client.dbg");
		WinsomeClient client = null;
		int exitCode = 0;
		try {
			client = new WinsomeClient(ConfigParser.parseFile(CONFIG_FNAME, ConfigParser.LOWER));
			exitCode = client.mainloop();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			exitCode = 1;
		} finally { try { client.close(); } catch (Exception e) { exitCode = 1; } }
		Debug.exit(exitCode);
	}
}