package winsome.client.command;

import java.util.*;

import winsome.util.*;

/**
 * This class models a parsed commands, containing the actual id, param and arguments already checked
 *  and (string-)manipulated (if necessary).
 * @author Salvatore Correnti
 * @see CommandParser
 * @see CommandDef
 * @see CommandArgs
 */
public final class Command {
	
	public static final String EMPTY = "";
	public static final Command
		SKIP = new Command(EMPTY, "skip", CollectionsUtils.toList()), /* A command representing a blank line */
		NULL = new Command(EMPTY, "null", CollectionsUtils.toList()); /* A command representing no match found */
	
	private final String id;
	private final String param;
	private final List<String> args;
	
	public Command(String id, String param, List<String> args) {
		Common.notNull(id);
		this.id = id;
		this.param = (param != null ? param : Command.EMPTY);
		this.args = new ArrayList<>();
		if (args != null) { Common.collectionNotNull(args); this.args.addAll(args); }
	}
	
	public final String getId() { return id; }
	public final String getParam() { return param; }
	public final List<String> getArgs() { return args; }
		
	public int hashCode() { return Objects.hash(args, id, param); }
	
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Command other = (Command) obj;
		return Objects.equals(args, other.args) && Objects.equals(id, other.id) && Objects.equals(param, other.param);
	}
	
	public String toString() { return Common.jsonString(this); }
}