package winsome.client.command;

import java.util.*;

import winsome.util.Common;

public final class Command {

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
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Command [id = '" + id + (param != null ? "'; param = '" + param : "") + "'; args = {");
		for (int i = 0; i < args.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append("'" + args.get(i) + "'");
		}
		sb.append("}]");
		return sb.toString();
	}
}