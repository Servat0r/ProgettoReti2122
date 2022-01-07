package winsome.common.config;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import winsome.util.Common;

/**
 * This class defines methods for parsing a configuration text file into a Map object. 
 * Syntax is defined like this:
 * 	a comment line starts with a '#';
 * 	an assignment is of the form: identifier = value, where "identifier" is an alphanumeric sequence starting
 * 	with a letter and allowing '_' and '.' from the second character.
 * @author Salvatore Correnti
 *
 */
public final class ConfigParser {

	/* REGEX for an assignment line. */
	private static final String
		WSPACE = "[\s\t\r\f]*",
		KEY = "[A-Za-z][a-zA-Z0-9_\\.]*",
		VALUE = "[^\s\t\r\f\n=]+",
		COMM = "#",
		EQ = "=",
		ASSIGN_LINE = KEY + WSPACE + EQ + WSPACE + VALUE;
	
	private ConfigParser() {}
	
	/* Flags for modifying parsed keys. */
	public static final int
		UPPER = 1,
		LOWER = 2;
	
	/**
	 * Parses the content of a text file into a Map<String, String> object, where the values parsed for the
	 * keys may be modified according to the flags passed.
	 * @param filename (Relative) path of file.
	 * @param flags Flags given to modify keys.
	 * @return A Map<String, String> object containing couples <key : value> for each "key = value" assignment
	 * line on success, null on error.
	 * @throws FileNotFoundException Thrown by FileInputStream constructor.
	 * @throws IOException Thrown by FileInputStream closing.
	 * @throws ConfigParsingException If a syntax error occurs while parsing.
	 */
	public static final Map<String, String> parseFile(String filename, int flags) throws FileNotFoundException, IOException, ConfigParsingException {
		Common.notNull(filename);
		Map<String, String> result;
		String nextLine;
		Matcher m;
		int currentLine = 0;
		try (
			FileInputStream f = new FileInputStream(filename);
			Scanner s = new Scanner(f);
		){
			result = new HashMap<>();
			while (s.hasNextLine()) {
				nextLine = s.nextLine().strip();
				currentLine++;
				if (nextLine.equals("") || nextLine.startsWith(COMM)) continue;
				else if (Pattern.matches(ASSIGN_LINE, nextLine)) {
					m = Pattern.compile(KEY).matcher(nextLine);
					m.find();
					String key = nextLine.substring(0, m.end());
					if (flags == UPPER) key = key.toUpperCase();
					else if (flags == LOWER) key = key.toLowerCase();
					String value = nextLine.substring(m.end()).strip().substring(EQ.length()).strip();
					if (result.containsKey(key)) throw new ConfigParsingException("Line " + currentLine + ": Key already defined: '" + key + "'");
					else result.put( new String(key), new String(value) );
				} else throw new ConfigParsingException("Line " + currentLine + ": Syntax error: '" + nextLine + "'");
			}
			return result;
		}
	}
	
	public static final Map<String, String> parseFile(String filename) throws FileNotFoundException, IOException, ConfigParsingException {
		return parseFile(filename, 0);
	}
	
	public String toString() { return Common.jsonString(this); }
}