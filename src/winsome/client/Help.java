package winsome.client;

import java.util.*;

import winsome.util.*;

final class Help {

	private Help() {}

	@SafeVarargs
	private static <T> List<T> list(T... args){ return Arrays.asList(args); }
	
	private static <K,V> Map<K, V> newMap(List<K> keys, List<V> vals){ return Common.newHashMapFromLists(keys, vals); }
	
	private static final List<String> COMMANDS = list(
		"register",
		"login",
		"logout",
		
		"list",
		"follow",
		"unfollow",
		
		"blog",
		"post",
		"show",
		
		"delete",
		"rewin",
		"rate",
		
		"comment",
		"wallet",
		"help",
		
		"quit",
		"exit",
		"whoami"
	);
	
	private static final Map<String, String> quitMap = newMap(list(""), list("Closes this program if user is logged out"));
	
	private static final Map<String, Map<String, String>> HELPS = newMap(
		COMMANDS,
		list(
			newMap(
				list("<username> <password> <taglist>"),
				list("Register to WinSome with the specified username and password and the list of tags <taglist>.\n"
						+ "A username is a unique identifier in alphanumeric characters (a-z, A-Z, 0-9 and _).\n "
						+ "You must provide at least one tag and at most five ones; the only characters allowed for a "
						+ "tag are lowercase letters (a-z), cyphers (0-9) and underscore (_).")
			),
			newMap(
				list("<username> <password>"),
				list("Login to WinSome with ...")
			),
			newMap(
				list("<username>"),
				list("Logout")
			),
			newMap(
				list("users", "followers", "following"),
				list("ListUsers", "ListFers", "ListFwing")
			),
			newMap(
				list("<username>"),
				list("Follow")
			),
			newMap(
				list("<username>"),
				list("Unfollow")
			),
			newMap(
				list(""),
				list("Blog")
			),
			newMap(
				list("<title> <content>"),
				list("Post")
			),
			newMap(
				list("feed", "post <idPost>"),
				list("Show feed", "Show post")
			),
			newMap(
				list("<idPost>"),
				list("Delete")
			),
			newMap(
				list("<idPost>"),
				list("Rewin")
			),
			newMap(
				list("<idPost> <vote>"),
				list("Rate")
			),
			newMap(
				list("<idPost> <comment>"),
				list("Comment")
			),
			newMap(
				list("", "btc"),
				list("Wallet", "Wallet in bitcoin")
			),
			newMap(
				list("", "cmd <command>"),
				list("shows this help guide for all commands", "shows help message for the command specified")
			),
			quitMap, quitMap,
			newMap(
				list(""),
				list("Prints the current logged user if any, otherwise prints 'No user logged'")
			)
		)
	);
	
	public static List<String> commandsList() {
		List<String> result = new ArrayList<>();
		for (int i = 0; i < COMMANDS.size(); i++) result.add( new String(COMMANDS.get(i)) );
		return result;
	}
	
	public static String getCmdHelp(String cmd) {
		Common.notNull(cmd);
		Map<String, String> vals = HELPS.get(cmd);
		String syntax, message;
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = vals.keySet().iterator();
		while (it.hasNext()) {
			syntax = it.next();
			message = vals.get(syntax);
			sb.append( cmd + " " + syntax + ": " + message + (it.hasNext() ? "\n" : "") );
		}
		return sb.toString();
	}
}