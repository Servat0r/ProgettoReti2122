package winsome.client.command;

import java.util.*;
import java.util.function.*;
import java.util.regex.*;

import winsome.util.Serialization;

public final class CommandArgs {

	private static final Predicate<List<String>> ALWAYS = (list) -> true;
	
	private static final String 
		OPEN_PAR = "(",
		CLOSE_PAR = ")?",
		SPACE_PLUS = CommandDef.SPACE_PLUS;
	
	private static final String
		ARG_NMATCH = "Argument #%d does not match description!",
		ARGS_NTEST = "Arguments do NOT satisfy requirements";
	
	private final String[] regexes;
	private final int minArg;
	private final int maxArg;
	private final String regex;
	private final Predicate<List<String>> checker;
	
	public CommandArgs(String[] regexes, int minArg, int maxArg, Predicate<List<String>> checker) {
		if (minArg > maxArg || maxArg > regexes.length) throw new IllegalArgumentException();
		this.checker = checker;
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
	
	public CommandArgs(String[] regexes, int minArg, int maxArg) { this(regexes, minArg, maxArg, ALWAYS); }
	
	public CommandArgs(String[] regexes, Predicate<List<String>> checks) { this(regexes, regexes.length, regexes.length, checks); }
	
	public CommandArgs(String[] regexes) { this(regexes, regexes.length, regexes.length); }
	
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
			m = Pattern.compile(this.regexes[index]).matcher(cmdstrip);
			if (!m.find()) throw new IllegalArgumentException(String.format(ARG_NMATCH, index+1));
			int len = m.end();
			String ccmd = new String(cmdstrip.substring(0, len)).strip();
			result.add(ccmd);
			cmdstrip = cmdstrip.substring(len);
			index++;
		}
		if (!this.checker.test(result)) throw new IllegalArgumentException(ARGS_NTEST);
		return result;
	}
	
	public String toString() {
		String 
			cname = this.getClass().getSimpleName(), 
			jsond = Serialization.GSON.toJson(this, CommandArgs.class);
		return String.format("%s: %s", cname, jsond);
	}
}