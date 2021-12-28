package winsome.client;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.rmi.*;
import java.rmi.registry.*;

import winsome.client.command.*;
import winsome.common.msg.*;
import winsome.common.rmi.*;
import winsome.util.*;

public final class WinsomeClient implements AutoCloseable {
	
	private static enum State {
		INIT, /* Client initialized (set by constructor) */
		COMM, /* Client communicating (mainloop/run) (set by login) */
		EXIT, /* Client exiting (exit/quit request received by user) */
	};
	
	private static final String
		INTRO = "This is the WinsomeClient. Type \"help\" for help on available commands or "
			+ "\"help cmd <command>\" to get help on <command>, or any command to get it executed.",
	
		EXIT = "Thanks for having used WinsomeClient.",
		CLOSED = "Connection closed by server.",
		ILL_RESPONSE = "Illegal message received by server.",
		NONE_LOGGED = "no user logged in",
		ALREADY_LOGGED = "there is still a user logged in",
		INV_PARAM = "Invalid parameter: '",
		INV_CMD = "Invalid command: '",
		ILLARG = "Illegal argument passed";
	
	private CommandParser parser = null;
	
	private String serverHost = null;
	private int tcpPort = 0;
	private Socket tcpSocket = null;
	private InputStream tcpIn = null;
	private OutputStream tcpOut = null;
	private int soTimeout = 0;
	
	private WalletNotifier mcastThread = null;
	
	private String regHost = null;
	private int regPort = 0;
	private Registry reg = null;
		
	private State state = State.INIT;
	
	private InputStream in = System.in;
	private PrintStream out = System.out;
	private PrintStream err = System.err;
	
	private ClientInterface clHandler = null;
	private ServerInterface svHandler = null;
	
	private String username = null;
	private ConcurrentMap<String, List<String>> followers = new ConcurrentHashMap<>();
	
	private static String printTags(List<String> tags) {
		Common.notNull(tags);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tags.size(); i++) sb.append( (i > 0 ? ", " : "") + tags.get(i) );
		return sb.toString();
	}
	
	private String formatUserList(ConcurrentMap<String, List<String>> users) {
		String USER = "Utente", TAG = "Tag", SEPAR = " | ";
		char SUB = '-', SPACE = ' ';
		Common.notNull(users);
		int maxUserLen = USER.length(), maxTagLen = TAG.length(), cud, ctd;
		StringBuilder sb = new StringBuilder();
		synchronized (users) {
			for (String user : users.keySet()) {
				maxUserLen = Math.max( maxUserLen, user.length() );
				maxTagLen = Math.max( maxTagLen, printTags(users.get(user)).length() );
			}
			Common.debugf("maxKeyLen = %d, maxValueLen = %d%n", maxUserLen, maxTagLen);
			cud = maxUserLen - USER.length();
			ctd = maxTagLen - TAG.length();
			sb.append(USER + Common.newCharSeq(cud, SPACE) + SEPAR);
			sb.append(TAG + Common.newCharSeq(ctd, SPACE) + "\n");
			sb.append(Common.newCharSeq(maxUserLen + SEPAR.length() + maxTagLen, SUB) + "\n");
			Iterator<String> userIter = users.keySet().iterator();
			String user;
			while (userIter.hasNext()) {
				user = userIter.next();
				String tags = printTags(users.get(user));
				cud = maxUserLen - user.length();
				ctd = maxTagLen - tags.length();
				sb.append(user + Common.newCharSeq(cud, SPACE) + SEPAR);
				sb.append( tags + Common.newCharSeq(ctd, SPACE) + (userIter.hasNext() ? "\n" : "") );
			}
		}
		return sb.toString();
	}
	
	private String formatPostList(List<List<String>> posts) {
		String ID = "Id", AUTHOR = "Autore", TITLE = "Titolo", SEPAR = " | ";
		char SUB = '-', SPACE = ' ';
		Common.notNull(posts);
		StringBuilder sb = new StringBuilder();
		int maxIdLen = ID.length(), maxAuthorLen = AUTHOR.length(), maxTitleLen = TITLE.length(), cid, cad, ctd;
		for (List<String> post : posts) {
			Common.checkAll(post != null, post.size() == 3);
			maxIdLen = Math.max(maxIdLen, post.get(0).length());
			maxAuthorLen = Math.max(maxAuthorLen, post.get(1).length());
			maxTitleLen = Math.max(maxTitleLen, post.get(2).length());
		}
		Common.debugf("maxIdLen = %d, maxAuthorLen = %d, maxTitleLen = %d%n", maxIdLen, maxAuthorLen, maxTitleLen);
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
	
	private String formatPost(List<String> data) {
		Common.checkAll(data != null, data.size() >= 4);
		StringBuilder sb = new StringBuilder();
		String title = data.get(0), content = data.get(1), likes = data.get(2), dislikes = data.get(3);
		title = title.substring(1, title.length()-1);
		content = content.substring(1, content.length()-1);
		List<String> comments = (data.size() > 4 ? data.subList(4, data.size()) : null);
		sb.append("Titolo: " + title + "\n");
		sb.append("Contenuto: " + content + "\n");
		sb.append("Voti: " + likes + " positivi, " + dislikes + " negativi");
		if (comments != null) {
			sb.append("\n");
			Iterator<String> iter = comments.iterator();
			while (iter.hasNext()) {
				sb.append("\t" + iter.next()); //For simplicity, comment contains its author already formatted
				sb.append( iter.hasNext() ? "\n" : "" );
			}
		}
		return sb.toString();
	}
	
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

	private boolean printfError(String msg, Object... args) { this.err.printf("Error: " + msg, args); return false; }
	
	private boolean printError(String msg) { this.err.println("Error: " + msg); return false; }
	
	private boolean printOK(String msg) { this.out.println(msg); return true; }
	
	private State getState() {
		State s;
		synchronized (this.state) { s = this.state; }
		return s;
	}

	private void setState(State state) { synchronized (this.state) { this.state = state; } }
		
	private synchronized void setUsername(String str) {
		if (username == null) this.username = new String(str);
	}
	
	private synchronized void unsetUsername() { this.username = null; }
	
	private synchronized boolean isUserSet() { return (username != null); }
	
	private synchronized String getUsername() { return (username != null ? new String(username) : null); }
	
	/**
	 * Dispatches the current received command to the corresponding method and handles returned results.
	 * @param cmd Current received command.
	 * @return true if the method executed correctly, false in case of a (not fatal) error, e.g. in case
	 * of connection closed by the server.
	 * @throws IOException If an IO error occurs, different from closure of any socket.
	 */
	private boolean dispatch(Command cmd) throws IOException {
		Common.notNull(cmd);
		String id = cmd.getId();
		String param = cmd.getParam();
		List<String> args = cmd.getArgs();
		boolean result;
		
		try {
			if ( id.equals(Message.REG) )
				{result = this.register(args.get(0), args.get(1), args.subList(2, args.size()) ); }
			
			else if ( id.equals(Message.LOGIN) ) {
				String u = args.get(0);
				result = this.login(args.get(0), args.get(1));
				if (result) this.setUsername(u);
			}
			
			else if ( id.equals(Message.QUIT) || id.equals(Message.EXIT)) {
				if (this.isUserSet()) return this.printError(ALREADY_LOGGED);
				else { this.setState(State.EXIT); result = true; }
			}
			//TODO Togliere questa riga? (thin client, è il server a verificare!)
			else if (!this.isUserSet()) return this.printError(NONE_LOGGED);
			
			else if ( id.equals(Message.LOGOUT) ) {
				if (!this.isUserSet()) return this.printError(NONE_LOGGED);
				else { result = this.logout(this.getUsername()); if (result) this.unsetUsername(); }
			}
			
			else if ( id.equals(Message.LIST) ) {
				if (param.equals(Message.USERS)) result = this.listUsers();
				else if (param.equals(Message.FOLLOWERS)) result = this.listFollowers();
				else if (param.equals(Message.FOLLOWING)) result = this.listFollowing();
				else return this.printError(Common.excStr(INV_PARAM + param + "'"));
			}
			
			else if ( id.equals(Message.FOLLOW) ) result = this.followUser(args.get(0));
			
			else if ( id.equals(Message.UNFOLLOW) ) result = this.unfollowUser(args.get(0));
			
			else if ( id.equals(Message.BLOG) ) result = this.viewBlog();
			
			else if ( id.equals(Message.POST) ) result = this.createPost(args.get(0), args.get(1));
			
			else if ( id.equals(Message.SHOW) ) {
				if (param.equals(Message.FEED)) result = this.showFeed();
				else if (param.equals(Message.POST)) result = this.showPost(Long.parseLong(args.get(0)));
				else return this.printError(Common.excStr(INV_PARAM + param + "'"));
			}
			
			else if ( id.equals(Message.DELETE) ) result = this.deletePost(Long.parseLong(args.get(0)));
			
			else if ( id.equals(Message.REWIN) ) result = this.rewinPost(Long.parseLong(args.get(0)));
			
			else if ( id.equals(Message.RATE) ) result = this.ratePost(Long.parseLong(args.get(0)), args.get(1));
			
			else if ( id.equals(Message.COMMENT) ) result = this.addComment(Long.parseLong(args.get(0)), args.get(1));
			
			else if ( id.equals(Message.WALLET) ) {
				if ( param == null || param.equals(Message.EMPTY) ) result = this.getWallet();
				else if (param.equals(Message.BTC)) result = this.getWalletInBitcoin();
				else return this.printError(Common.excStr(INV_PARAM + param + "'"));
			}
			
			else if ( id.equals(Message.HELP) ) {
				if (param == null || param.equals(Message.EMPTY) ) result = this.help();
				else if (param.equals(Message.CMD)) result = this.help(args.get(0));
				else return this.printError(Common.excStr(INV_PARAM + param + "'"));
			}
			else return this.printError(Common.excStr(INV_CMD + id + "'"));
			
			return result;
		} catch (IllegalArgumentException ex) { return this.printError(Common.excStr(ILLARG)); }
	}
	
	private boolean simpleRequest(String type, String parameter, List<String> args) throws IOException {
		try {
			Message msg = new Message(type, parameter, args);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.EMPTY)) return this.printError(ILL_RESPONSE);
					return this.printOK(msg.getArguments().get(0));
				} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { mex.printStackTrace(err); return false; }		
	}
	
	private boolean walletRequest(boolean btc) throws IOException {
		try {
			Message msg = new Message(Message.WALLET, (btc ? Message.BTC : Message.EMPTY), null);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			if ((msg = Message.recvFromStream(tcpIn)) == null) return this.printError(CLOSED);
			String[] strCodes = msg.getIdParam();
			String id = strCodes[0], param = strCodes[1];
			if (id.equals(Message.OK)) {
				if (!param.equals(Message.WALLET)) return this.printError(ILL_RESPONSE);
				List<String> l = msg.getArguments();
				String output = this.formatWallet(l.subList(1, l.size()), btc);
				return this.printOK(l.get(0) + "\n" + output);
			} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
			else return this.printError(ILL_RESPONSE);
		} catch (MessageException mex) { mex.printStackTrace(err); return false; }				
	}

	private boolean setFollowers(ConcurrentMap<String, List<String>> fwers) {
		if (fwers == null) return false;
		synchronized (this.followers) {
			Set<String> keys = fwers.keySet();
			if (this.followers.keySet().size() > 0) return false; //Prevent multiple settings
			else for (String key : keys) {
				if (key == null) return false;
				this.followers.put( key, fwers.get(key) );
			}
		}
		return true;
	}

	private void clearFollowers() { synchronized (this.followers) { this.followers.clear(); } }

	boolean addFollower(String username, List<String> tags) {
		Common.notNull(username, tags);
		synchronized (this.followers) {
			if (!this.followers.containsKey(username)) {
				this.followers.put(username, tags);
				return true;
			} else return false;
		}
	}

	boolean removeFollower(String username, List<String> tags) {
		Common.notNull(username, tags);
		synchronized (this.followers) {
			if (this.followers.containsKey(username)) {
				this.followers.remove(username);
				return true;
			} else return false;
		}
	}

	/**
	 * Builds a WinsomeClient based on configuration map.
	 * @param configMap Configuration map (e.g. as returned by ConfigParser::parseFile).
	 * @throws FileNotFoundException If any of the files specified for input/output/error is not found.
	 * @throws RemoteException Thrown by initialization of this.reg / this.svHandler.
	 * @throws NotBoundException Thrown by this.reg::lookup.
	 * @throws IOException If an IO error occurs.
	 */
	public WinsomeClient(Map<String, String> configMap) throws FileNotFoundException, RemoteException,
		NotBoundException, IOException {
		String cval;
		if (configMap != null) {
			cval = configMap.get("reghost");
			if (cval != null) this.regHost = new String(cval);
			cval = configMap.get("server");
			if (cval != null) this.serverHost = new String(cval);
			cval = configMap.get("tcpport");
			if (cval != null) {
				try {this.tcpPort = Integer.parseInt(cval);}
				catch (NumberFormatException ex)
				{throw new IllegalArgumentException(Common.excStr(cval + "is not a correct integer!"));}
			}
			cval = configMap.get("regport");
			if (cval != null) {
				try {this.regPort = Integer.parseInt(cval);}
				catch (NumberFormatException ex)
				{throw new IllegalArgumentException(Common.excStr(cval + "is not a correct integer!"));}
			}
			cval = configMap.get("timeout");
			if (cval != null) {
				try {this.soTimeout = Integer.parseInt(cval);}
				catch (NumberFormatException ex)
				{throw new IllegalArgumentException(Common.excStr(cval + "is not a correct integer!"));}
			}
			cval = configMap.get("input");
			if (cval != null) this.in = new FileInputStream(cval);
			cval = configMap.get("output");
			if (cval != null) this.out = new PrintStream(cval);
			cval = configMap.get("error");
			if (cval != null) this.err = new PrintStream(cval);
		}
		this.parser = new CommandParser(in);
		this.clHandler = new ClientInterfaceImpl(this);
		
		Common.debugln(this);
		
		this.tcpSocket = new Socket(this.serverHost, this.tcpPort);
		this.tcpSocket.setSoTimeout(this.soTimeout);
		this.tcpIn = this.tcpSocket.getInputStream();
		this.tcpOut = this.tcpSocket.getOutputStream();
		
		this.reg = LocateRegistry.getRegistry(regHost, regPort);
		this.svHandler = (ServerInterface) this.reg.lookup(ServerInterface.REGSERVNAME);
		Common.debugln(this);
		this.out.println(INTRO);
	}
	
	public WinsomeClient() throws FileNotFoundException, RemoteException, NotBoundException, IOException { this(null); }
	
	public final String getUser() { return new String(this.username); }

	public final String getHost() { return this.serverHost; }

	public final InputStream getIn() { return this.in; }

	public final PrintStream getOut() {return this.out; }

	public final PrintStream getErr() { return this.err; }

	/**
	 * Mainloop of the client: waits for a command while parser InputStream is open and it has
	 * not received and correctly executed a quit/exit request, then executes it. 
	 * @return 0 if all operations executed successfully, 1 on
	 * @throws Exception
	 */
	public int mainloop() throws Exception {
		int retCode = 0;
		Command cmd;
		while ((this.getState() != State.EXIT) && (this.parser.hasNextCmd())) {
			cmd = this.parser.nextCmd();
			if (cmd == null) this.printError("unrecognized command");
			try { this.dispatch(cmd); }
			catch (IOException e) { e.printStackTrace(this.err); retCode = 1; break; }
		}
		this.close();
		return retCode;
	}
	
	public boolean register(String username, String password, List<String> tags) {
		Common.notNull(username, password, tags);
		return this.svHandler.register(username, password, tags);
	}
	
	public boolean login(String username, String password) throws IOException {
		Common.notNull(username, password);
		try {
			Message msg = new Message(Message.LOGIN, null, Arrays.asList(username, password) );
			if ( !msg.sendToStream(tcpOut) ) return this.printError(CLOSED);
			
			msg = Message.recvFromStream(tcpIn);
			if (msg == null) return this.printError(CLOSED);
			
			String id, param;
			id = msg.getIdStr(); param = msg.getParamStr();
			
			if (id.equals(Message.OK) && param.equals(Message.INFO)) {
				List<String> l = msg.getArguments();
				String mcastAddr = l.get(1);
				int mcastPort, mcastMsgLen;
				try {
					mcastPort = Integer.parseInt(l.get(2));
					mcastMsgLen = Integer.parseInt(l.get(3));
				} catch (NumberFormatException nfe)
					{ return this.printfError("<%s, %s> are not valid integers!%n", l.get(2), l.get(3)); }
				this.mcastThread = new WalletNotifier(this, mcastPort, mcastAddr, mcastMsgLen);
				this.mcastThread.setDaemon(true);
				this.mcastThread.start();
				if ( !this.setFollowers( SerializationUtils.deserializeMap( l.subList(4, l.size()) ) ) )
					{ return this.printError("when retrieving current followers"); }
				if ( !this.svHandler.followersRegister(this.clHandler))
					{ return this.printError("when registering for followers updates"); }
				this.setState(State.COMM);
				return this.printOK(l.get(0));
				
			} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
			
			else return this.printError(ILL_RESPONSE);
			
		} catch (MessageException | IllegalArgumentException ex) { ex.printStackTrace(err); return false; }
	}
	
	public boolean logout(String username) throws IOException {
		Common.notNull(username);
		try {
			Message msg = new Message(Message.LOGOUT, null, Arrays.asList(username));
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			
			if ((msg = Message.recvFromStream(tcpIn)) == null) return this.printError(CLOSED);
			
			String id = msg.getIdStr(), param = msg.getParamStr();
			if (id.equals(Message.OK)) {
				if (!param.equals(Message.EMPTY)) return this.printError(ILL_RESPONSE);
				/* Closing multicast handling thread (does NOT join the other thread!) */
				if (this.mcastThread != null) this.mcastThread.close();
				/* Deregistering for followers updates */
				if ( !this.svHandler.followersUnregister(clHandler) )
					{ return this.printError("when unregistering for followers updates"); }
				/* Clearing followers list */
				this.clearFollowers();
				/* Printing result */
				return this.printOK(msg.getArguments().get(0));
			} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
			
			else return this.printError(ILL_RESPONSE);
		} catch (MessageException ex) { ex.printStackTrace(err); return false; }
	}
	
	public boolean listUsers() throws IOException {
		try {
			Message msg = new Message(Message.LIST, Message.USERS, null);
			if ( !(msg.sendToStream(tcpOut)) ) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = Message.getIdParam(msg.getIdCode(), msg.getParamCode());
				String id = strCodes[0], param = strCodes[1];
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.LIST)) return this.printError(ILL_RESPONSE);
					List<String> l = msg.getArguments();
					ConcurrentMap<String, List<String>> map = SerializationUtils.deserializeMap(l.subList(1, l.size()));
					if (map == null) return this.printError("when retrieving users list");
					String output = this.formatUserList(map);
					if (output == null) return this.printError("when formatting users list output");
					return this.printOK( l.get(0) + "\n" + output);
				} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { mex.printStackTrace(err); return false; }
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
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.LIST)) return this.printError(ILL_RESPONSE);
					List<String> l = msg.getArguments();
					ConcurrentMap<String, List<String>> map = SerializationUtils.deserializeMap(l.subList(1, l.size()));
					if (map == null) return this.printError("when retrieving following users list");
					String output = this.formatUserList(map);
					if (output == null) return this.printError("when formatting following users list output");
					return this.printOK( l.get(0) + "\n" + output);
				} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { mex.printStackTrace(err); return false; }
	}
	
	public boolean followUser(String idUser) throws IOException {
		Common.notNull(idUser);
		return this.simpleRequest(Message.FOLLOW, null, Arrays.asList(idUser));
	}
	
	public boolean unfollowUser(String idUser) throws IOException {
		Common.notNull(idUser);
		return this.simpleRequest(Message.UNFOLLOW, null, Arrays.asList(idUser));
	}
	
	public boolean viewBlog() throws IOException { 
		try {
			Message msg = new Message(Message.BLOG, null, null);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.LIST)) return this.printError(ILL_RESPONSE);
					List<String> l = msg.getArguments();
					List<List<String>> posts = SerializationUtils.deserializePostList(l.subList(1, l.size()));
					if (posts == null) return this.printError("when getting post list");
					String output = this.formatPostList(posts);
					if (output == null) return this.printError("when formatting post list");
					return this.printOK(l.get(0) + "\n" + output);
				} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
				else return this.printError(ILL_RESPONSE);
			}
		} catch (MessageException mex) { mex.printStackTrace(err); return false; }
	}
	
	public boolean createPost(String title, String content) throws IOException {
		Common.notNull(title, content);
		title = title.substring(1, title.length()-1);
		content = content.substring(1, content.length()-1);
		return this.simpleRequest(Message.POST, null, Arrays.asList(title, content));
	}
	
	public boolean showFeed() throws IOException {
		try {
			Message msg = new Message(Message.SHOW, Message.FEED, null);
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.LIST)) return this.printError(ILL_RESPONSE);
					List<String> l = msg.getArguments();
					List<List<String>> posts = SerializationUtils.deserializePostList(l.subList(1, l.size()));
					if (posts == null) return this.printError("when getting post list");
					String output = this.formatPostList(posts);
					if (output == null) return this.printError("when formatting post list");
					return this.printOK(l.get(0) + "\n" + output);
				} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
				else return this.printError(ILL_RESPONSE);
			}			
		} catch (MessageException mex) { mex.printStackTrace(err); return false; }		
	}
	
	public boolean showPost(long idPost) throws IOException {
		Common.checkAll(idPost >= 0);
		try {
			Message msg = new Message( Message.SHOW, Message.POST, Arrays.asList(Long.toString(idPost)) );
			if (!msg.sendToStream(tcpOut)) return this.printError(CLOSED);
			else {
				msg = Message.recvFromStream(tcpIn);
				String[] strCodes = msg.getIdParam();
				String id = strCodes[0], param = strCodes[1];
				if (id.equals(Message.OK)) {
					if (!param.equals(Message.POST)) return this.printError(ILL_RESPONSE);
					List<String> l = msg.getArguments();
					String output = this.formatPost( l.subList(1, l.size()) );
					if (output == null) return this.printError("when formatting post data");
					return this.printOK(l.get(0) + "\n" + output);
				} else if (id.equals(Message.ERR)) return this.printError(msg.getArguments().get(0));
				else return this.printError(ILL_RESPONSE);
			}			
		} catch (MessageException mex) { mex.printStackTrace(err); return false; }		
	}
	
	public boolean deletePost(long idPost) throws IOException {
		Common.checkAll(idPost >= 0);
		return this.simpleRequest(Message.DELETE, null, Arrays.asList(Long.toString(idPost)));
	}
	
	public boolean rewinPost(long idPost) throws IOException {
		Common.checkAll(idPost >= 0);
		return this.simpleRequest(Message.REWIN, null, Arrays.asList(Long.toString(idPost)));
	}
	
	public boolean ratePost(long idPost, String vote) throws IOException {
		Common.checkAll(idPost >= 0, vote != null);
		return this.simpleRequest( Message.RATE, null, Arrays.asList(Long.toString(idPost), vote) );
	}
	
	public boolean addComment(long idPost, String comment) throws IOException {
		Common.checkAll(idPost >= 0, comment != null);
		comment = comment.substring(1, comment.length()-1);
		return this.simpleRequest(Message.COMMENT, null, Arrays.asList(Long.toString(idPost), comment) );
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
	
	/**
	 * Prints help guide for the specified command.
	 * @param cmd Command.
	 * @return true if cmd is a valid command, false otherwise.
	 */
	public boolean help(String cmd) {
		Common.notNull(cmd);
		String msg = Help.getCmdHelp(cmd);
		if (msg != null) { this.out.println(msg); return true; }
		else return false;
	}
	
	/**
	 * Prints help guide for all commands.
	 * @return true
	 */
	public boolean help() {
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = Help.commandsList().iterator();
		while (it.hasNext()) sb.append(Help.getCmdHelp(it.next()) + (it.hasNext() ? "\n" : ""));
		this.out.println(sb.toString());
		return true;
	}

	/**
	 * Releases all resources associated with client: closes parser, tcp connection, IO streams,
	 * wallet notifier and clears all data about current user and followers.
	 */
	public void close() throws Exception {
		if (this.parser.isClosed()) this.parser.close();
		if (!this.tcpSocket.isClosed()) this.tcpSocket.close();
		this.in.close();
		this.out.close();
		this.err.close();
		if (this.mcastThread != null) {
			this.mcastThread.close();
			this.mcastThread.join();
		}
		if (this.svHandler.isRegistered(clHandler)) this.svHandler.followersUnregister(clHandler);
		if (this.isUserSet()) this.unsetUsername();
		this.clearFollowers();
		this.out.println(EXIT);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			Field[] fields = this.getClass().getDeclaredFields();
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 )
					sb.append( (i > 0 ? ", " : "") + f.getName() + " = " + f.get(this) );
			}
		} catch (IllegalAccessException ex) { ex.printStackTrace(err); }
		sb.append("]");
		return sb.toString();
	}
}