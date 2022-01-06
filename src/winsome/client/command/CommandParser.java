package winsome.client.command;

import java.io.*;
import java.util.*;
import java.util.function.*;

import winsome.util.*;


public final class CommandParser implements AutoCloseable {
	
	private static final Predicate<String> testLong = (str) -> {
		Common.notNull(str);
		try { Long.parseLong(str); return true; }
		catch (Exception ex) { return false; }
	};
	
	private static final BiPredicate<String, Integer> testTitleComm = (str, len) -> {
		String str2 = Common.dequote(str);
		return (str2.length() <= len);
	};
	
	private static final Predicate<String> testRate = (str) -> { return (str.equals("+1") || str.equals("-1")); };
	
	private static final Predicate<List<String>>
		postTest = (list) -> { //post <title> <comment>
			if (list.size() != 2) return false;
			else {
				String title = list.get(0), content = list.get(1);
				return testTitleComm.test(title, 20) && testTitleComm.test(content, 500);
			}
		},
		
		numTest = (list) -> { return (list.size() == 1 ? testLong.test(list.get(0)) : false); },
		
		rateTest = (list) -> { //rate <idPost> <vote>
			return (list.size() == 2) && testLong.test(list.get(0)) && testRate.test(list.get(1));
		},
		
		commentTest = (list) -> { //comment <idPost> <text>
			return (list.size() == 2 && testLong.test(list.get(0))) && testTitleComm.test(list.get(1), 100); //FIXME Vedere nella specifica
		};
	
	
	public static final String WHOAMI = "whoami";
		
	public static final String
		ALPHANUM = "[a-zA-Z0-9_]+",
		LOWERNUM = "[a-z0-9_]+",
		NUM = "[0-9]+",
		QUOTED = "[\"][^\"]+[\"]", /* Empty titles and contents NOT allowed! */
		RATE = "(\\+|\\-)1";
	
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
		postMap.put( null, new CommandArgs(new String[] {QUOTED, QUOTED}, postTest) );
		
		HashMap<String, CommandArgs> showMap = new HashMap<>();
		showMap.put("feed", null);
		showMap.put("post", new CommandArgs(new String[]{NUM}, numTest) );
		
		HashMap<String, CommandArgs> numMap = new HashMap<>();
		numMap.put(null, new CommandArgs(new String[] {NUM}, numTest) );
		
		HashMap<String, CommandArgs> rateMap = new HashMap<>();
		rateMap.put( null, new CommandArgs(new String[] {NUM, RATE}, rateTest) );
		
		HashMap<String, CommandArgs> commentMap = new HashMap<>();
		commentMap.put( null, new CommandArgs(new String[] {NUM, QUOTED}, commentTest) );
		
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
		
		this.cdefs.add(new CommandDef(WHOAMI, idOnlyMap));
	}
	
	public CommandParser() { this(System.in); }
	
	public Command nextCmd() {
		Command cmd = null;
		String nextLine = this.input.nextLine();
		if (CommandDef.matchWSpaceComment(nextLine)) return Command.NULL;
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