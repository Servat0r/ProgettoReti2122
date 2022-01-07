package winsome.client.command;

import java.io.*;
import java.util.*;

import winsome.server.ServerUtils;
import winsome.util.Common;

public final class CommandParser implements AutoCloseable {
	
	public static final String DFLPROMPT = ">>> ";
	
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
		WHOAMI = "whoami";
	
	public static final String
		USERS = "users",
		FOLLOWERS = "followers",
		FOLLOWING = "following",
		FEED = "feed",
		BTC = "btc",
		NOTIFY = "notify",
		CMD = "cmd";
	
	public static final String
		ALPHANUM = "[a-zA-Z0-9_]+",
		LOWERNUM = "[a-z0-9_]+",
		NUM = "[0-9]+",
		QUOTED = "[\"][^\"]+[\"]", /* Empty titles and contents NOT allowed! */
		RATESTR = "(\\+|\\-)1";
	
	private final Set<CommandDef> cdefs;
	private final Scanner scanner;
	private boolean closed, cmdlinescan;
	private String prompt;
	
	public CommandParser(InputStream input) {
		
		this.prompt = DFLPROMPT;
		this.cmdlinescan = (input.equals(System.in) ? true : false);
		this.scanner = new Scanner(input);
		this.closed = false;
		
		HashMap<String, CommandArgs> registerMap = new HashMap<>();
		registerMap.put( null, new CommandArgs( new String[] {ALPHANUM, ALPHANUM, LOWERNUM, LOWERNUM, LOWERNUM, LOWERNUM, LOWERNUM}, 3, 7) );
		
		HashMap<String, CommandArgs> loginMap = new HashMap<>();
		CommandArgs s = new CommandArgs(new String[]{ALPHANUM, ALPHANUM});
		loginMap.put( null, s);
		
		HashMap<String, CommandArgs> idOnlyMap = new HashMap<>();
		idOnlyMap.put(null, null);
		
		HashMap<String, CommandArgs> listMap = new HashMap<>();
		listMap.put(USERS, null);
		listMap.put(FOLLOWERS, null);
		listMap.put(FOLLOWING, null);
		
		HashMap<String, CommandArgs> userMap = new HashMap<>();
		userMap.put( null, new CommandArgs(new String[] {ALPHANUM}) );
		
		HashMap<String, CommandArgs> postMap = new HashMap<>();
		postMap.put( null, new CommandArgs(new String[] {QUOTED, QUOTED}, ServerUtils.postTest) );
		
		HashMap<String, CommandArgs> showMap = new HashMap<>();
		showMap.put(FEED, null);
		showMap.put(POST, new CommandArgs(new String[]{NUM}, ServerUtils.numTest) );
		
		HashMap<String, CommandArgs> numMap = new HashMap<>();
		numMap.put(null, new CommandArgs(new String[] {NUM}, ServerUtils.numTest) );
		
		HashMap<String, CommandArgs> rateMap = new HashMap<>();
		rateMap.put( null, new CommandArgs(new String[] {NUM, RATESTR}, ServerUtils.rateTest) );
		
		HashMap<String, CommandArgs> commentMap = new HashMap<>();
		commentMap.put( null, new CommandArgs(new String[] {NUM, QUOTED}, ServerUtils.commentTest) );
		
		HashMap<String, CommandArgs> walletMap = new HashMap<>();
		walletMap.put(null, null);
		walletMap.put(BTC, null);
		walletMap.put(NOTIFY, null);
		
		HashMap<String, CommandArgs> helpMap = new HashMap<>();
		helpMap.put(null, null);
		helpMap.put(CMD, new CommandArgs(new String[] {CommandDef.ID_PARAM_REGEX}) );
				
		this.cdefs = new HashSet<>();
		
		this.cdefs.add(new CommandDef(REG, registerMap));
		this.cdefs.add(new CommandDef(LOGIN, loginMap));
		this.cdefs.add(new CommandDef(LOGOUT, idOnlyMap));
		
		this.cdefs.add(new CommandDef(LIST, listMap));
		this.cdefs.add(new CommandDef(FOLLOW, userMap));
		this.cdefs.add(new CommandDef(UNFOLLOW, userMap));
		
		this.cdefs.add(new CommandDef(BLOG, idOnlyMap));
		this.cdefs.add(new CommandDef(POST, postMap));
		this.cdefs.add(new CommandDef(SHOW, showMap));
		
		this.cdefs.add(new CommandDef(DELETE, numMap));
		this.cdefs.add(new CommandDef(REWIN, numMap));
		this.cdefs.add(new CommandDef(RATE, rateMap));
		
		this.cdefs.add(new CommandDef(COMMENT, commentMap));
		this.cdefs.add(new CommandDef(WALLET, walletMap));
		this.cdefs.add(new CommandDef(HELP, helpMap));
		
		this.cdefs.add(new CommandDef(QUIT, idOnlyMap));
		this.cdefs.add(new CommandDef(EXIT, idOnlyMap));
		
		this.cdefs.add(new CommandDef(WHOAMI, idOnlyMap));
	}
	
	public CommandParser() { this(System.in); }
	
	public Command nextCmd() {
		Command cmd = null;
		if (cmdlinescan) System.out.print(this.prompt);
		if (!this.scanner.hasNextLine()) return null;
		String nextLine = this.scanner.nextLine();
		if (CommandDef.matchWSpaceComment(nextLine)) return Command.SKIP;
		String idAttempt = CommandDef.matchId(nextLine);
		if (idAttempt != null) {
			for (CommandDef def : this.cdefs) {
				if (def.getId().equals(idAttempt)) {
					cmd = def.matchDef(nextLine);
					break;
				}
			}
		}
		return cmd;
	}
	
	public void setPrompt(String fmt, Object...objects) {
		Common.notNull(fmt);
		String msg = String.format(fmt, objects);
		this.prompt = msg;
	}
	
	public void resetPrompt() { this.prompt = DFLPROMPT; }
		
	public synchronized void close() throws Exception {
		if (!closed) { this.scanner.close(); closed = true; }
	}
	
	public synchronized boolean isClosed() { return closed; }
}