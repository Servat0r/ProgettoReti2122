package winsome.server;

import java.util.Map;

import winsome.common.config.ConfigParser;
import winsome.util.*;

/**
 * Main class for {@link WinsomeServer}.
 * @author Salvatore Correnti
 */
public final class WinsomeServerMain {
	/* Default config filename */
	public static final String CONFIG = "serverConfig.txt";
	
	public static void main(String[] args) {
		String config = (args.length > 0 ? args[0] : CONFIG);
		Debug.setDebug();
		Debug.setDbgStream("server.dbg");
		int exitCode = 0;
		WinsomeServer server = null;
		Thread t = null;
		Pair<Boolean, String> result = new Pair<>(false, "Error during execution");
		try {
			Map<String, String> configMap = ConfigParser.parseFile(config, ConfigParser.LOWER);
			if (WinsomeServer.createServer(configMap) && (server = WinsomeServer.getServer()) != null) {
				t = new Thread(new ShutdownHook());
				t.setName(ShutdownHook.DFLNAME);
				Runtime.getRuntime().addShutdownHook(t);
				result = server.mainloop();
				exitCode = (result.getKey() ? 0 : 1);
			} else exitCode = 1;
		} catch (Exception ex) { 
			if (server != null) server.logger().logStackTrace(ex);
			else ex.printStackTrace();
			exitCode = 1;
		} finally {
			try { t.join(); server.logger().log("Joined");}
			catch (Exception ex) {
				if (server != null) server.logger().logStackTrace(ex);
				else ex.printStackTrace();
				exitCode = 1;
			} finally {
				System.out.printf("%s%nMain exiting with code: %d%n", result.getValue(), exitCode);
				System.out.flush();
				Debug.exit(exitCode);
			}
		}
	}
}