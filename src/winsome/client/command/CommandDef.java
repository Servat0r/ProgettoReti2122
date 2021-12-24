package winsome.client.command;

import java.util.*;
import java.util.regex.*;

public final class CommandDef {
	
	private static final String[] SYNTAX_ERROR = {"Syntax error for ", ": '", "'"};
	
	public static final String ID_PARAM_REGEX = "[a-z]+";
	public static final String SPACE = "[\s\t\f\r\n]";
	public static final String SPACE_PLUS = SPACE + "+";
	public static final String SPACE_STAR = SPACE + "*";
	
	private final String id;
	private final HashMap<String, CommandArgs> args; /* param -> regex (eventualmente param = "") */
	
	public CommandDef(String id, HashMap<String, CommandArgs> args) {
		if (!Pattern.matches(ID_PARAM_REGEX, id))
			throw new IllegalArgumentException(SYNTAX_ERROR[0] + "id" + SYNTAX_ERROR[1] + id + SYNTAX_ERROR[2]);
		for (String key : args.keySet()) {
			if ((key != null) && !Pattern.matches(ID_PARAM_REGEX, key))
				throw new IllegalArgumentException(SYNTAX_ERROR[0] + "parameter " + SYNTAX_ERROR[1] + key + SYNTAX_ERROR[2]);
		}
		this.id = id;
		this.args = args;
	}
	
	public static final String matchId(String cmdline) {
		String cmdstrip = cmdline.strip();
		Matcher m = Pattern.compile(ID_PARAM_REGEX).matcher(cmdstrip);
		return (m.find() ? cmdstrip.substring(0, m.end()) : null);
	}
	
	public final Command matchDef(String cmdline) {
		Command result = null;
		String regex = null;
		String start = null;
		String param = null;
		String args = null;
		String cmdstrip = cmdline.strip();
		for (String key : this.args.keySet()) {
			CommandArgs c = this.args.get(key);
			String val = (c != null ? c.getRegex() : null);
			
			start = (key != null ? new String(this.id + SPACE_PLUS + key) : new String(this.id));
			regex = ( val != null ? new String(start + SPACE_PLUS + val + SPACE_STAR) : new String(start + SPACE_STAR) );
			
			if (Pattern.matches(regex, cmdstrip)) {
				Matcher m = Pattern.compile(start).matcher(cmdstrip);
				m.find();
				args = cmdstrip.substring(m.end()).strip();
				param = (key != null ? new String(key) : null);
				if (args.length() > 0) result = new Command(new String(id), param, c.extractArgs(args));
				else result = new Command(new String(id), param, new ArrayList<>());
				break;
			}
		}
		return result;
	}

	public String getId() { return this.id; }
	
	public int hashCode() { return Objects.hash(id); }

	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		CommandDef other = (CommandDef) obj;
		return Objects.equals(id, other.id);
	}
}