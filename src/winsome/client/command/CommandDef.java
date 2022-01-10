package winsome.client.command;

import java.util.*;
import java.util.function.*;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * This class provides a parser definition for a single command (i.e. a single starting id) with all
 *  possible parameters.
 * @author Salvatore Correnti
 * @see Command
 * @see CommandArgs
 * @see CommandParser
 */
public final class CommandDef {
	
	private static final String SYNTAX_ERROR = "Syntax error for %s: '%s'";
	
	/** Standard matcher for id and params */
	@NotNull
	public static final ToIntFunction<String>
		IdParamRegex = Common.regexMatcher("[a-z]+");
		
	
	private final String id;
	private final Map<String, CommandArgs> args; /* param -> regex */
	
	public CommandDef(String id, Map<String, CommandArgs> args) {
		Common.notNull(id, args);
		if ( !matchesIdParam(id) ) throw new IllegalArgumentException( Common.excStr(SYNTAX_ERROR, "id", id) );
		for (String key : args.keySet()) {
			if (key == null) throw new NullPointerException("Null exception!");
			if (!matchesIdParam(key))
				throw new IllegalArgumentException( Common.excStr(SYNTAX_ERROR, "parameter", key) );
		}
		this.id = id;
		this.args = args;
	}
	
	/**
	 * Matches a line made up only by whitespace or that starts with a comment sign '#'.
	 * @param cmdline Input line.
	 * @return true if line is as described above, false otherwise.
	 */
	public static final boolean matchWSpaceComment(String cmdline) {
		String cmdstrip = Common.strip(cmdline);
		if (cmdstrip.length() == 0 || cmdstrip.startsWith("#")) return true;
		else return false;
	}
	
	/**
	 * Attempts to match an id definition at the beginning of a command line to speed up
	 *  command definition search (see {@link CommandParser#nextCmd()}).
	 * @param cmdline Command line.
	 * @return The string matched at the beginning of the line as a command definition id
	 *  candidate on success, an empty string ({@link Command#EMPTY}) on error.
	 */
	@NotNull
	public static final String matchIdPar(String cmdline) {
		Common.notNull(cmdline);
		cmdline = Common.strip(cmdline);
		int result = IdParamRegex.applyAsInt(cmdline);
		if (result < 0) return Command.EMPTY;
		else if (result == cmdline.length()) return cmdline;
		else {
			char ch = cmdline.charAt(result);
			if (Character.isWhitespace(ch)) return cmdline.substring(0, result);
			else return Command.EMPTY;
		}
	}
	
	public static final boolean matchesIdParam(String cmdline) { return matchIdPar(cmdline).equals(cmdline); }
	
	/**
	 * Attempts to match an entire line with this Command definition and if yes, returns a Command object
	 *  as result of the matching.
	 * @param cmdline Input line.
	 * @return A Command object as result of the matching on success, {@link Command#NULL} on failure.
	 */
	@NotNull
	public final Command matchDef(String cmdline) {
		String cmdcopy = Common.stripLeading(cmdline);
		int n = Common.regexMatcher(id).applyAsInt(cmdcopy);
		if (n == id.length()) cmdcopy = Common.stripLeading(cmdcopy.substring(n));
		else return Command.NULL;
		for (String key : args.keySet()) {
			CommandArgs cargs = args.get(key);
			String str = cmdcopy, param = Command.EMPTY;
			if (!key.equals(Command.EMPTY)) {
				n = Common.regexMatcher(key).applyAsInt(str);
				if (n == key.length()) {
					str = Common.stripLeading(str.substring(n));
					param = new String(key);
				} else continue;
			}
			List<String> args;
			try { args = cargs.extractArgs(str); }
			catch (Exception ex) { continue; }
			
			return new Command(new String(id), param, args);
		}
		return Command.NULL;
	}
	
	public String getId() { return this.id; }
	
	public Map<String, CommandArgs> getArgs() { return this.args; }
	
	public int hashCode() { return Objects.hash(id); }
	
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		CommandDef other = (CommandDef) obj;
		return Objects.equals(id, other.id);
	}
	
	public String toString() { return Common.jsonString(this); }
}