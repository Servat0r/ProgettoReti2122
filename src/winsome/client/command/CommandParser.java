package winsome.client.command;

import java.io.*;
import java.util.*;
import java.util.function.*;

import winsome.util.Common;


public final class CommandParser implements AutoCloseable {
	
	private static final Predicate<String> testLong = new Predicate<>() {
		public boolean test(String str) {
			Common.notNull(str);
			try { Long.parseLong(str); return true; }
			catch (Exception ex) { return false; }
		}
	};
		
	public static final String ALPHANUM = "[a-zA-Z0-9_]+";
	public static final String LOWERNUM = "[a-z0-9_]+";
	public static final String NUM = "[0-9]+";
	public static final String QUOTED = "[\"][^\"]+[\"]"; /* Empty titles and contents NOT allowed! */
	public static final String RATE = "(\\+|\\-)1";
	
	private final Set<CommandDef> cdefs;
	private final Scanner input;
	private boolean closed;
	
	public CommandParser(InputStream input) {
		
		this.input = new Scanner(input);
		this.closed = false;
		
		HashMap<String, CommandArgs> registerMap = new HashMap<>();
		registerMap.put( null, new CommandArgs( new String[] {ALPHANUM, ALPHANUM, LOWERNUM, LOWERNUM, LOWERNUM, LOWERNUM, LOWERNUM}, 3, 7) );
		
		HashMap<String, CommandArgs> loginMap = new HashMap<>();
		CommandArgs s = new CommandArgs(new String[]{ALPHANUM, ALPHANUM});
		loginMap.put( null, s);
		
		HashMap<String, CommandArgs> idOnlyMap = new HashMap<>();
		idOnlyMap.put(null, null);
		
		HashMap<String, CommandArgs> listMap = new HashMap<>();
		listMap.put("users", null);
		listMap.put("followers", null);
		listMap.put("following", null);
		
		HashMap<String, CommandArgs> userMap = new HashMap<>();
		userMap.put( null, new CommandArgs(new String[] {ALPHANUM}) );
		
		HashMap<String, CommandArgs> postMap = new HashMap<>();
		postMap.put( null, new CommandArgs(new String[] {QUOTED, QUOTED}) );
		
		HashMap<String, CommandArgs> showMap = new HashMap<>();
		showMap.put("feed", null);
		showMap.put("post", new CommandArgs(new String[]{NUM}, Arrays.asList(testLong)) );
		
		HashMap<String, CommandArgs> numMap = new HashMap<>();
		numMap.put(null, new CommandArgs(new String[] {NUM}, Arrays.asList(testLong)) );
		
		HashMap<String, CommandArgs> rateMap = new HashMap<>();
		rateMap.put( null, new CommandArgs(new String[] {NUM, RATE}, Arrays.asList(testLong, null)) );
		
		HashMap<String, CommandArgs> commentMap = new HashMap<>();
		commentMap.put( null, new CommandArgs(new String[] {NUM, QUOTED}, Arrays.asList(testLong, null)) );
		
		HashMap<String, CommandArgs> walletMap = new HashMap<>();
		walletMap.put(null, null);
		walletMap.put("btc", null);
		
		HashMap<String, CommandArgs> helpMap = new HashMap<>();
		helpMap.put(null, null); 
		helpMap.put("cmd", new CommandArgs(new String[] {CommandDef.ID_PARAM_REGEX}) );
		
		this.cdefs = new HashSet<>();
		
		this.cdefs.add(new CommandDef("register", registerMap));
		this.cdefs.add(new CommandDef("login", loginMap));
		this.cdefs.add(new CommandDef("logout", idOnlyMap));
		
		this.cdefs.add(new CommandDef("list", listMap));
		this.cdefs.add(new CommandDef("follow", userMap));
		this.cdefs.add(new CommandDef("unfollow", userMap));
		
		this.cdefs.add(new CommandDef("blog", idOnlyMap));
		this.cdefs.add(new CommandDef("post", postMap));
		this.cdefs.add(new CommandDef("show", showMap));
		
		this.cdefs.add(new CommandDef("delete", numMap));
		this.cdefs.add(new CommandDef("rewin", numMap));
		this.cdefs.add(new CommandDef("rate", rateMap));
		
		this.cdefs.add(new CommandDef("comment", commentMap));
		this.cdefs.add(new CommandDef("wallet", walletMap));
		this.cdefs.add(new CommandDef("help", helpMap));
		
		this.cdefs.add(new CommandDef("quit", idOnlyMap));
		this.cdefs.add(new CommandDef("exit", idOnlyMap));		
	}
	
	public CommandParser() { this(System.in); }
	
	public Command nextCmd() {
		Command cmd = null;
		String nextLine = this.input.nextLine();
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
	
	public boolean hasNextCmd() { return this.input.hasNextLine(); }
	
	public synchronized void close() throws Exception {
		if (!closed) { this.input.close(); closed = true; }
	}
	
	public synchronized boolean isClosed() { return closed; }
}