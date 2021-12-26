package winsome.client;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.rmi.*;
import java.rmi.registry.*;

import winsome.client.command.*;
import winsome.common.rmi.*;
import winsome.util.Common;
import winsome.util.UserList;

public final class WinsomeClient implements AutoCloseable {
	
	private static enum State {
		INIT, /* Client initialized (set by constructor) */
		COMM, /* Client communicating (mainloop/run) (set by login) */
		EXIT, /* Client exiting (exit/quit request received by user) */
	};
	
	private static String INTRO = "This is the WinsomeClient. Type \"help\" for help on available commands or "
			+ "\"help cmd <command>\" to get help on <command>, or any command to get it executed";
	
	private static String EXIT = "Thanks for having used WinsomeClient";
	
	private CommandParser parser; //Per ricevere i prossimi comandi
	
	private String serverHost = null; //Ricavati dal file di configurazione nel main del client
	private int tcpPort = 0;
	private Socket tcpSocket = null;
	
	private int udpPort = 0;
	private String mcastAddr = null;
	private String mcastPort = null;
	private MulticastSocket mcastSocket = null;
	
	private String regHost = null;
	private int regPort = 0;
	private Registry reg = null;
	
	private int soTimeout = 0;
	
	private State state = State.INIT;
	
	private InputStream in = System.in;
	private PrintStream out = System.out;
	private PrintStream err = System.err;
	
	private ClientInterface clHandler;
	private ServerInterface svHandler;
	
	private String username = null;
	private ConcurrentMap<String, List<String>> followers; //FIXME Eventualmente caricare da file JSON
	
	private static String printTags(List<String> tags) {
		Common.notNull(tags);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tags.size(); i++) sb.append( (i > 0 ? ", " : "") + tags.get(i) );
		return sb.toString();
	}
	
	private String printUserList(ConcurrentMap<String, List<String>> users) {
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
	
	private State getState() {
		State s;
		synchronized (this.state) { s = this.state; }
		return s;
	}

	private void setState(State state) { synchronized (this.state) { this.state = state; } }
	
	private boolean checkUsername(String str) {
		return (username == null || username.equals(str));
	}
	
	private void setUsername(boolean b, String str) {
		if (b && (username == null)) this.username = new String(str);
	}
	
	private void unsetUsername(boolean b) { if (b) this.username = null; }
	
	private boolean isUserSet() { return (username != null); }
	
	private boolean dispatch(Command cmd) {
		Common.notNull(cmd);
		String id = cmd.getId();
		String param = cmd.getParam();
		List<String> args = cmd.getArgs();
		boolean result;
		if ( id.equals("register") ) {
			String u = args.get(0);
			if ( !checkUsername(u) ) return false;
			result = this.register(args.get(0), args.get(1), args.subList(2, args.size()) );
			this.setUsername(result, u);
		} else if ( id.equals("login") ) {
			String u = args.get(0);
			if ( !checkUsername(u) ) return false;
			result = this.login( args.get(0), args.get(1) );
			this.setUsername(result, u);
		} else if ( id.equals("logout") ) {
			if (username == null) return false;
			result = this.logout(username);
			this.unsetUsername(result);
			//FIXME Per tutti i metodi successivi, distinguere il dispatching fra metodi con username != null e == null!
		} else if ( id.equals("list") ) {
			if (param.equals("users")) result = this.listUsers();
			else if (param.equals("followers")) result = this.listFollowers();
			else if (param.equals("following")) result = this.listFollowing();
			else throw new IllegalArgumentException();
		} else if ( id.equals("follow") ) result = this.followUser(args.get(0));
		else if ( id.equals("unfollow") ) result = this.unfollowUser(args.get(0));
		else if ( id.equals("blog") ) result = this.viewBlog();
		else if ( id.equals("post") ) {
			String title = args.get(0);
			String content = args.get(1);
			/* Removing starting and ending (") */
			args.set(0, title.substring(1, title.length()-1));
			args.set(1, content.substring(1, content.length()-1));
			result = this.createPost(args.get(0), args.get(1));
		} else if ( id.equals("show") ) {
			if (param.equals("feed")) result = this.showFeed();
			else if (param.equals("post")) result = this.showPost(Long.parseLong(args.get(0)));
			else throw new IllegalArgumentException();
		} else if ( id.equals("delete") ) result = this.deletePost(Long.parseLong(args.get(0)));
		else if ( id.equals("rewin") ) result = this.rewinPost(Long.parseLong(args.get(0)));
		else if ( id.equals("rate") ) result = this.ratePost(Long.parseLong(args.get(0)), args.get(1));
		else if ( id.equals("comment") ) {
			String comment = args.get(1);
			args.set(1, comment.substring(1, comment.length()-1));
			result = this.addComment(Long.parseLong(args.get(0)), args.get(1));
		} else if ( id.equals("wallet") ) {
			if (param == null) result = this.getWallet();
			else if (param.equals("btc")) result = this.getWalletInBitcoin();
			else throw new IllegalArgumentException();
		} else if ( id.equals("help") ) {
			if (param == null) result = this.help();
			else if (param.equals("cmd")) result = this.help(args.get(0));
			else throw new IllegalArgumentException();
		} else if ( id.equals("quit") || id.equals("exit")) {
			this.setState(State.EXIT);
			result = true;
		} else throw new IllegalArgumentException(Common.excStr("Invalid command"));
		return result;
	}
	
	public WinsomeClient(Map<String, String> configMap) throws FileNotFoundException, RemoteException, NotBoundException {
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
			cval = configMap.get("udpport");
			if (cval != null) {
				try {this.udpPort = Integer.parseInt(cval);}
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
		//FIXME Eventualmente caricare da file JSON la lista dei followers
		this.parser = new CommandParser(in);
		this.clHandler = new ClientInterfaceImpl(this);
		//this.reg = LocateRegistry.getRegistry(regHost, regPort);
		//this.svHandler = (ServerInterface) this.reg.lookup(ServerInterface.REGSERVNAME);
		//TODO Eliminare nella versione definitiva
		Common.debugln(this);
		this.followers = UserList.deserializeMap(Arrays.asList(
			"wario #3 tag1 tag2 tag3",
			"mario #2 tag2 tag3",
			"peachable #4 tag2 tag3 tag4 tag5"
		));
		//TODO Instaura connessione TCP
		Common.debugln(this);
		this.out.println(INTRO);
	}
	
	public WinsomeClient() throws FileNotFoundException, RemoteException, NotBoundException { this(null); }
	
	public boolean setFollowers(ConcurrentMap<String, List<String>> followers) {
		Common.notNull(followers);
		synchronized (this.followers) {
			Set<String> keys = followers.keySet();
			if (keys.size() > 0) return false; //Prevent multiple settings
			else for (String key : keys) {
				if (key == null) return false;
				this.followers.put(key, followers.get(key));
			}
		}
		return true;
	}
	
	public String getUser() { return new String(this.username); }
	
	public String getHost() { return this.serverHost; } //TODO Eliminare!

	public final InputStream getIn() { return this.in; }
	public final PrintStream getOut() {return this.out; }
	public final PrintStream getErr() { return this.err; }

	public int mainloop() {
		int retCode = 0;
		Command cmd;
		while ((this.getState() != State.EXIT) && (this.parser.hasNextCmd())) {
			cmd = this.parser.nextCmd();
			if (cmd == null) { this.err.println("Error: unrecognized command"); retCode = 1; break; } //TODO Ricontrollare!
			Common.debugln(cmd);
			this.dispatch(cmd);
		}
		return retCode; //TODO Codice da usare in System.exit() alla fine
	}

	public boolean register(String username, String password, List<String> tags) {
		Common.notNull(username, password, tags);
		return this.svHandler.register(username, password, tags);
	}
	
	public boolean login(String username, String password) {
		//TODO Ricevi messaggio con gli indirizzi di multicast
		
		return true;
	}
	
	public boolean logout(String username) { return true; }
	
	public boolean listUsers() { return true; }
	
	public boolean listFollowers() {
		String output;
		synchronized (this.followers) { output = this.printUserList(this.followers); }
		if (output != null) { this.out.println(output); return true; }
		else return false;
	}
	
	boolean addFollower(String username, List<String> tags) {
		Common.notNull(username, tags);
		synchronized (this.followers) {
			if (!this.followers.containsKey(username)) {
				this.followers.put(username, tags);
				return true;
			} else return false;
		}
	} //TODO Usato dalla callback del server
	
	boolean removeFollower(String username, List<String> tags) {
		Common.notNull(username, tags);
		synchronized (this.followers) {
			if (this.followers.containsKey(username)) {
				this.followers.remove(username);
				return true;
			} else return false;
		}
	} //TODO Usato dalla callback del server
	
	public boolean listFollowing() { return true; }
	
	public boolean followUser(String idUser) { return true; }
	
	public boolean unfollowUser(String idUser) { return true; }
	
	public boolean viewBlog() { return true; }
	
	public boolean createPost(String title, String content) { return true; }
	
	public boolean showFeed() { return true; }
	
	public boolean showPost(long idPost) { return true; }
	
	public boolean deletePost(long idPost) { return true; }
	
	public boolean rewinPost(long idPost) { return true; }
	
	public boolean ratePost(long idPost, String vote) { return true; }
	
	public boolean addComment(long idPost, String comment) { return true; }
	
	public boolean getWallet() { return true; }
	
	public boolean getWalletInBitcoin() { return true; }
	
	public boolean help(String cmd) {
		Common.notNull(cmd);
		String msg = Help.getCmdHelp(cmd);
		if (msg != null) {
			this.out.println(msg);
			return true;
		} else return false;
	}
	
	public boolean help() {
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = Help.commandsList().iterator();
		while (it.hasNext()) sb.append(Help.getCmdHelp(it.next()) + (it.hasNext() ? "\n" : ""));
		this.out.println(sb.toString());
		return true;
	}
	
	public void close() throws Exception {
		this.parser.close();
		this.out.println(EXIT);
	}
}