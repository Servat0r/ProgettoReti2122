package winsome.client.command;

import java.io.*;
import java.util.*;
import java.util.function.*;

import winsome.annotations.NotNull;
import winsome.server.ServerUtils;
import winsome.util.*;

/**
 * This class models a complete command-line parser with any number of command definitions.
 * @author Salvatore Correnti
 * @see CommandDef
 * @see CommandArgs
 * @see Command
 */
public final class CommandParser implements AutoCloseable, Iterable<CommandDef> {
	
	/* Default prompt when scanning from System.in . */
	public static final String DFLPROMPT = ">>> ";
	
	/* Default identifier strings. */
	public static final String 
		REG = "register",
		LOGIN = "login",
		LOGOUT = "logout",
		LIST = "list",
		FOLLOW = "follow",
		UNFOLLOW = "unfollow",
		BLOG = "blog",
		POST = "post",
		SHOW = "show",
		DELETE = "delete",
		REWIN = "rewin",
		RATE = "rate",
		COMMENT = "comment",
		WALLET = "wallet",
		HELP = "help",
		QUIT = "quit",
		EXIT = "exit",
		CLEAR = "clear",
		WAIT = "wait";
	
	/* Param strings */
	public static final String
		USERS = "users",
		FOLLOWERS = "followers",
		FOLLOWING = "following",
		FEED = "feed",
		BTC = "btc",
		NOTIFY = "notify";
	
	/** Common matchers. */
	@NotNull
	public static final ToIntFunction<String>
		ALPHANUM = Common.regexMatcher("[a-zA-Z0-9_]+"),
		PASSWORD = (str) -> {
			char ch;
			for (int i = 0; i < str.length(); i++) {
				ch = str.charAt(i);
				if (Character.isWhitespace(ch)) return i;
			}
			return str.length();
		},
		LOWERNUM = Common.regexMatcher("[a-z0-9_]+"),
		NUM = (str) -> (str.equals("0") ? 1 : Common.regexMatcher("[1-9][0-9]*").applyAsInt(str)),
		RATESTR = (str) -> (str.equals(ServerUtils.LIKE) || str.equals(ServerUtils.DISLIKE) ? 2 : -1),
		QUOTED = (str) -> {
			if (str.length() <= 2) return -1;
			else if (str.charAt(0) != '"') return -1;
			char ch;
			int index = 1;
			while (index < str.length()) {
				ch = str.charAt(index);
				if (ch == '\\' && str.charAt(index + 1) == '"') { index += 2; }
				else if (ch == '"') return index+1;
				else index++;
			}
			return -1;
		}; /* Empty titles and contents NOT allowed! */

		private static final boolean testLong(String str){
			Common.notNull(str);
			try { Long.parseLong(str); return true; }
			catch (Exception ex) { return false; }
		}
		
		private static final boolean testTitleComm(String str, int len) {
			Common.allAndArgs(str != null, len >= 0);
			return (str.length() <= len);
		}
		
		private static final boolean testRate(String str) {
			Common.notNull(str);
			return (str.equals("+1") || str.equals("-1"));
		}
		
		/* Default checkers. */
		public static final Predicate<List<String>>
			/* Checker for post <title> <comment> command */
			postTest = (list) -> {
				if (list.size() != 2) return false;
				else {
					String title = list.get(0), content = list.get(1);
					title = Common.dequote(title); content = Common.dequote(content);
					title = title.replace("\\\"", "\""); content = content.replace("\\\"", "\"");
					//All dequoted
					list.set(0, title); list.set(1, content);
					return testTitleComm(title, 20) && testTitleComm(content, 500);
				}
			},
			/* Commands with a single numeric argument */
			numTest = (list) -> { return (list.size() == 1 ? testLong(list.get(0)) : false); },
			/* rate <idPost> <vote> */
			rateTest = (list) -> {
				return (list.size() == 2) && testLong(list.get(0)) && testRate(list.get(1));
			},
			/* comment <idPost> <text> */
			commentTest = (list) -> {
				if (list.size() != 2) return false;
				String comment = Common.dequote(list.get(1));
				comment = comment.replace("\\\"", "\"");
				list.set( 1, comment);
				return testLong(list.get(0));
			};		
	
	@NotNull
	private final Set<CommandDef> cdefs;
	@NotNull
	private final transient BufferedReader reader;
	/* closed := (parser closed); cmdline := if true, parser is scanning from System.in
	 *  and prompt is printed before reading any command
	 */
	private boolean closed, cmdlinescan;
	@NotNull
	private String prompt;
		
	/**
	 * Builds a CommandParser from an InputStream and a set of command definitions.
	 * @param input InputStream.
	 * @param defs Definitions.
	 */
	public CommandParser(InputStream input, CommandDef ...defs) {
		Common.notNull("InputStream is NULL!", input);
		
		this.prompt = DFLPROMPT;
		this.cmdlinescan = (input.equals(System.in) ? true : false);
		this.reader = new BufferedReader(new InputStreamReader(input));
		this.closed = false;
		
		this.cdefs = new HashSet<>();
		for (CommandDef def : defs) this.cdefs.add(def);
		Common.collectionNotNull(this.cdefs);
		
	}
	
	/**
	 * @param input InputStream.
	 * @return Default parser for {@link winsome.client.WinsomeClient}.
	 */
	public static CommandParser defaultParser(InputStream input) {
		Common.notNull("InputStream is NULL!", input);
		
		Map<String, CommandArgs> registerMap = new HashMap<>();
		registerMap.put(Command.EMPTY,
			new CommandArgs(3, 7, ALPHANUM, PASSWORD, LOWERNUM, LOWERNUM, LOWERNUM, LOWERNUM, LOWERNUM) );
				
		Map<String, CommandArgs> loginMap = new HashMap<>();
		loginMap.put( Command.EMPTY, new CommandArgs(ALPHANUM, PASSWORD));
		
		Map<String, CommandArgs> idOnlyMap = new HashMap<>();
		idOnlyMap.put(Command.EMPTY, CommandArgs.NULL);
		
		Map<String, CommandArgs> listMap = new HashMap<>();
		listMap.put(USERS, CommandArgs.NULL);
		listMap.put(FOLLOWERS, CommandArgs.NULL);
		listMap.put(FOLLOWING, CommandArgs.NULL);
		
		Map<String, CommandArgs> userMap = new HashMap<>();
		userMap.put(Command.EMPTY, new CommandArgs(ALPHANUM));
		
		Map<String, CommandArgs> postMap = new HashMap<>();
		postMap.put(Command.EMPTY, new CommandArgs(postTest, QUOTED, QUOTED) );
		
		Map<String, CommandArgs> showMap = new HashMap<>();
		showMap.put(FEED, CommandArgs.NULL);
		showMap.put(POST, new CommandArgs(numTest, NUM));
		
		Map<String, CommandArgs> numMap = new HashMap<>();
		numMap.put(Command.EMPTY, new CommandArgs(numTest, NUM));
		
		Map<String, CommandArgs> rateMap = new HashMap<>();
		rateMap.put(Command.EMPTY, new CommandArgs(rateTest, NUM, RATESTR));
		
		Map<String, CommandArgs> commentMap = new HashMap<>();
		commentMap.put(Command.EMPTY, new CommandArgs(commentTest, NUM, QUOTED) );
		
		Map<String, CommandArgs> walletMap = new HashMap<>();
		walletMap.put(Command.EMPTY, CommandArgs.NULL);
		walletMap.put(BTC, CommandArgs.NULL);
		walletMap.put(NOTIFY, CommandArgs.NULL);
		
		Map<String, CommandArgs> helpMap = new HashMap<>();
		helpMap.put(Command.EMPTY, new CommandArgs(0, 1, CommandDef.IdParamRegex));
		
		return new CommandParser(
			input,
			new CommandDef(REG, registerMap),
			new CommandDef(LOGIN, loginMap),
			new CommandDef(LOGOUT, idOnlyMap),
			new CommandDef(LIST, listMap),
			new CommandDef(FOLLOW, userMap),
			new CommandDef(UNFOLLOW, userMap),
			new CommandDef(BLOG, idOnlyMap),
			new CommandDef(POST, postMap),
			new CommandDef(SHOW, showMap),
			new CommandDef(DELETE, numMap),
			new CommandDef(REWIN, numMap),
			new CommandDef(RATE, rateMap),
			new CommandDef(COMMENT, commentMap),
			new CommandDef(WALLET, walletMap),
			new CommandDef(HELP, helpMap),
			new CommandDef(QUIT, idOnlyMap),
			new CommandDef(EXIT, idOnlyMap),
			new CommandDef(CLEAR, idOnlyMap),
			new CommandDef(WAIT, numMap)
		);
	}
	
	public CommandParser(CommandDef ...defs) { this(System.in, defs); }
	
	/**
	 * Retrieves the next command from the next line in the InputStream by matching command definitions.
	 * @return A Command object representing:
	 *  1) parsed commands on success;
	 *  2) {@link Command#SKIP} if line is blank / comment;
	 *  3) {@link Command#NULL} if no match is found;
	 *  null on error (incomplete line / IO error).
	 */
	public Command nextCmd() {
		Command cmd = null;
		if (cmdlinescan) System.out.print(this.prompt);
		String nextLine;
		try { nextLine = reader.readLine(); if (nextLine == null) return null; }
		catch (IOException ioe) { return null; }
		if (CommandDef.matchWSpaceComment(nextLine)) return Command.SKIP;
		String idAttempt = CommandDef.matchIdPar(nextLine);
		if (!idAttempt.equals(Command.EMPTY)) {
			for (CommandDef def : this.cdefs) {
				if (def.getId().equals(idAttempt))
					{ cmd = def.matchDef(nextLine); break; }
			}
		}
		return cmd;
	}
	
	/**
	 * Sets the prompt to a formatted string.
	 * @param fmt Format string.
	 * @param objects Objects to format.
	 */
	public void setPrompt(String fmt, Object...objects) {
		Common.notNull(fmt);
		String msg = String.format(fmt, objects);
		this.prompt = msg;
	}
	
	/** Resets prompt to default value. */
	public void resetPrompt() { this.prompt = DFLPROMPT; }
	
	/** Closes parser and releases resources. Successive invocations will have no effect. */
	public synchronized void close() throws Exception {
		if (!closed) { closed = true; this.reader.close(); }
	}
	
	public String toString() { return Common.jsonString(this); }
	
	/** Returns an iterator over the command definitions provided for this parser. */
	public Iterator<CommandDef> iterator() { return this.cdefs.iterator(); }
}