package winsome.client.command;

import java.util.*;
import java.util.function.*;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * This class models a command-arguments parser and its objects are intended for use after parsing command
 *  id and (if present) command param.
 *  Each CommandArgs defines:
 *  1) a minimum and maximum number of arguments (both >= 0);
 *  2) a list of "matching" functions {String -> Integer} such that for each one function f, f(s) := the length
 *  of the longest matched substring of s starting from position 0 on success, or a negative value on failure.
 *  A common factory of these functions is {@link Common#regexMatcher(String)}, but it is also possible to define
 *  custom functions;
 *  3) A checker function {List(String) -> Boolean} that checks if arguments are correct and if necessary performs
 *  any necessary manipulation of the strings themselves. 
 * @author Salvatore Correnti
 * @see Command
 * @see CommandDef
 * @see CommandParser
 */
public final class CommandArgs {
	
	/** Default checker for the arguments list. */
	private static final Predicate<List<String>> ALWAYS = (list) -> true;
	
	private static final String
		ARG_NMATCH = "Argument #%d does not match description!",
		ARGS_NTEST = "Arguments do NOT satisfy requirements";
	
	public static final CommandArgs NULL = new CommandArgs();
	
	@NotNull
	private final List<ToIntFunction<String>> matchers;
	private final int minArg, maxArg;
	@NotNull
	private final Predicate<List<String>> checker;
	
	@SafeVarargs
	public CommandArgs(Predicate<List<String>> checker, int minArg, int maxArg,
		ToIntFunction<String> ...matchers) {
		
		Common.allAndArgs(matchers != null, minArg <= maxArg, maxArg <= matchers.length);
		this.checker = (checker != null ? checker : ALWAYS);
		this.minArg = minArg;
		this.maxArg = maxArg;
		this.matchers = Arrays.asList(matchers);
		Common.collectionNotNull(this.matchers);
	}
	
	
	@SafeVarargs
	public CommandArgs(Predicate<List<String>> checker, ToIntFunction<String> ...matchers) {
		this(checker, matchers.length, matchers.length, matchers);
	}
	
	@SafeVarargs
	public CommandArgs(int minArg, int maxArg, ToIntFunction<String> ...matchers) {
		this(null, minArg, maxArg, matchers);
	}
	
	private CommandArgs() { this(null, 0, 0); }
	
	@SafeVarargs
	public CommandArgs(ToIntFunction<String> ...matchers) {
		this(null, matchers.length, matchers.length, matchers);
	}
	
	
	public final int minArg() { return this.minArg; }
	public final int maxArg() { return this.maxArg; }
	public final Predicate<List<String>> checker() { return this.checker; }
	public final List<ToIntFunction<String>> matchers() { return this.matchers; }
	
	/**
	 * Decodes a string into a list of argument strings using provided matchers and attempting to match
	 *  the entire string.
	 * @param cmdline String to decode.
	 * @return A list of strings contained all decoded arguments.
	 * @throws IllegalArgumentException On failure (string does not match any of the provided matchers, too few
	 *  or too many arguments, checker returns false).
	 */
	@NotNull
	public List<String> extractArgs(String cmdline) {
		List<String> result = new ArrayList<>();
		int j;
		String cmdstrip = Common.stripLeading(new String(cmdline));
		String currCmd;
		while (cmdstrip.length() > 0) {
			j = matchers.get(result.size()).applyAsInt(cmdstrip);
			if (j < 0) throw new IllegalArgumentException( String.format(ARG_NMATCH, result.size()) );
			else {
				currCmd = cmdstrip.substring(0, j);
				cmdstrip = Common.stripLeading(cmdstrip.substring(j));
				result.add(currCmd);
			}
		}
		int size = result.size();
		if (size < minArg) throw new IllegalArgumentException("Too few arguments");
		else if (size > maxArg) throw new IllegalArgumentException("Too many arguments");
		else if (!this.checker.test(result)) throw new IllegalArgumentException(ARGS_NTEST);
		else return result;
	}
	
}