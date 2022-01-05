package winsome.server;

import winsome.common.config.ConfigParser;
import winsome.util.*;

final class WinsomeServerMain {

	public static final String CONFIG = "serverConfig.txt";
	
	public static void main(String[] args) {
		Debug.setDebug();
		Debug.setDbgStream("serverDebug.txt");
		int exitCode = 0;
		WinsomeServer server = null;
		try {
			server = WinsomeServer.newServer(ConfigParser.parseFile(CONFIG, ConfigParser.LOWER));
			Pair<Boolean, String> result = server.mainloop();
			exitCode = (result.getKey() ? 0 : 1);
			if (exitCode == 0) System.out.println(result.getValue());
			else System.err.println(result.getValue());
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			exitCode = 1;
		} finally { Debug.exit(exitCode); }
	}
}