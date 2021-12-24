package winsome.client.command;

import java.util.*;
import java.util.regex.*;

public final class CommandArgs {

	private static final String OPEN_PAR = "(";
	private static final String CLOSE_PAR = ")?";
	private static final String SPACE_PLUS = CommandDef.SPACE_PLUS;
	
	private final String[] regexes;
	private final int minArg;
	private final int maxArg;
	private final String regex;
	
	public CommandArgs(String[] regexes, int minArg, int maxArg) {
		if (minArg > maxArg || maxArg > regexes.length) throw new IllegalArgumentException();
		this.minArg = minArg;
		this.maxArg = maxArg;
		this.regexes = regexes;
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < minArg) {
			if (i > 0) sb.append(SPACE_PLUS);
			sb.append(regexes[i++]);
		}
		while (i < maxArg) {
			sb.append(OPEN_PAR + SPACE_PLUS + regexes[i++]);
		}
		while (i > minArg) { sb.append(CLOSE_PAR); i--; }
		this.regex = new String(sb.toString());
	}
	
	public CommandArgs(String[] regexes) {
		this(regexes, regexes.length, regexes.length);
	}
	
	public String getRegex() { return this.regex; }
	public int getMinArg() { return this.minArg; }
	public int getMaxArg() { return this.maxArg; }
	
	public List<String> extractArgs(String cmdline) {
		List<String> result = new ArrayList<>();
		Matcher m;
		int index = 0;
		String cmdstrip = new String(cmdline);
		while (cmdstrip.length() > 0) {
			cmdstrip = cmdstrip.strip();
			m = Pattern.compile(this.regexes[index++]).matcher(cmdstrip);
			if (!m.find()) throw new IllegalArgumentException();
			int len = m.end();
			result.add( new String(cmdstrip.substring(0, len)).strip() );
			cmdstrip = new String(cmdstrip.substring(len));
		}
		return result;
	}
}