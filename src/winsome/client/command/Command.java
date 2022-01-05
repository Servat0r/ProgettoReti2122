package winsome.client.command;

import java.util.*;

import winsome.util.Common;
import winsome.util.Serialization;

public final class Command {
	
	public static final String EMPTY = "";
	public static final Command NULL = new Command(EMPTY, EMPTY, null);
	
	private final String id;
	private final String param;
	private final List<String> args;
	
	public Command(String id, String param, List<String> args) {
		Common.notNull(id);
		this.id = id;
		this.param = param;
		this.args = new ArrayList<>();
		if (args != null) { for (String arg : args) this.args.add(arg); }
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

	public String toString() {
		String jsond = Serialization.GSON.toJson(this);
		return String.format("%s : %s", this.getClass().getSimpleName(), jsond);
	}
}