package winsome.server;

import java.util.Map;

import winsome.common.config.ConfigParser;
import winsome.util.*;

final class WinsomeServerMain {

	public static final String CONFIG = "serverConfig.txt";
	
	public static void main(String[] args) {
		Debug.setDebug();
		Debug.setDbgStream("server.dbg");
		int exitCode = 0;
		WinsomeServer server = null;
		Thread t = null;
		Pair<Boolean, String> result = new Pair<>(false, "Error during execution");
		try {
			Map<String, String> configMap = ConfigParser.parseFile(CONFIG, ConfigParser.LOWER);
			if (WinsomeServer.createServer(configMap) && (server = WinsomeServer.getServer()) != null) {
				t = new Thread(new CtrlCHandler(server));
				t.setName(CtrlCHandler.DFLNAME);
				Runtime.getRuntime().addShutdownHook(t);
				result = server.mainloop();
				exitCode = (result.getKey() ? 0 : 1);
			} else exitCode = 1;
		} catch (Exception ex) { Debug.debugExc(ex); exitCode = 1; }
		finally {
			try { t.join(); } catch (Exception ex) { Debug.debugExc(ex); }
			finally {
				System.out.println(result.getValue());
				System.out.printf("Main exiting with code %d%n", exitCode);
				Debug.exit(exitCode);
			}
		}
	}
}