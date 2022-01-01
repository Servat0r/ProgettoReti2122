package winsome.server;

import winsome.common.config.ConfigParser;
import winsome.util.Common;

final class WinsomeServerMain {

	public static final String CONFIG = "serverConfig.txt";
	
	public static void main(String[] args) {
		Common.setDebug(); Common.setDbgFile("serverDebug.txt");
		//WinsomeServer server = null;
		int exitCode = 0;
		try (
			WinsomeServer server = WinsomeServer.newServer(ConfigParser.parseFile(CONFIG, ConfigParser.LOWER))
		){
			Common.printLn(server);
			server.serialize();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			exitCode = 1;
		}
		Common.exit(exitCode);
	}
}