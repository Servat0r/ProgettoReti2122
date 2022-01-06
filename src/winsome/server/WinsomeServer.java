package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.channels.*;
import java.rmi.AlreadyBoundException;
import java.rmi.*;
import java.rmi.registry.*;

import winsome.annotations.NotNull;
import winsome.common.config.ConfigUtils;
import winsome.common.msg.*;
import winsome.common.rmi.*;
import winsome.server.action.*;
import winsome.server.data.*;
import winsome.util.*;

public final class WinsomeServer implements AutoCloseable {
	
	private static WinsomeServer server = null;
	
	private static final Pair<String, String> ILLSTATE_OK = new Pair<>(null, null);
	
	private static final Type
		USERSTYPE = new TypeToken< Table<String, User> >() {}.getType(),
		POSTSTYPE = new TypeToken< Table<Long, Post> >(){}.getType(),
		WALLETSTYPE = new TypeToken < Table<String, Wallet> >(){}.getType();
	
	private static enum State { INIT, ACTIVE, CLOSED };
	
	private static final int
		COREPOOLSIZE = Runtime.getRuntime().availableProcessors(),
		MAXPOOLSIZE = 2 * COREPOOLSIZE,
		KEEPALIVETIME = 60_000,
		BUFFERCAP = 4096; //4 KB
	
	private static final TimeUnit KEEPALIVEUNIT = TimeUnit.MILLISECONDS;
	
	private static final String
		LOGSTR = "SERVER @ %s: { %s : %s }",
		ERRLOGSTR = "SERVER @ %s : ERROR @ %s : %s",
		EMPTY = "";
	
	private static final ReentrantLock
		ILLSTATELOCK = new ReentrantLock(),
		LOGLOCK = new ReentrantLock(),
		SERVERLOCK = new ReentrantLock();
	
	static final BiConsumer<SelectionKey, Exception> DFLEXCHANDLER = (key, ex) -> {
		Class<?> exClass = ex.getClass();
		WinsomeServer server = WinsomeServer.getServer();
		if (server == null) return;
		else if ( exClass.equals(IllegalArgumentException.class) ) { }
		else if (exClass.equals(IllegalStateException.class)) {
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			server.signalIllegalState(ex);
		} else { server.closeConnection(key); }
		server.logStackTrace(ex);
	};
	
	public static final Type TYPE = new TypeToken<WinsomeServer>() {}.getType();
	
	public static final String
		DFLSERVERJSON = "server.json",
		DFLUSERJSON = "users.json",
		DFLPOSTJSON = "posts.json",
		DFLWALLETJSON = "wallets.json";
	
	public static final String REWMANAGERNAME = "RewardManager";
	
	/* Connection state */
	private transient State state;
	
	/* I/O */
	private transient PrintStream logStream = System.out;
	//private transient PrintStream errStream = System.err;
	private String logName = EMPTY;
	
	/* Nomi dei file json (da leggere dal file di configurazione) */
	private transient String
		serverJson = null,
		userJson = null,
		postJson = null,
		walletJson = null;
		
	/* Gestione delle connessioni TCP con i client */
	private transient ConcurrentMap<SocketChannel, User> loggedMap; //Mappa ogni username con un channel
	private transient Set<SocketChannel> unlogged; //Canali non mappati, su cui cioè nessun utente è loggato

	private ConcurrentMap<String, NavigableSet<String>> tagsMap;
	private String serverHost = null;
	private int tcpPort = 0;
	private transient InetSocketAddress tcpSockAddr = null;
	private transient ServerSocketChannel tcpListener = null;

	private int tcpTimeout = 0;
	private int udpPort = 0;
	private transient Selector selector = null;
	
	/* Workers (elaborazione richieste -> prevedere un task per ognuna) */
	private int corePoolSize, maxPoolSize, keepAliveTime;
	private transient ThreadFactoryImpl workersFactory;

	private transient ExecutorService workers;

	private int bufferCap;
	/* Thread di calcolo ricompense ("writer" dell'actReg) + notifica client su multicast */
	private String mcastAddr;
	private int mcastPort;
	private long rewPeriod;
	private double rwAuthPerc, rwCurPerc;
	private TimeUnit rewUnit;
	private transient ActionRegistry actReg;
	private transient RewardManager rewManager;
	
	/* Conversione in bitcoin */
	private transient BitcoinService bitcoinService;
	
	/* "Database" (verrà serializzato a parte) */
	private transient Table<String, User> users;
	private transient Table<Long, Post> posts;
	private transient Table<String, Wallet> wallets;
	
	/* RMI */
	private transient Registry rmiReg;
	private int regPort;
	private transient ServerInterfaceImpl svHandler; //OK
	
	private IDGen postGen;
	
	private Pair<String, String> illegalState;
	
	private static <T extends Comparable<T>,V extends Indexable<T>> Table<T, V> initTable(String filename, Type type)
		throws IOException, DeserializationException {
		Table<T, V> table = null;
		JsonReader reader = Serialization.fileReader(filename);
		if (reader != null) {
			try { table = Serialization.GSON.fromJson(reader, type); }
			catch (JsonIOException | JsonSyntaxException ex) { ex.printStackTrace(); table = null; }
			finally { reader.close(); }
		}
		if (table != null) table.deserialize();
		return table;
	}
	
	private void transientsInit(String serverJson, String userJson, String postJson, String walletJson, Table<String, User> users,
		Table<Long, Post> posts, Table<String, Wallet> wallets)
			throws IOException, AlreadyBoundException, DeserializationException {		
		this.serverJson = serverJson;
		this.userJson = userJson;
		this.postJson = postJson;
		this.walletJson = walletJson;
		this.transientsInit(users, posts, wallets);
	}
	
	private void transientsInit(Table<String, User> users, Table<Long, Post> posts, Table<String, Wallet> wallets)
			throws IOException, AlreadyBoundException, DeserializationException {
		if (postGen == null) this.postGen = new IDGen(1);
		Post.setGen(postGen);
		this.bitcoinService = new BitcoinService();
		this.users = ( users != null ? users : new Table<String, User>() );
		this.posts = (posts != null ? posts : new Table<Long, Post>() );
		this.wallets = (wallets != null ? wallets : new Table<String, Wallet>() );
		for (Wallet w : this.wallets.getAll()) { w.deserialize(); }
		for (Post p : this.posts.getAll()) { p.deserialize(); }
		for (User u : this.users.getAll()) { u.deserialize(this.users, this.posts, this.wallets); }
		this.logStream = (logName != EMPTY ? new PrintStream(logName) : System.out);
		this.tcpSockAddr = new InetSocketAddress(InetAddress.getByName(serverHost), tcpPort);
		this.loggedMap = new ConcurrentHashMap<>();
		this.unlogged = new HashSet<>();
		this.tcpListener = ServerSocketChannel.open();
		this.tcpListener.bind(this.tcpSockAddr);
		this.tcpListener.socket().setSoTimeout(tcpTimeout);
		this.tcpListener.configureBlocking(false);
		this.selector = Selector.open();
		this.workersFactory = new ThreadFactoryImpl();
		this.workers = new ThreadPoolExecutor(
			this.corePoolSize,
			this.maxPoolSize,
			this.keepAliveTime,
			WinsomeServer.KEEPALIVEUNIT,
			new LinkedBlockingQueue<Runnable>(),
			this.workersFactory,
			new ThreadPoolExecutor.AbortPolicy()
		);
		this.actReg = new ActionRegistry( new Pair<>(rewPeriod, rewUnit) );
		this.rewManager = new RewardManager(
			this,
			this.mcastAddr,
			this.mcastPort,
			this.wallets,
			this.actReg,
			this.rwAuthPerc,
			this.rwCurPerc
		);
		this.rewManager.setName(REWMANAGERNAME);
		this.rewManager.setDaemon(true);
		this.svHandler = new ServerInterfaceImpl(this);
		this.rmiReg = LocateRegistry.createRegistry(regPort);
		this.rmiReg.bind(ServerInterface.REGSERVNAME, this.svHandler);
		
		this.state = State.INIT;
	}
	
	private WinsomeServer(Map<String, String> configMap, Table<String, User> users, Table<Long, Post> posts,
		Table<String, Wallet> wallets) throws IOException, AlreadyBoundException, DeserializationException {
		Common.notNull(configMap);
		
		Function<String, String> newStr = ConfigUtils.newStr;
		Function<String, Integer> newInt = ConfigUtils.newInt;
		Function<String, Long> newLong = ConfigUtils.newLong;
		Function<String, Double> newDouble = ConfigUtils.newDouble;
		Function<String, TimeUnit> newTimeUnit = ConfigUtils.newTimeUnit;
		
		Integer tmp;			
		logName = ConfigUtils.setValueOrDefault(configMap, "logger", newStr, EMPTY);
					
		serverJson = ConfigUtils.setValueOrDefault(configMap, "serverjson", newStr, DFLSERVERJSON);
		userJson = ConfigUtils.setValueOrDefault(configMap, "userjson", newStr, DFLUSERJSON);
		postJson = ConfigUtils.setValueOrDefault(configMap, "postjson", newStr, DFLPOSTJSON);
		walletJson = ConfigUtils.setValueOrDefault(configMap, "walletjson", newStr, DFLWALLETJSON);
		
		serverHost = ConfigUtils.setValue(configMap, "server", newStr);
		tcpPort = ConfigUtils.setValue(configMap, "tcpport", newInt);
		udpPort = ConfigUtils.setValue(configMap, "udpport", newInt);
		tcpTimeout = ConfigUtils.setValue(configMap, "tcptimeout", newInt);
		
		tmp = ConfigUtils.setValueOrDefault(configMap, "corepoolsize", newInt, COREPOOLSIZE);
		corePoolSize = (tmp >= 0 ? tmp : COREPOOLSIZE);
		
		tmp = ConfigUtils.setValueOrDefault(configMap, "maxpoolsize", newInt, MAXPOOLSIZE);
		maxPoolSize = (tmp >= 0 ? tmp : MAXPOOLSIZE);
		
		tmp = ConfigUtils.setValueOrDefault(configMap, "keepalivetime", newInt, KEEPALIVETIME);
		keepAliveTime = (tmp >= 0 ? tmp : KEEPALIVETIME);
					
		tmp = ConfigUtils.setValueOrDefault(configMap, "buffercap", newInt, BUFFERCAP);
		bufferCap = (tmp >= 0 ? tmp : BUFFERCAP);
		
		regPort = ConfigUtils.setValue(configMap, "regport", newInt);
		mcastPort = ConfigUtils.setValue(configMap, "mcastport", newInt);
		mcastAddr = ConfigUtils.setValue(configMap, "multicast", newStr);
		rewPeriod = ConfigUtils.setValue(configMap, "rwperiod", newLong);
		rwAuthPerc = ConfigUtils.setValue(configMap, "rwauthperc", newDouble);
		rwCurPerc = ConfigUtils.setValue(configMap, "rwcurperc", newDouble);
		rewUnit = ConfigUtils.setValue(configMap, "rwperiodunit", newTimeUnit);
		
		tagsMap = new ConcurrentHashMap<>();
		illegalState = WinsomeServer.ILLSTATE_OK;
		this.transientsInit(users, posts, wallets);
	}

	public static boolean createServer(Map<String, String> configMap) throws Exception {
		Common.notNull(configMap);
		try {
			WinsomeServer.SERVERLOCK.lock();
			if (WinsomeServer.server != null) return false;
			String
				serverJson = ConfigUtils.setValueOrDefault(configMap, "serverjson", ConfigUtils.newStr, DFLSERVERJSON),
				userJson = ConfigUtils.setValueOrDefault(configMap, "userjson", ConfigUtils.newStr, DFLUSERJSON),
				postJson = ConfigUtils.setValueOrDefault(configMap, "postjson", ConfigUtils.newStr, DFLPOSTJSON),
				walletJson = ConfigUtils.setValueOrDefault(configMap, "walletjson", ConfigUtils.newStr, DFLWALLETJSON);
			
			
			Table<String, Wallet> wallets = initTable(walletJson, WALLETSTYPE);
			Table<Long, Post> posts = initTable(postJson, POSTSTYPE);
			Table<String, User> users = initTable(userJson, USERSTYPE);
			
			JsonReader serverReader = Serialization.fileReader(serverJson);
			WinsomeServer server = null;
			if (serverReader != null) {
				try { server = Serialization.GSON.fromJson(serverReader, WinsomeServer.TYPE); }
				catch (JsonIOException | JsonSyntaxException ex) { Debug.println(ex); server = null; }
				finally { serverReader.close(); }
			}
			if (server != null) {
				if (!server.illegalState.equals(ILLSTATE_OK)) {
					String message = String.format("%s : %s", server.illegalState.getKey(), server.illegalState.getValue());
					throw new IllegalStateException(message);
				} else server.transientsInit(serverJson, userJson, postJson, walletJson, users, posts, wallets);
			}
			else server = new WinsomeServer(configMap, users, posts, wallets);
			WinsomeServer.server = server;
			return true;
		} finally { WinsomeServer.SERVERLOCK.unlock(); }
	}

	public static WinsomeServer getServer() {
		try { WinsomeServer.SERVERLOCK.lock(); return server; }
		finally { WinsomeServer.SERVERLOCK.unlock(); }
	}

	@NotNull
	public Pair<Boolean, String> mainloop() throws IllegalStateException, NullPointerException, IOException {
		synchronized (this) {
			if (state == State.ACTIVE) return new Pair<>(false, "Server already active");
			else if (state == State.CLOSED) return new Pair<>(false, "Server is closing");
			else {
				state = State.ACTIVE;
				rewManager.start();
				tcpListener.register(selector, SelectionKey.OP_ACCEPT);
			}
		}
		log("Server initialized, accepting for connection on port %d", tcpPort);
		int selected = 0;
		Iterator<SelectionKey> keysIter;
		SelectionKey selectKey, clientKey;
		SocketChannel client;
		Pair<String, String> illegalState;
		while (state == State.ACTIVE) {
			
			try { selected = selector.select(); }
			catch (ClosedSelectorException cse) { break; }
			illegalState = this.getIllegalState();
			if ( !illegalState.equals(ILLSTATE_OK) ) {
				String message = String.format("%s : %s", illegalState.getKey(), illegalState.getValue());
				throw new IllegalStateException(message);
			} else if (selected > 0) {
				log("Selected %d key(s)", selected);
				keysIter = selector.selectedKeys().iterator();
				while (keysIter.hasNext()) {
					
					selectKey = keysIter.next();
					keysIter.remove();
					
					if (selectKey.isAcceptable()) {
						client = ((ServerSocketChannel)selectKey.channel()).accept();
						Socket sock = client.socket();
						log("Accepted connection from %s:%d on port %d",
							sock.getInetAddress().toString(), sock.getPort(), sock.getLocalPort());
						client.configureBlocking(false);
						clientKey = client.register(selector, SelectionKey.OP_READ);
						clientKey.attach(null);
						unlogged.add(client); //Currently unmapped to any user
					} else if (selectKey.isReadable()) {
						selectKey.interestOps(0);
						workers.execute(new Worker(selectKey));
					} else if (selectKey.isWritable()) {
						client = (SocketChannel)selectKey.channel();
						String u = this.translateChannel(client);
						Message msg = (Message)selectKey.attachment();
						MessageBuffer buf = new MessageBuffer(bufferCap);
						String msgstr = "Sending response to " + (u != null ? u : "anonymous user");
						log("%s: it is (%s, %s - %s)", msgstr, msg.getIdStr(), msg.getParamStr(), msg.getArguments().toString());
						boolean sent = true;
						try { sent = msg.sendToChannel(client, buf); }
						catch (IOException ioe) { sent = false; }
						String idCode = msg.getIdStr();
						if (!sent || idCode.equals(Message.QUIT) || idCode.equals(Message.EXIT))
							{ this.closeConnection(selectKey); }
						else selectKey.interestOps(SelectionKey.OP_READ);
					} else throw new IllegalStateException();
				}
			}
		}
		return new Pair<>(true, "Successful execution");
	}

	Long checkIdPost(String str) {
		try { return Long.parseLong(str); } catch (NumberFormatException ex) { return null; }
	}
		
	void signalIllegalState(Exception ex) { //String exName, String msg) {
		try {
			ILLSTATELOCK.lock();
			String exName = ex.getClass().getSimpleName(), msg = ex.getMessage();
			illegalState.setKey(exName);
			illegalState.setValue(msg);
			selector.wakeup();
		} finally { ILLSTATELOCK.unlock(); }
	}
	
	Pair<String, String> getIllegalState() {
		try { ILLSTATELOCK.lock(); return illegalState; }
		finally { ILLSTATELOCK.unlock(); }
	}
	
	void closeConnection(SelectionKey key) {
		SocketChannel client = (SocketChannel) key.channel();
		try {
			key.cancel(); 
			loggedMap.remove(client);
			unlogged.remove(client);
			client.close();
		} catch (IOException ioe) { throw new IllegalStateException(); }
	}
	
	String translateChannel(SocketChannel client) {
		Common.notNull(client);
		User u = loggedMap.get(client);
		if (u != null) return new String(u.key());
		else return null;
	}
	
	String formatChannel(SocketChannel client) {
		Common.notNull(client);
		Socket sock = client.socket();
		return String.format("(%s:%d)", sock.getInetAddress(), sock.getPort());
	}

	void log(String format, Object ...objs) {
		String timestamp = new Date().toString().substring(0, 19);
		StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
		String fname = "Thread[" + Thread.currentThread().getName() + "]: " + elem.getClassName() + "." + elem.getMethodName();
		String msg = String.format(format, objs);
		try {
			LOGLOCK.lock();
			logStream.println(String.format(LOGSTR, timestamp, fname, msg));
		} finally { LOGLOCK.unlock(); }
	}
	
	void logStackTrace(Exception ex) {
		String timestamp = new Date().toString().substring(0, 19);
		Thread t = Thread.currentThread();
		StackTraceElement elem = t.getStackTrace()[2];
		String fname = "Thread[" + t.getName() + "]: " + elem.getClassName() + "." + elem.getMethodName();
		try {
			LOGLOCK.lock();
			logStream.println(String.format(ERRLOGSTR, timestamp, fname, "Exception caught: {"));
			ex.printStackTrace(logStream);
			logStream.println("}");
		} finally { LOGLOCK.unlock(); }
	}
	
	@NotNull
	Pair<Boolean, String> register(String username, String password, List<String> tags) {
		User user = User.newUser(username, password, users, posts, wallets, tags);
		if (user == null) return new Pair<>(false, "Registrazione fallita: utente " + username + " già presente!");
		if (!users.putIfAbsent(user)) return new Pair<>(false, "Registrazione fallita: errore interno al server");
		NavigableSet<String> set; 
		for (String t : tags) {
			String tag = new String(t);
			set = tagsMap.get(tag);
			if (set == null) { set = new TreeSet<>(); tagsMap.put(tag, set); }
			set.add(new String(username));
		}
		log("Registrato nuovo utente: '%s' con tags: '%s'", username, tags.toString());
		return new Pair<>(true, "Registrazione avvenuta con successo!");
	}
	
	@NotNull
	Message login(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		String username = args.get(0), password = args.get(1);
		User user = users.get(username);
		if (user == null) return Message.newError(Answ.NEXISTING, username);
		else if (user.checkPassword(password)) {
			synchronized (loggedMap) {
				if (loggedMap.containsValue(user)) return Message.newError(Answ.ALREADY, username);
				else if (loggedMap.containsKey(client)) return Message.newError(Answ.ANOTHER);
				else synchronized (unlogged) {
					if (unlogged.contains(client)) {
						unlogged.remove(client);
						loggedMap.put(client, user);
						log("Channel '%s' reassigned to new user '%s'", this.formatChannel(client), username);
					} else throw new IllegalArgumentException(Common.excStr("Unknown channel: '%s'", client.toString()));
				}
			}
			List<String> followers = Serialization.serializeMap(user.getFollowers());
			return Message.newInfo(mcastAddr, mcastPort, rewManager.mcastMsgLen(), followers, Answ.LOGIN_OK, username);
		} else {
			log("Password checking failed for user '%s'", username);
			return Message.newError(Answ.LOGIN_PWINV);
		}
	}
	
	@NotNull
	Message logout(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		String username = args.get(0);
		User user = users.get(username);
		if (user == null) return Message.newError(Answ.NEXISTING, username);
		else if (!user.key().equals(username)) return Message.newError(Answ.PERMDEN + ": " + Answ.USERSNEQ, user.key(), username);
		else {
			synchronized (loggedMap) {
				if (!loggedMap.containsKey(client) || !loggedMap.containsValue(user))
					return Message.newError(Answ.NLOGGED, username);
				else synchronized (unlogged) {
					loggedMap.remove(client);
					unlogged.add(client);
				}
			}
			return Message.newOK(Answ.LOGOUT_OK, username);
		}
	}
	
	@NotNull
	Message listFollowing(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		List<String> following = Serialization.serializeMap(user.getFollowing());
		return Message.newList(following, Answ.OK);
	}
	
	@NotNull
	Message listUsers(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		List<String> result;
		ConcurrentMap<String, List<String>> map = new ConcurrentHashMap<>();
		NavigableSet<String> currSet;
		NavigableSet<String> set = new TreeSet<>();
		for (String tag : user.tags()) {
			currSet = tagsMap.get(tag);
			if (currSet != null) set.addAll(currSet);
		}
		NavigableSet<User> users = this.users.get(set);
		for (User u : users) map.put(u.key(), u.tags());
		result = Serialization.serializeMap(map);
		return Message.newList(result, Answ.OK);
	}
	
	@NotNull
	Message followUser(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		
		User follower = loggedMap.get(client);
		if (follower == null) return Message.newError(Answ.NONELOGGED);
		
		User followed = users.get(args.get(0));
		if (followed == null) return Message.newError(Answ.NEXISTING, args.get(0));
		
		int res = User.addFollower(follower, followed);
		if (res == 0) {
			try {
				
				if ( svHandler.isRegistered(followed.key()) && 
					!svHandler.addFollower(follower.key(), followed.key(), follower.tags()) ) {
					
					return Message.newError(Answ.INT_ERROR);
				} else return Message.newOK(Answ.OK);
				
			} catch (RemoteException rex) {
				this.logStackTrace(rex);
				return Message.newError(Answ.INT_ERROR);
			}
		} else if (res == 1) return Message.newOK(Answ.FOLLOW_ALREADY, args.get(0));
		else return Message.newError(Answ.INT_ERROR);
	}
	
	@NotNull
	Message unfollowUser(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		User follower = loggedMap.get(client);
		if (follower == null) return Message.newError(Answ.NONELOGGED);
		User followed = users.get(args.get(0));
		if (followed == null) return Message.newError(Answ.NEXISTING, args.get(0));
		int res = User.removeFollower(follower, followed);
		if (res == 0) {
			try {
				if ( !svHandler.removeFollower(follower.key(), followed.key()) ) {
					return Message.newError(Answ.INT_ERROR);
				} else return Message.newOK(Answ.OK);
			} catch (RemoteException rex) {
				this.logStackTrace(rex);
				return Message.newError(Answ.INT_ERROR);
			}
		} else if (res == 1) return Message.newOK(Answ.UNFOLLOW_ALREADY, args.get(0));
		else return Message.newError(Answ.INT_ERROR);
	}
	
	
	Message viewBlog(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		else return Message.newList(user.getBlog(), Answ.OK);
	}
	
	Message createPost(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		String title = args.get(0), content = args.get(1);
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		String author = new String(user.key());
		Action a = Action.newCreatePost(author);
		this.actReg.putAction(a);
		long idPost = user.createPost(title, content);
		a.setIdPost(idPost);
		this.actReg.endAction(a);
		return Message.newOK("Post creato correttamente (id = %d)", idPost);
	}
	
	Message showFeed(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		List<String> feed = user.getFeed();
		return Message.newList(feed, Message.OK);
	}
	
	Message showPost(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(Answ.INV_IDPOST, args.get(0));
		else idPost = id.longValue();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		List<String> posts;
		try { posts = user.getPost(idPost); return Message.newPost(posts, Message.OK); }
		catch (DataException de) { return Message.newError(de.getMessage()); }
	}
	
	Message deletePost(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(Answ.INV_IDPOST, args.get(0));
		else idPost = id.longValue();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		String actor = new String(user.key());
		Action a = Action.newDeletePost(actor, idPost);
		try {
			this.actReg.putAction(a);
			user.deletePost(idPost);
			this.actReg.endAction(a);
			return Message.newOK(Message.OK);
		} catch (DataException de) {
			this.actReg.abortAction(a);
			String msg = de.getMessage();
			if (msg.equals(DataException.NOT_AUTHOR)) return Message.newError("%s: %s", Answ.PERMDEN, Answ.POST_NAUTHOR);
			else return Message.newError(Answ.INT_ERROR);
		}
	}
	
	
	Message rewinPost(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(Answ.INV_IDPOST, args.get(0));
		else idPost = id.longValue();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		try {
			if (user.rewinPost(idPost)) return Message.newOK(Answ.OK);
			else return Message.newError(Answ.REWON_ALREADY);
		} catch (DataException de) {
			String msg = de.getMessage();
			if (msg.equals(DataException.NOT_IN_FEED)) return Message.newError(Answ.POST_NINFEED, idPost);
			else return Message.newError(Answ.INT_ERROR);
		}
	}
	
	
	Message ratePost(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(Answ.INV_IDPOST, args.get(0));
		else idPost = id.longValue();
		String vote = args.get(1);
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		String actor = new String(user.key());
		boolean like;
		Post p = this.posts.get(idPost);
		if (p == null) return Message.newError(Answ.POST_NEXISTS, idPost);
		String author = new String(p.getAuthor());
		try { like = Post.getVote(vote); }
		catch (DataException de) { return Message.newError(Answ.INV_VOTE_SYNTAX); }
		Action a = Action.newRatePost(like, actor, author, idPost);		
		try {
			this.actReg.putAction(a);
			if (user.ratePost(idPost, like)) {
				this.actReg.endAction(a);
				return Message.newOK(Answ.OK);
			} else { this.actReg.abortAction(a); return Message.newError(Answ.VOTED_ALREADY); }
		} catch (DataException de) {
			this.actReg.abortAction(a);
			String msg = de.getMessage();
			if (msg.equals(DataException.NOT_IN_FEED)) return Message.newError(Answ.POST_NINFEED, idPost);
			else return Message.newError(Answ.INT_ERROR);
		}
	}
	
	Message addComment(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(Answ.INV_IDPOST, args.get(0));
		else idPost = id.longValue();
		String comment = args.get(1);
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		String actor = new String(user.key());
		Post p = this.posts.get(idPost);
		if (p == null) return Message.newError(Answ.POST_NEXISTS, idPost);
		String author = new String(p.getAuthor());
		Action a = Action.newAddComment(actor, author, idPost);
		try {
			this.actReg.putAction(a);
			int ncomm = user.addComment(idPost, comment);
			a.setNComments(ncomm);
			this.actReg.endAction(a);
			return Message.newOK(Answ.OK);
		} catch (DataException de) {
			this.actReg.abortAction(a);
			switch (de.getMessage()) {
				case DataException.NOT_IN_FEED : { return Message.newError(Answ.POST_NINFEED, idPost); }
				case DataException.SAME_AUTHOR : { return Message.newError("%s : %s", Answ.PERMDEN, Answ.POST_AUTHOR); }
				case DataException.UNADD_COMMENT :
				default : 
				{ return Message.newError(Answ.INT_ERROR); }
			}
		}
	}
	
	Message getWallet(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		try {
			List<String> history = user.getWallet();
			double value = Double.parseDouble(history.get(0));
			return Message.newWallet(value, history.subList(1, history.size()), Answ.OK);
		} finally { }
	}
	
	Message getWalletInBitcoin(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(Answ.NONELOGGED);
		double value, btcValue;
		try {
			List<String> history = user.getWallet();
			value = Double.parseDouble(history.get(0));
			btcValue = bitcoinService.convert(value);
			return Message.newBtcWallet(btcValue, value, history.subList(1, history.size()), Answ.OK);
		} catch (IOException ioe) { return Message.newError(Answ.BTC_CONV); }
		finally { }
	}
		
	Message quitReq(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		if (loggedMap.containsKey(client)) return Message.newError(Answ.PERMDEN + ": " + Answ.STILL_LOGGED);
		else if (!unlogged.contains(client)) return Message.newError(Answ.UNKNOWN_CHAN);
		else { unlogged.remove(client); return Message.newQuit(Message.OK); }
	}
	
	public final int bufferCap() { return bufferCap; }
	
	public final Selector selector() { return selector; }
	
	
		
	private void serialize() throws IllegalStateException {
		Gson gson = Serialization.GSON;
		log("Serializing on (%s, %s, %s, %s)", userJson, postJson, walletJson, serverJson);
		
		try ( FileOutputStream w = new FileOutputStream(walletJson); ){
			String jsond = gson.toJson(wallets);
			w.write(jsond.getBytes());
		} catch (IOException exc) { this.logStackTrace(exc); }
		
		try ( FileOutputStream p = new FileOutputStream(postJson); ){
			String jsond = gson.toJson(posts);
			p.write(jsond.getBytes());
		} catch (IOException exc) { this.logStackTrace(exc); }
		
		try ( FileOutputStream u = new FileOutputStream(userJson); ){
			String jsond = gson.toJson(users);
			u.write(jsond.getBytes());
		} catch (IOException exc) { this.logStackTrace(exc); }
		
		try ( FileOutputStream s = new FileOutputStream(serverJson); ){
			String jsond = gson.toJson(this, WinsomeServer.class);
			s.write(jsond.getBytes());
		} catch (IOException exc) { Debug.debugExc(exc); exc.printStackTrace(); }
/*		try (JsonWriter walletsWriter = Serialization.fileWriter(walletJson)){
			String jsond = gson.toJson(wallets);
			Debug.println(jsond);
			//gson.toJson(wallets, Wallet.TYPE, walletsWriter);
		} catch (IOException ioe) { ioe.printStackTrace(err); throw new IllegalStateException(); }
		
		try (JsonWriter postsWriter = Serialization.fileWriter(postJson)){
			gson.toJson(posts, WinsomeServer.POSTSTYPE, postsWriter);
		} catch (IOException ioe) { ioe.printStackTrace(err); throw new IllegalStateException(); }
		
		try (JsonWriter usersWriter = Serialization.fileWriter(userJson)){
			gson.toJson(users, WinsomeServer.USERSTYPE, usersWriter);
		} catch (IOException ioe) { ioe.printStackTrace(err); throw new IllegalStateException(); }
		
		try (JsonWriter serverWriter = Serialization.fileWriter(serverJson)){
			gson.toJson(this, WinsomeServer.TYPE, serverWriter);
		} catch (IOException ioe) { ioe.printStackTrace(err); throw new IllegalStateException(); }
*/	}
		
	
	public synchronized void close() throws Exception {
		if (state == State.ACTIVE) {
			this.state = State.CLOSED;
			
			selector.close();
			this.log("Selector closed");
			
			rewManager.interrupt();
			rewManager.join();
			this.log("Rew manager joined");
			
			tcpListener.close();
			this.log("TCP Listener closed");
			
			try { rmiReg.unbind(ServerInterface.REGSERVNAME); }
			catch (Exception ex) { Debug.debugExc(ex); }
			this.log("RMI Registry closed");
			
			workers.shutdown();
			workersFactory.interruptAll();
			workersFactory.joinAll();
			this.log("Workers pool closed");
			
			for (SocketChannel chan : loggedMap.keySet()) chan.close();
			for (SocketChannel chan : unlogged) chan.close();
			this.log("All SocketChannels closed");
			
			this.serialize();
			this.log("Serialization done");
			
			loggedMap.clear();
			unlogged.clear();
			workersFactory.clearList();
			this.log("Data cleared");
			
			if (this.logStream != System.out) this.logStream.close();
		}
	}
	
	@NotNull
	public String toString() {
		String cname = this.getClass().getSimpleName();
		String jsond = Serialization.GSON.toJson(this, this.getClass());
		return String.format("%s: %s", cname, jsond);
	}
}