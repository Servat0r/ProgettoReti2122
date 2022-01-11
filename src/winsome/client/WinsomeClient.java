package winsome.client;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.net.*;
import java.rmi.*;
import java.rmi.registry.*;

import winsome.client.command.*;
import winsome.common.config.ConfigUtils;
import winsome.common.msg.*;
import winsome.common.rmi.*;
import winsome.util.*;

/**
 * Winsome client.
 * @author Salvatore Correnti.
 */
public final class WinsomeClient implements AutoCloseable {
	
	/** States of the server */
	private static enum State {
		INIT, /* Client initialized (set by constructor) */
		COMM, /* Client communicating (mainloop/run) (set by login) */
		EXIT, /* Client exiting (exit/quit request received by user) */
	};
	
	
	private static final String
		CLOSED = "Connection closed by server.",
		ILL_RESPONSE = "Illegal message received by server.",
		NONE_LOGGED = "no user logged in",
		ALREADY_LOGGED = "there is still a user logged in",
		INV_PARAM = "Invalid parameter: '%s'",
		INV_CMD = "Invalid command: '%s'",
		ILLARG = "Illegal argument passed";
	
	/* Logging strings */
	private static final String
		LOGSTR = "CLIENT @ %s: { %s : %s }",
		ERRLOGSTR = "CLIENT @ %s : ERROR @ %s : %s";
	
	private static final String
		WNOTIFIERNAME = "WalletNotifier",
		EMPTY = "";
	
	private CommandParser parser = null;
	/* Output stream */
	private PrintStream out = System.out;
	
	private Logger logger = null;
	/* TCP connection fields. */
	private String serverHost = null;
	private int tcpPort = 0;
	private Socket tcpSocket = null;
	private InputStream tcpIn = null;
	private OutputStream tcpOut = null;
	
	private ClientWalletNotifier walletNotifier = null;
	/* LinkedBlockingQueue for receiving multicast notifies. */
	private LinkedBlockingQueue<String> walletNotifies = null;
	
	private String regHost = null;
	private int regPort = 0;
	private Registry reg = null;
		
	private State state = State.INIT;
	
	private ClientRMI clHandler = null;
	private ServerRMI svHandler = null;
	
	private String username = EMPTY;
	private ConcurrentMap<String, List<String>> followers = new ConcurrentHashMap<>();
	private boolean fwset = false;
	
	
	/**
	 * @param tags Tags of a user.
	 * @return Formatted tag list.
	 */
	private static String formatTags(List<String> tags) {
		Common.notNull(tags);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tags.size(); i++) sb.append( (i > 0 ? ", " : "") + tags.get(i) );
		return sb.toString();
	}
	
	/**
	 * @param users Map of the form {username -> user tags}.
	 * @return A formatted string as described in {@link WinsomeClientHelp}.
	 */
	private String formatUserList(ConcurrentMap<String, List<String>> users) {
		String USER = "Utente", TAG = "Tag", SEPAR = " | ";
		char SUB = '-', SPACE = ' ';
		Common.notNull(users);
		int maxUserLen = USER.length(), maxTagLen = TAG.length(), cud, ctd;
		StringBuilder sb = new StringBuilder();
		synchronized (users) {
			for (String user : users.keySet()) {
				maxUserLen = Math.max( maxUserLen, user.length() );
				maxTagLen = Math.max( maxTagLen, formatTags(users.get(user)).length() );
			}
			cud = maxUserLen - USER.length();
			ctd = maxTagLen - TAG.length();
			sb.append(USER + Common.newCharSeq(cud, SPACE) + SEPAR);
			sb.append(TAG + Common.newCharSeq(ctd, SPACE) + "\n");
			sb.append(Common.newCharSeq(maxUserLen + SEPAR.length() + maxTagLen, SUB) + "\n");
			Iterator<String> userIter = users.keySet().iterator();
			String user;
			while (userIter.hasNext()) {
				user = userIter.next();
				String tags = formatTags(users.get(user));
				cud = maxUserLen - user.length();
				ctd = maxTagLen - tags.length();
				sb.append(user + Common.newCharSeq(cud, SPACE) + SEPAR);
				sb.append( tags + Common.newCharSeq(ctd, SPACE) + (userIter.hasNext() ? "\n" : "") );
			}
		}
		return sb.toString();
	}
	
	/**
	 * @param posts List of lists of the form {id, author, title}.
	 * @return A formatted string as described in {@link WinsomeClientHelp}.
	 */
	private String formatPostList(List<List<String>> posts) {
		String ID = "Id", AUTHOR = "Autore", TITLE = "Titolo", SEPAR = " | ";
		char SUB = '-', SPACE = ' ';
		Common.notNull(posts);
		StringBuilder sb = new StringBuilder();
		int maxIdLen = ID.length(), maxAuthorLen = AUTHOR.length(), maxTitleLen = TITLE.length(), cid, cad, ctd;
		for (List<String> post : posts) {
			Common.allAndArgs(post != null, post.size() == 3);
			maxIdLen = Math.max(maxIdLen, post.get(0).length());
			maxAuthorLen = Math.max(maxAuthorLen, post.get(1).length());
			maxTitleLen = Math.max(maxTitleLen, post.get(2).length());
		}
		cid = maxIdLen - ID.length(); cad = maxAuthorLen - AUTHOR.length(); ctd = maxTitleLen - TITLE.length();
		sb.append(ID + Common.newCharSeq(cid, SPACE) + SEPAR);
		sb.append(AUTHOR + Common.newCharSeq(cad, SPACE) + SEPAR);
		sb.append(TITLE + Common.newCharSeq(ctd, SPACE) + "\n");
		sb.append(Common.newCharSeq(2 * SEPAR.length() + maxIdLen + maxAuthorLen + maxTitleLen, SUB) + "\n");
		Iterator<List<String>> iter = posts.iterator();
		List<String> post;
		while (iter.hasNext()) {
			post = iter.next();
			String id = post.get(0), author = post.get(1), title = post.get(2);
			cid = maxIdLen - id.length();
			cad = maxAuthorLen - author.length();
			ctd = maxTitleLen - title.length();
			sb.append(id + Common.newCharSeq(cid, SPACE) + SEPAR);
			sb.append(author + Common.newCharSeq(cad, SPACE) + SEPAR);
			sb.append(title + Common.newCharSeq(ctd, SPACE) + (iter.hasNext() ? "\n" : "") );
		}
		return sb.toString();
	}
	
	/**
	 * @param data A list of {title, content, likes, dislikes, {comments}}.
	 * @return A formatted string as described in {@link WinsomeClientHelp}.
	 */
	private String formatPost(List<String> data) {
		Common.allAndArgs(data != null, data.size() >= 4);
		StringBuilder sb = new StringBuilder();
		String title = data.get(0), content = data.get(1), likes = data.get(2), dislikes = data.get(3);
		List<String> comments = (data.size() > 4 ? data.subList(4, data.size()) : null);
		sb.append("Titolo: " + title + "\n");
		sb.append("Contenuto: " + content + "\n");
		sb.append("Voti: " + likes + " positivi, " + dislikes + " negativi");
		sb.append("\nCommenti: ");
		if (comments != null) {
			sb.append("\n");
			Iterator<String> iter = comments.iterator();
			while (iter.hasNext()) {
				sb.append(iter.next()); //For simplicity, comment contains its author already formatted
				sb.append( iter.hasNext() ? "\n" : "" );
			}
		}
		return sb.toString();
	}
	
	/**
	 * @param args A list of {[bitcoin value,] wincoin value, {history}}.
	 * @param btc If true, formats the string to indicate also bitcoin value.
	 * @return A formatted string as described in {@link WinsomeClientHelp}.
	 */
	private String formatWallet(List<String> args, boolean btc) {
		Common.notNull(args);
		StringBuilder sb = new StringBuilder();
		if (btc) {
			sb.append("Valore (bitcoin): " + args.get(0) + "\n");
			args = args.subList(1, args.size());
		}
		sb.append("Valore (wincoin): " + args.get(0) + "\n");
		sb.append("Transazioni effettuate:");
		for (int i = 1; i < args.size(); i++) sb.append("\n" + args.get(i));
		return sb.toString();
	}

	private boolean printfError(String msg, Object... args) { this.out.printf("Error: " + msg, args); return false; }
	
	private boolean printError(String msg) { this.out.println("Error: " + msg); return false; }
	
	private boolean printOK(String fmt, Object...objs) { this.out.println(String.format(fmt, objs)); return true; }
	
	private State getState() {
		synchronized (this.state) { return this.state; }
	}
	private void setState(State state) { synchronized (this.state) { this.state = state; } }
	
	
	private void setUsername(String str) {
		synchronized (EMPTY) { if (username.isEmpty()) this.username = new String(str); }
	}
	private void unsetUsername() { synchronized (EMPTY) { this.username = EMPTY; } }
	private boolean isUserSet() { synchronized (EMPTY) { return !username.isEmpty(); } }
	private String getUsername() { synchronized (EMPTY) { return new String(username); } }
	
	/**
	 * Dispatches the current received command to the corresponding method and handles returned results.
	 * @param cmd Current received command.
	 * @return true if the method executed correctly, false in case of a (not fatal) error, e.g. in case
	 * of connection closed by the server.
	 * @throws IOException If an IO error occurs, different from closure of any socket.
	 * @throws InterruptedException Thrown by {@link Process#waitFor()}.
	 */
	private boolean dispatch(Command cmd) throws IOException, InterruptedException {
		Common.notNull(cmd);
		String id = cmd.getId();
		String param = cmd.getParam();
		if (param == null || param == Command.EMPTY) param = new String(Message.EMPTY);
		List<String> args = cmd.getArgs();
		boolean result;
		
		try {
			if ( id.equals(Message.REG) ) {
				List<String> sbargs = new ArrayList<>();
				for (int i = 2; i < args.size(); i++) sbargs.add(args.get(i));
				Pair<Boolean, String> pair = this.register(args.get(0), args.get(1), sbargs);
				result = pair.getKey().booleanValue();
				this.out.println(pair.getValue());
			}
			
			else if ( id.equals(Message.LOGIN) ) {
				String u = args.get(0);
				result = this.login(args.get(0), args.get(1));
				if (result) { this.setUsername(u); this.parser.setPrompt("%s> ", u); }
			}
			
			else if ( id.equals(Message.QUIT) || id.equals(Message.EXIT)) {
				if (this.isUserSet()) return this.printError(ALREADY_LOGGED);
				else {
					result = this.quitReq();
					if (result) this.setState(State.EXIT);
				}
			}
			
			else if ( id.equals(CommandParser.WHOAMI) ) {
				String res = this.getUsername();
				if (res != null) this.out.println(res);
				else this.out.println(NONE_LOGGED);
				result = true;
			}
						
			else if ( id.equals(CommandParser.CLEAR) ) {
				int code;
				try { code = new ProcessBuilder("clear").inheritIO().start().waitFor(); }
				catch (IOException ioe) { logger.logStackTrace(ioe); code = 1; }
				try { if (code != 0) code = new ProcessBuilder("cls").inheritIO().start().waitFor(); }
				catch (IOException ioe) { logger.logStackTrace(ioe); code = 1; }
				try { if (code != 0) code = new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor(); }
				catch (IOException ioe) { logger.logStackTrace(ioe); code = 1; }
				result = (code == 0);
			}
			
			else if ( id.equals(CommandParser.WAIT) ) {
				long millis = ConfigUtils.newLong.apply(args.get(0));
				result = Common.sleep(millis * 1000);
				out.println("End");
			}
			
			else if ( id.equals(Message.LOGOUT) ) {
				if (!this.isUserSet()) return this.printError(NONE_LOGGED);
				else {
					result = this.logout(this.getUsername());
					if (result) { this.unsetUsername(); this.parser.resetPrompt(); }
				}
			}
			
			else if ( id.equals(Message.LIST) ) {
				if (param.equals(Message.USERS)) result = this.listUsers();
				else if (param.equals(Message.FOLLOWERS)) result = this.listFollowers();
				else if (param.equals(Message.FOLLOWING)) result = this.listFollowing();
				else return this.printError(Common.excStr(INV_PARAM, param));
			}
			
			else if ( id.equals(Message.FOLLOW) ) result = this.followUser(args.get(0));
			
			else if ( id.equals(Message.UNFOLLOW) ) result = this.unfollowUser(args.get(0));
			
			else if ( id.equals(Message.BLOG) ) result = this.viewBlog();
			
			else if ( id.equals(Message.POST) ) result = this.createPost(args.get(0), args.get(1));
			
			else if ( id.equals(Message.SHOW) ) {
				if (param.equals(Message.FEED)) result = this.showFeed();
				else if (param.equals(Message.POST)) result = this.showPost(Long.parseLong(args.get(0)));
				else return this.printError(Common.excStr(INV_PARAM, param));
			}
			
			else if ( id.equals(Message.DELETE) ) result = this.deletePost(Long.parseLong(args.get(0)));
			
			else if ( id.equals(Message.REWIN) ) result = this.rewinPost(Long.parseLong(args.get(0)));
			
			else if ( id.equals(Message.RATE) ) result = this.ratePost(Long.parseLong(args.get(0)), args.get(1));
			
			else if ( id.equals(Message.COMMENT) ) result = this.addComment(Long.parseLong(args.get(0)), args.get(1));
			
			else if ( id.equals(Message.WALLET) ) {
				switch (param) {
					case Message.EMPTY : { result = this.getWallet(); break; }
					case Message.BTC : { result = this.getWalletInBitcoin(); break; }
					case Message.NOTIFY : { 
						List<String> notifies = this.walletNotifies();
						StringBuilder sb = new StringBuilder();
						for (String str : notifies) sb.append(str + "\n");
						this.out.print(sb.toString());
						result = true;
						break;
					} default : return this.printError(Common.excStr(INV_PARAM, param));
				}				 
			}
			
			else if ( id.equals(Message.HELP) ) {
				if (param == null || param.equals(Message.EMPTY) ) {
					List<String> l = cmd.getArgs();
					result = ( l.isEmpty() ? this.help() : this.help(l.get(0)) );
				} else return this.printError(Common.excStr(INV_PARAM, param));
			}
			else return this.printError(Common.excStr(INV_CMD, id));
			
			return result;
		} catch (IllegalArgumentException ex) { return this.printError(Common.excStr(ILLARG)); }
	}
	
	/**
	 * Simple request template: sends a message and receives a message that contains a text message
	 *  as only argument.
	 * @param type Id string for the message to send.
	 * @param parameter Parameter string.
	 * @param args Arguments.
	 * @return true on success, false on error.
	 * @throws IOException On I/O errors.
	 */
	private boolean simpleRequest(String type, String parameter, List<String> args) throws IOException {
		try {
			Message msg = new Message(type, parameter, args);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				List<String> l = msg.getArguments();
				if (l.isEmpty()) return this.printError(ILL_RESPONSE);
				String confirm = l.remove(0);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.EMPTY)) return this.printError(ILL_RESPONSE);
					return this.printOK(confirm);
				} else if (id.equals(Message.ERR)) return this.printError(confirm);
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }		
	}
	
	/**
	 * Handles a wallet request: sends a (WALLET, EMPTY/BTC) message and receives
	 *  a message (OK, WALLET) with value(s) and transaction history.
	 * @param btc If true, the request is with parameter BTC.
	 * @return true on success, false on error.
	 * @throws IOException On I/O errors.
	 */
	private boolean walletRequest(boolean btc) throws IOException {
		try {
			Message msg = new Message(Message.WALLET, (btc ? Message.BTC : Message.EMPTY), null);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			if ((msg = Message.recvFromStream(tcpIn)) == null) return this.printError(CLOSED);
			String[] strCodes = msg.getIdParam();
			String id = strCodes[0], param = strCodes[1];
			List<String> l = msg.getArguments();
			if (l.isEmpty()) return this.printError(ILL_RESPONSE);
			String confirm = l.remove(0);
			if (id.equals(Message.OK)) {
				if (!param.equals(Message.WALLET)) return this.printError(ILL_RESPONSE);
				
				String output = this.formatWallet(l, btc);
				return this.printOK("%s%n%s", confirm, output);
			} else if (id.equals(Message.ERR)) return this.printError(confirm);
			else return this.printError(ILL_RESPONSE);
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }				
	}
	
	/**
	 * Sets followers list after successful login.
	 * @param followers Map user -> tags received by server.
	 * @return true on success, false on error.
	 */
	private boolean setFollowers(ConcurrentMap<String, List<String>> followers) {
		if (followers == null) return false;
		Set<String> keys = followers.keySet();
		Common.collectionNotNull(keys);
		synchronized (this.followers) {
			if (fwset) return false; //Prevent multiple settings
			if (keys.size() > 0) this.followers = followers;
			fwset = true;			
		}
		return true;
	}
	
	private boolean setFollowers() {
		synchronized (this.followers) {
			if (fwset) return false; else { fwset = true; return true; }
		}
	}
	
	private void clearFollowers() { synchronized (this.followers) { this.followers.clear(); fwset = false; } }
	
	/** Called by {@link #clHandler} for adding a follower. */
	boolean addFollower(String username, List<String> tags) {
		Common.notNull(username, tags);
		synchronized (this.followers) {
			if (!this.followers.containsKey(username)) { this.followers.put(username, tags); return true; }
			else return false; //Already following
		}
	}
	
	/** Called by {@link #clHandler} for removing a follower. */
	boolean removeFollower(String username) {
		Common.notNull(username);
		synchronized (this.followers) {
			if (this.followers.containsKey(username)) { this.followers.remove(username); return true; }
			else return false; //Not following
		}
	}
	
	/**
	 * Builds a WinsomeClient based on configuration map.
	 * @param configMap Configuration map (e.g. as returned by ConfigParser::parseFile).
	 * @throws RemoteException Thrown by initialization of this.reg / this.svHandler.
	 * @throws NotBoundException Thrown by this.reg::lookup.
	 * @throws IOException If an IO error occurs.
	 */
	public WinsomeClient(Map<String, String> configMap) throws RemoteException, NotBoundException, IOException {
		Common.notNull(configMap);
		
		Function<String, String> newStr = ConfigUtils.newStr;
		Function<String, Integer> newInt = ConfigUtils.newInt;
		Function<String, InputStream> newInStr = ConfigUtils.newInputStream;
		Function<String, PrintStream> newPrStr = ConfigUtils.newPrintStream;
		
		InputStream input;
		PrintStream stream;
		
		regHost = ConfigUtils.setValueOrDefault(configMap, "reghost", newStr, "localhost");
		serverHost = ConfigUtils.setValueOrDefault(configMap, "server", newStr, "127.0.0.1");
		tcpPort = ConfigUtils.setValueOrDefault(configMap, "tcpport", newInt, 0);
		regPort = ConfigUtils.setValueOrDefault(configMap, "regport", newInt, 0);
		input = ConfigUtils.setValueOrDefault(configMap, "input", newInStr, System.in);
		out = ConfigUtils.setValueOrDefault(configMap, "output", newPrStr, System.out);
		stream = ConfigUtils.setValueOrDefault(configMap, "logger", newPrStr, System.out);
		logger = new Logger(LOGSTR, ERRLOGSTR, stream);
		
		this.parser = CommandParser.defaultParser(input);
		this.clHandler = new ClientRMIImpl(this);
		
		this.walletNotifies = new LinkedBlockingQueue<>();
		
		logger.log("ServerHost = %s, tcpPort = %d", this.serverHost, this.tcpPort);
		
		this.tcpSocket = new Socket(this.serverHost, this.tcpPort);
		this.tcpIn = this.tcpSocket.getInputStream();
		this.tcpOut = this.tcpSocket.getOutputStream();
		
		this.reg = LocateRegistry.getRegistry(regHost, regPort);
		this.svHandler = (ServerRMI) this.reg.lookup(ServerRMI.REGSERVNAME);
	}
		
	public final LinkedBlockingQueue<String> walletNotifyingList() { return this.walletNotifies; }
	
	public final String tcpHost() { return this.serverHost; }

	public final PrintStream out() {return this.out; }
	
	
	/**
	 * Mainloop of the client: waits for a command while parser InputStream is open and it has
	 * not received and correctly executed a quit/exit request, then executes it. 
	 * @return 0 if all operations executed successfully, 1 on error.
	 * @throws Exception
	 */
	public int mainloop() throws Exception {
		int retCode = 0;
		Command cmd;
		while (this.getState() != State.EXIT) {
			cmd = this.parser.nextCmd();
			if (cmd == null) { this.printError("Unrecognized command (null)"); break; }
			else if (cmd.equals(Command.NULL)) { out.println("Unrecognized command"); }
			else if (cmd.equals(Command.SKIP)) {}
			else {
				try { this.dispatch(cmd); }
				catch (IOException e) { logger.logStackTrace(e); retCode = 1; break; }
			}
			out.println();
		}
		return retCode;
	}
	
	public Pair<Boolean, String> register(String username, String password, List<String> tags) throws RemoteException {
		Common.notNull(username, password, tags);
		return this.svHandler.register(username, password, tags);
	}
	
	
	public boolean login(String username, String password) throws IOException {
		Common.notNull(username, password);
		try {
			Message msg = new Message(Message.LOGIN, Message.EMPTY, Common.toList(username, password) );
			if ( !msg.sendToStream(tcpOut) ) return this.printError(CLOSED);
			
			msg = Message.recvFromStream(tcpIn);
			if (msg == null) return this.printError(CLOSED);
			
			String id, param;
			id = msg.getIdStr(); param = msg.getParamStr();
			
			List<String> l = msg.getArguments();
			if (l.isEmpty()) return this.printError(ILL_RESPONSE);
			String confirm = l.remove(0);
			
			if (id.equals(Message.OK) && param.equals(Message.INFO)) {
				if (l.size() < 3) return this.printError(ILL_RESPONSE);
				String 
					mcastAddr = l.remove(0),
					str1 = l.remove(0),
					str2 = l.remove(0);
				
				int mcastPort, mcastMsgLen;
				try {
					mcastPort = Integer.parseInt(str1);
					mcastMsgLen = Integer.parseInt(str2);
				} catch (NumberFormatException nfe)
					{ return this.printfError("<%s, %s> are not valid integers!%n", str1, str2); }
				this.walletNotifier = new ClientWalletNotifier(this, mcastPort, mcastAddr, mcastMsgLen);
				this.walletNotifier.setDaemon(true);
				this.walletNotifier.setName(WNOTIFIERNAME);
				this.walletNotifier.start();
				if (!l.isEmpty()) {
					if ( !this.setFollowers( Serialization.deserializeMap(l) ) )
						{ return this.printError("when retrieving current followers"); }
				} else if (!this.setFollowers()) return this.printError("when retrieving current followers");
				if ( !this.svHandler.followersRegister(username, this.clHandler))
					{ return this.printError("when registering for followers updates"); }
				this.setState(State.COMM);
				return this.printOK(confirm);
				
			} else if (id.equals(Message.ERR)) {
				return this.printError(confirm);
			
			} else return this.printError(ILL_RESPONSE);
			
		} catch (MessageException | IllegalArgumentException ex) { logger.logStackTrace(ex); return false; }
	}
	
	public boolean logout(String username) throws IOException, InterruptedException {
		Common.notNull(username);
		try {
			Message msg = new Message(Message.LOGOUT, Message.EMPTY, Common.toList(username));
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			if ((msg = Message.recvFromStream(tcpIn)) == null) return this.printError(CLOSED);
			String id = msg.getIdStr(), param = msg.getParamStr();
			List<String> l = msg.getArguments();
			if (l.size() != 1) return this.printError(ILL_RESPONSE);
			String confirm = l.remove(0);
			if (id.equals(Message.OK)) {
				if (!param.equals(Message.EMPTY)) return this.printError(ILL_RESPONSE);
				/* Closing multicast handling thread (does NOT join the other thread!) */
				if (this.walletNotifier != null) this.walletNotifier.close();
				this.walletNotifier.join();
				/* Deregistering for followers updates */
				if ( !this.svHandler.followersUnregister(this.getUsername()) )
					{ return this.printError("when unregistering for followers updates"); }
				/* Clearing followers list */
				this.clearFollowers();
				/* Printing result */
				return this.printOK(confirm);
			} else if (id.equals(Message.ERR)) return this.printError(confirm);
			else return this.printError(ILL_RESPONSE);
		} catch (MessageException ex) { logger.logStackTrace(ex); return false; }
	}
	
	public boolean listUsers() throws IOException {
		try {
			Message msg = new Message(Message.LIST, Message.USERS, null);
			if ( !(msg.sendToStream(tcpOut)) ) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = Message.getIdParam(msg.getIdCode(), msg.getParamCode());
				String id = strCodes[0], param = strCodes[1];
				List<String> l = msg.getArguments();
				if (l.isEmpty()) return this.printError(ILL_RESPONSE);
				String confirm = l.remove(0);
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.USLIST)) return this.printError(ILL_RESPONSE);
					ConcurrentMap<String, List<String>> map;
					if (!l.isEmpty()) {
						map = Serialization.deserializeMap(l);
						if (map == null) return this.printError("when retrieving users list");
					} else map = new ConcurrentHashMap<>();
					String output = this.formatUserList(map);
					if (output == null) return this.printError("when formatting users list output");
					return this.printOK("%s%n%s", confirm, output);
				} else if (id.equals(Message.ERR)) return this.printError(confirm);
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }
	}
	
	public boolean listFollowers() {
		String output;
		synchronized (this.followers) { output = this.formatUserList(this.followers); }
		if (output != null) { this.out.println(output); return true; }
		else return this.printError("when formatting followers list output");
	}
	
	public boolean listFollowing() throws IOException {
		try {
			Message msg = new Message(Message.LIST, Message.FOLLOWING, null);
			if ( !(msg.sendToStream(tcpOut)) ) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = Message.getIdParam(msg.getIdCode(), msg.getParamCode());
				String id = strCodes[0], param = strCodes[1];
				List<String> l = msg.getArguments();
				if (l.isEmpty()) return this.printError(ILL_RESPONSE);
				String confirm = l.remove(0);
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.USLIST)) return this.printError(ILL_RESPONSE);
					ConcurrentMap<String, List<String>> map;
					if (l.size() > 0) {
						map = Serialization.deserializeMap(l);
						if (map == null) return this.printError("when retrieving following users list");
					} else map = new ConcurrentHashMap<>();
					String output = this.formatUserList(map);
					if (output == null) return this.printError("when formatting following users list output");
					return this.printOK("%s%n%s", confirm, output);
				} else if (id.equals(Message.ERR)) return this.printError(confirm);
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }
	}
	
	public boolean followUser(String idUser) throws IOException {
		Common.notNull(idUser);
		return this.simpleRequest(Message.FOLLOW, Message.EMPTY, Common.toList(idUser));
	}
	
	public boolean unfollowUser(String idUser) throws IOException {
		Common.notNull(idUser);
		return this.simpleRequest(Message.UNFOLLOW, Message.EMPTY, Common.toList(idUser));
	}
	
	public boolean viewBlog() throws IOException { 
		try {
			Message msg = new Message(Message.BLOG, Message.EMPTY, null);
			if (!msg.sendToStream(tcpOut)) { return this.printError(CLOSED); }
			else {
				msg = Message.recvFromStream(tcpIn);
				List<String> l = msg.getArguments();
				if (l.size() % 3 != 1) return this.printError(ILL_RESPONSE);
				String confirm = l.remove(0);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.PSLIST)) return this.printError(ILL_RESPONSE);
					List<List<String>> posts;
					if (l.size() > 0) {
						posts = Serialization.deserializePostList(l, 3);
						if (posts == null) return this.printError("when getting post list");
					} else posts = new ArrayList<>();
					String output = this.formatPostList(posts);
					if (output == null) return this.printError("when formatting post list");
					else return this.printOK("%s%n%s", confirm, output);
				} else if (id.equals(Message.ERR)) return this.printError(confirm);
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }
	}
	
	public boolean createPost(String title, String content) throws IOException {
		Common.notNull(title, content);
		return this.simpleRequest(Message.POST, null, Common.toList(title, content));
	}
	
	public boolean showFeed() throws IOException {
		try {
			Message msg = new Message(Message.SHOW, Message.FEED, null);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				List<String> l = msg.getArguments();
				if (l.size() % 3 != 1) return this.printError(ILL_RESPONSE);
				String confirm = l.remove(0);
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.PSLIST)) return this.printError(ILL_RESPONSE);
					List<List<String>> posts = Serialization.deserializePostList(l, 3);
					if (posts == null) return this.printError("When getting post list");
					String output = this.formatPostList(posts);
					if (output == null) return this.printError("When formatting post list");
					return this.printOK("%s%n%s", confirm, output);
				} else if (id.equals(Message.ERR)) return this.printError(confirm);
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }		
	}
	
	public boolean showPost(long idPost) throws IOException {
		Common.allAndArgs(idPost >= 0);
		try {
			Message msg = new Message( Message.SHOW, Message.POST, Common.toList(Long.toString(idPost)) );
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				List<String> l = msg.getArguments();
				if (l.isEmpty()) return this.printError(ILL_RESPONSE);
				String confirm = l.remove(0);
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.POST)) return this.printError(ILL_RESPONSE);
					
					String output = this.formatPost(l);
					if (output == null) return this.printError("when formatting post data");
					return this.printOK("%s%n%s", confirm, output);
				} else if (id.equals(Message.ERR)) return this.printError(confirm);
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }		
	}
	
	public boolean deletePost(long idPost) throws IOException {
		Common.allAndArgs(idPost >= 0);
		return this.simpleRequest(Message.DELETE, Message.EMPTY, Common.toList(Long.toString(idPost)));
	}
	
	public boolean rewinPost(long idPost) throws IOException {
		Common.allAndArgs(idPost >= 0);
		return this.simpleRequest(Message.REWIN, Message.EMPTY, Common.toList(Long.toString(idPost)));
	}
	
	public boolean ratePost(long idPost, String vote) throws IOException {
		Common.allAndArgs(idPost >= 0, vote != null);
		return this.simpleRequest( Message.RATE, null, Common.toList(Long.toString(idPost), vote) );
	}
	
	public boolean addComment(long idPost, String comment) throws IOException {
		Common.allAndArgs(idPost >= 0, comment != null);
		return this.simpleRequest(Message.COMMENT, null, Common.toList(Long.toString(idPost), comment) );
	}
	
	/**
	 * Prints current wincoin value with the history of all transactions (one per line).
	 * @return true on success, false on error (connection closed, wrong answer etc.).
	 * @throws IOException If an IO error (different from connection closing) occurs.
	 */
	public boolean getWallet() throws IOException { return this.walletRequest(false); }

	/**
	 * Prints current wincoin value and its bitcoin correspondent with the history of
	 * all transactions (one per line).
	 * @return true on success, false on error (connection closed, wrong answer etc.).
	 * @throws IOException If an IO error (different from connection closing) occurs.
	 */	
	public boolean getWalletInBitcoin() throws IOException { return this.walletRequest(true); }
	
	
	public List<String> walletNotifies() {
		List<String> result = new ArrayList<>();
		String curr;
		while ( (curr = walletNotifies.poll()) != null) result.add(curr);
		return result;
	}
	
	/**
	 * Prints help guide for the specified command.
	 * @param cmd Command.
	 * @return true if cmd is a valid command, false otherwise.
	 */
	public boolean help(String cmd) {
		Common.notNull(cmd);
		String msg = WinsomeClientHelp.getCmdHelp(cmd);
		if (msg != null) { this.out.println(msg); return true; }
		else return false;
	}
	
	/**
	 * Prints help guide for all commands.
	 * @return true
	 */
	public boolean help() {
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = WinsomeClientHelp.commandsList().iterator();
		while (it.hasNext()) sb.append(WinsomeClientHelp.getCmdHelp(it.next()) + "\n");
		this.out.println(sb.toString());
		return true;
	}
	
	public boolean quitReq() throws IOException {
		try {
			Message msg = new Message(Message.QUIT, Message.EMPTY, null);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				List<String> l = msg.getArguments();
				if (l.isEmpty()) return this.printError(ILL_RESPONSE);
				String confirm = l.remove(0);
				if (id.equals(Message.OK)) {
					if (param.equals(Message.QUIT) || param.equals(Message.EXIT))
						return this.printOK(confirm);
					else return this.printError(ILL_RESPONSE);
				} else if (id.equals(Message.ERR)) return this.printError(confirm);
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { logger.logStackTrace(mex); return false; }		
	}
	
	public Logger logger() { return logger; }
	
	/**
	 * Releases all resources associated with client: closes parser, tcp connection, IO streams,
	 * wallet notifier and clears all data about current user and followers.
	 */
	public synchronized void close() throws Exception {
		this.setState(State.EXIT);
		this.parser.close();
		logger.log("Parser closed");
		if (!this.tcpSocket.isClosed()) this.tcpSocket.close();
		logger.log("TCP connection closed");
		if (!this.out.equals(System.out)) this.out.close();
		logger.log("I/O streams closed");
		if (this.walletNotifier != null) {
			this.walletNotifier.close();
			this.walletNotifier.join();
		}
		logger.log("WalletNotifier terminated");
		String user = this.getUsername();
		if (user != null) this.svHandler.followersUnregister(user);
		if (this.isUserSet()) this.unsetUsername();
		this.clearFollowers();
		logger.log("Followers service deregistered");
		this.logger.close();
	}
	
	public String toString() { return Common.jsonString(this); }
}