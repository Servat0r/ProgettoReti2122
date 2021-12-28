package winsome.client.command;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					sb.append( (first ? ", " : "") + f.getName() + " = " + f.get(this) );
					if (!first) first = true;
				}
			}
		} catch (IllegalAccessException ex) { return null; }
		sb.append("]");
		return sb.toString();
	}
}