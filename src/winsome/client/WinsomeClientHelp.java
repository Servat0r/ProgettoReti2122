package winsome.client;

import java.util.*;

import winsome.client.command.Command;
import winsome.client.command.CommandParser;
import winsome.util.*;

/**
 * Help messages and methods for help guide.
 * @author Salvatore Correnti
 * @see WinsomeClient
 */
final class WinsomeClientHelp {
	
	private WinsomeClientHelp() {}
	
	@SafeVarargs
	private static <T> List<T> list(T... args){ return Common.toList(args); }
	
	private static <K,V> Map<K, V> newMap(List<K> keys, List<V> vals){ return Common.newHashMapFromLists(keys, vals); }
	
	/** Offset for printing help guide lines. */
	private static String OFFSET = "  ";
	
	private static final List<String> COMMANDS = list(
		CommandParser.REG,
		CommandParser.LOGIN,
		CommandParser.LOGOUT,
		
		CommandParser.LIST,
		CommandParser.FOLLOW,
		CommandParser.UNFOLLOW,
		
		CommandParser.BLOG,
		CommandParser.POST,
		CommandParser.SHOW,
		
		CommandParser.DELETE,
		CommandParser.REWIN,
		CommandParser.RATE,
		
		CommandParser.COMMENT,
		CommandParser.WALLET,
		CommandParser.HELP,
		
		CommandParser.QUIT,
		CommandParser.EXIT,
		CommandParser.CLEAR,
		CommandParser.WAIT
	);
	
	private static final Map<String, String> quitMap = newMap(list(""), list("Closes this program if user is logged out"));
	
	/** Help messages map of the form { id -> {param -> message} }. */
	private static final Map<String, Map<String, String>> HELPS = newMap(
		COMMANDS,
		list(
			newMap(
				list("<username> <password> <taglist>"),
				list("Register to WinSome with the specified username and password and the list of tags <taglist>.\n"
						+ "A username is a unique identifier in alphanumeric characters (a-z, A-Z, 0-9 and _).\n"
						+ "You must provide at least one tag and at most five ones; the only characters allowed for a\n"
						+ "tag are lowercase letters (a-z), cyphers (0-9) and underscore (_).")
			),
			newMap(
				list("<username> <password>"),
				list("Login to WinSome with given username and password.\nOn success, server returns a confirmation\n"
						+ "message and command prompt changes from anonymous form (>>> ) to a named one (username>).\n"
						+ "On failure, an error message is printed. There could not be more than one user logged on\n"
						+ "the same console at the same time.")
			),
			newMap(
				list(Command.EMPTY),
				list("Logout from Winsome for the current user. On success, server returns a confirmation message and\n"
						+ "command prompt changes from named form (username>) to anonymous form (>>> ). On failure,\n"
						+ "an error message is printed.\nNOTE: Before than exiting the program, there MUST be no user "
						+ "logged in, so it is necessary\nto type 'logout' and then 'quit'/'exit' if any user is still "
						+ "logged in for exiting the program.")
			),
			newMap(
				list("users", "followers", "following"),
				list(
					"Prints a list of all users that shares at least one tag with the current one. The list is of the "
					+ "form e.g.:\nUser | Tags\n-----------------\nuser | tag1, tag2\n for a user with username 'user' "
					+ "and two tags 'tag1', 'tag2'.",
					
					"Prints a list of all users that are following the current one. The list is of the form e.g.:\n"
					+ "User  | Tags\n------------------\nuser1 | tag1, tag2\nuser2 | tag1, tag3\nfor a user with"
					+ " followers: 'user1' with tags 'tag1' and 'tag2' and 'user2' with tags 'tag1' and 'tag3'.",
					
					"Prints a list of all users that the current one is following. The list is of the form e.g.:\n"
					+ "User  | Tags\n------------------\nuser1 | tag1, tag2\nuser2 | tag1, tag3\nfor a user that"
					+ " is following: 'user1' with tags 'tag1' and 'tag2' and 'user2' with tags 'tag1' and 'tag3'."
				)
			),
			newMap(
				list("<username>"),
				list("Sets the current user to follow the specified one if existing and not already followed.\n"
					+ "On success, server returns an \"OK\" message and the user with its tag will be visible\n"
					+ "with the command \"list following\". On error, it returns an error message.")
			),
			newMap(
				list("<username>"),
				list("Sets the current user to not follow anymore the specified one if existing and still followed.\n"
					+ "On success, server returns an \"OK\" message and the user with its tag will not be visible\n"
					+ "anymore with the command \"list following\". On error, it returns an error message.")
			),
			newMap(
				list(Command.EMPTY),
				list("Shows all posts in the blog of the current user, i.e. each one created or rewinned by the current\n"
					+ "user. The list is of the form e.g.:\nId | Author | Title\n--------------------\n1  | user1  |"
					+ " title1\n2  | user2  | title2\nfor a user named 'user1' who has created post with (id = 1) and that"
					+ " has rewon a post created by 'user2' with (id = 2).")
			),
			newMap(
				list("<title> <content>"),
				list("Creates a post with the specified title and content, which must be provided each one within a couple\n"
					+ "of (\"), e.g. 'post \"Example of title\" \"Example of content\". To insert a (\"), type '\\\"' with\n"
					+ "'\\' as an escape (will not be counted against character limit). A title must have at most 20 characters\n"
					+ "and a content must have at most 500 ones.")
			),
			newMap(
				list("feed", "post <idPost>"),
				list(
					"Shows the post(s) in all the blogs of all the users that the current one is following. The list is of\n"
					+ "the form e.g.:\nId | Author | Title\n--------------------\n1  | user1  | title1\n2  | user2  | "
					+ "title2\nfor a user 'user0' that is following: 'user1' and 'user2', with posts with id=1,2 in their "
					+ "blogs.",
					
					"Shows the post specified by <idPost> if there is a post with that id, otherwise it returns an error\n"
					+ "message. The output is of the form e.g.:\nTitle: Title of the post\nContent: Content of the post\n"
					+ "Votes: 2 likes, 1 dislikes\nComments:\n  user1: Content of comment 1\n  user2: Content of comment 2\n")
			),
			newMap(
				list("<idPost>"),
				list("Deletes the post with <idPost>, if and only if the current user is also the author of the post: all\n"
					+ "rewarding actions on that post (rates and comments) not registered in the last reward calculations\n"
					+ "will not be counted for rewarding.")
			),
			newMap(
				list("<idPost>"),
				list("Rewins the post with <idPost>, if and only if the post is contained in the feed and not already rewon.\n"
					+ "On success, the post with <idPost> will appear on the blog.")
			),
			newMap(
				list("<idPost> <vote>"),
				list("Rates the post with <idPost> with <vote>, which must be one of '+1' (like) or '-1' (dislike).\n"
					+ "To rate a post, current user must NOT be its author, and a post can be rated only once by any user.")
			),
			newMap(
				list("<idPost> <comment>"),
				list("Comments the post with <idPost> with <comment>, which must be within quotes (\") (to insert a quote\n"
					+ "into the comment type '\\\"' and the '\\' will count as an escape). To rate a post, current user\n"
					+ "must NOT be its author.")
			),
			newMap(
				list(Command.EMPTY, "btc"),
				list(
					"Returns the value of the wallet as calculated in the last period with a history of all transactions,\n"
					+ "sorted from the most recent to the least recent in order of time. The output is of the form:\n"
					+ "Value (wincoin): 4.00\nTransactions:\n#2 : Fri Jan 07 17:02:29 CET 2022 : +2.00\n#1 : Fri Jan 07 "
					+ "17:00:29 CET 2022 : +2.00\nfor a value of 4.00 wincoins gained with 2 rewards.",
					
					"Returns the value of the wallet converted in bitcoin together with the value in bitcoin and the history\n"
					+ "of all transactions. The output is of the form:\nValue (bitcoin): +3.20\nValue (wincoin): +4.00\n"
					+ "Transactions:\n#2 : Fri Jan 07 17:02:29 CET 2022 : +2.00\n#1 : Fri Jan 07 17:00:29 CET 2022 : +2.00\n"
					+ "for a value of 4.00 wincoins gained with 2 rewards and an exchange rate (1 bitcoin = 0.8 wincoins)."
				)
			),
			newMap(
				list("[<command>]"),
				list("If argument <command> is not specified, shows this help guide for all commands, otherwise shows\n"
					+ "help message for the command specified.")
			),
			quitMap, quitMap,
			newMap(
				list(Command.EMPTY),
				list("Attempts to clear console screen. Particularly, attempts to execute the command 'clear' as a subprocess,\n"
					+ "on failure tries to execute 'cls', and on failure also on this it terminates without doing anything more.")
			),
			newMap(
				list("<secs>"),
				list("Waits for <secs> seconds, unless interrupted by another thread.")
			)
		)
	);
	
	/** Sets the offset to the given value. */
	public synchronized static void setOffset(String str) { Common.notNull(str); OFFSET = new String(str); }
	
	/** Returns current offset. */
	public synchronized static String getOffset() { return OFFSET; }
	
	/** Returns a list of all available commands (id only). */	
	public static List<String> commandsList() {
		List<String> result = new ArrayList<>();
		for (int i = 0; i < COMMANDS.size(); i++) result.add( new String(COMMANDS.get(i)) );
		return result;
	}
	
	/**
	 * @param cmd Command id.
	 * @return A formatted string for the given command.
	 */
	public static String getCmdHelp(String cmd) {
		Common.notNull(cmd);
		Map<String, String> vals = HELPS.get(cmd);
		String syntax, message;
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = vals.keySet().iterator();
		while (it.hasNext()) {
			syntax = it.next();
			message = vals.get(syntax);
			sb.append( cmd + " " + syntax + ":\n" + message + (it.hasNext() ? "\n" : "") );
		}
		return sb.toString().replace("\n", "\n" + getOffset());
	}
}