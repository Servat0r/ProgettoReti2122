package winsome.test;

import java.util.*;

import winsome.common.config.*;

public final class ConfigParserTestMain {

	private static final String DFL_FNAME = "config.txt";
	
	public static void main(String[] args) {
		String filename = (args.length > 0 ? args[0] : DFL_FNAME);
		try {
			Map<String, String> parsingMap = ConfigParser.parseFile(filename);
			for (String key : parsingMap.keySet()) {
				System.out.printf("<key = '%s' : value = '%s'>%n", key, (String) parsingMap.get(key));
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}
}