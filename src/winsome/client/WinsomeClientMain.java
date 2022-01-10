package winsome.client;

import winsome.common.config.*;
import winsome.util.*;

/**
 * Main class form WinsomeClient.
 * @author Salvatore Correnti
 * @see WinsomeClient
 */
public final class WinsomeClientMain {

	public static final String
		CONFIG = "clientConfig.txt",
		INTRO = "This is the WinsomeClient. Type \"help\" for help on available commands or "
			+ "\"help <command>\" to get help on <command>, or any command to get it executed.",
		EXIT = "Thanks for having used WinsomeClient.";
		
	public static void main(String[] args) {
		String config = (args.length > 0 ? args[0] : CONFIG);
		Debug.setDebug();
		Debug.setDbgStream("client.dbg");
		WinsomeClient client = null;
		int exitCode = 0;
		try {
			client = new WinsomeClient(ConfigParser.parseFile(config, ConfigParser.LOWER));
			System.out.println(INTRO);
			exitCode = client.mainloop();
		} catch (Exception ex) {
			if (client != null) client.logger().logStackTrace(ex);
			else ex.printStackTrace();
			exitCode = -1;
		} finally {
			try { client.close(); }
			catch (Exception ex) { ex.printStackTrace(); }
			System.out.printf("%s%nMain exiting with code: %d%n", EXIT, exitCode);
			System.out.flush();
			Debug.exit(exitCode);
		}
	}
}