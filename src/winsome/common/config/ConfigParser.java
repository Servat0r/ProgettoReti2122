package winsome.common.config;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public final class ConfigParser {

	public static final String ASSIGNMENT = "^[\s\t]*[A-Za-z]+[a-zA-Z0-9_\\.]*[\s\t]*=[\s\t]*[^\s\t=]+$";
	
	private ConfigParser() {}
	
	public static final int UPPER = 2;
	public static final int LOWER = 1;
	
	public static final Map<String, String> parseFile(String filename, int flags) throws FileNotFoundException, IOException, ConfigParsingException {
		if (filename == null) throw new NullPointerException();
		Map<String, String> result;
		String nextLine;
		int currentLine = 0;
		try (
			FileInputStream f = new FileInputStream(filename);
			Scanner s = new Scanner(f);
		){
			result = new HashMap<>();
			while (s.hasNextLine()) {
				nextLine = s.nextLine().strip();
				if ((flags & LOWER) != 0) { nextLine = nextLine.toLowerCase(); }
				if ((flags & UPPER) != 0) { nextLine = nextLine.toUpperCase(); }
				currentLine++;				
				if (nextLine.equals("") || nextLine.startsWith("#")) continue;
				else if (Pattern.matches(ASSIGNMENT, nextLine)) {
					String[] res = nextLine.split("=");
					res[0] = res[0].trim();
					res[1] = res[1].trim();
					if (result.containsKey(res[0])) throw new ConfigParsingException("Line " + currentLine + ": Key already defined: '" + res[0] + "'");
					else result.put(new String(res[0]), new String(res[1]));
				} else throw new ConfigParsingException("Line " + currentLine + ": Syntax error: '" + nextLine + "'");
			}
			return result;
		}
	}
	
	public static final Map<String, String> parseFile(String filename) throws FileNotFoundException, IOException, ConfigParsingException {
		return parseFile(filename, 0);
	}
	
}