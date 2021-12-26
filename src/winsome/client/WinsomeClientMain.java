package winsome.client;

import winsome.common.config.ConfigParser;
import winsome.util.Common;

public final class WinsomeClientMain {

	public static void main(String[] args) {
		Common.setDebug(); Common.setDbgStream();
		WinsomeClient client = null;
		int exitCode = 0;
		try {
			client = new WinsomeClient(ConfigParser.parseFile("clientConfig.txt"));
			exitCode = client.mainloop();
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
			exitCode = 1;
		} finally {
			try { client.close(); } catch (Exception e) { exitCode = 1; }
		}
		System.exit(exitCode);
	}

}