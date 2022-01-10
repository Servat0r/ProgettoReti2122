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

/**
 * Winsome server, implemented as a singleton.
 * @author Salvatore Correnti
 */
public final class WinsomeServer implements AutoCloseable {
	/* Instancw of the server */
	private static WinsomeServer server = null;
	
	/* No illegal state occurred */
	private static final Pair<String, String> ILLSTATE_OK = new Pair<>(null, null);
	
	/* States of the server */
	private static enum State { INIT, ACTIVE, CLOSING, CLOSED };
	
	/* Workers pool constants */
	private static final int
		DFLCOREPOOLSIZE = Runtime.getRuntime().availableProcessors(),
		DFLMAXPOOLSIZE = 2 * DFLCOREPOOLSIZE,
		DFLKEEPALIVETIME = 60_000,
		DFLBUFFERCAP = 4096; //4 KB
	
	private static final TimeUnit DFLKEEPALIVEUNIT = TimeUnit.MILLISECONDS;
	
	/* Default rewards percentages */
	private static final double
		DFLREWAUTH = 70.0,
		DFLREWCURS = 30.0;
	
	public static final String
		DFLSERVERHOST = "127.0.0.1",
		DFLMCASTADDR = "239.255.32.32";
	
	public static final int
		DFLTCPPORT = 6666,
		DFLMCASTPORT = 44444,
		DFLREGPORT = 7777;
	
	private static final String
		LOGSTR = "SERVER @ %s: { %s : %s }",
		ERRLOGSTR = "SERVER @ %s : ERROR @ %s : %s",
		EMPTY = "";
	
	/* Static locks for a singleton instance */
	private static final ReentrantLock
		ILLSTATELOCK = new ReentrantLock(),
		SERVERLOCK = new ReentrantLock();
	
	/* Default Exception handler for workers. */
	static final BiConsumer<SelectionKey, Exception> DFLEXCHANDLER = (key, ex) -> {
		Class<?> exClass = ex.getClass();
		WinsomeServer server = WinsomeServer.getServer();
		boolean logst = false;
		if (server == null) return;
		else if ( exClass.equals(IllegalArgumentException.class) ) { }
		else if (exClass.equals(IllegalStateException.class)) {
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			server.signalIllegalState(ex);
			logst = true;
		} else {
			server.closeConnection(key);
			if (exClass.equals(NullPointerException.class)) logst = true;
		}
		if (logst) server.logger().logStackTrace(ex);
		else server.logger().log("Exception caught: %s: %s", exClass.getSimpleName(), ex.getMessage());
	};
	
	public static final Type TYPE = new TypeToken<WinsomeServer>() {}.getType();
	
	/* Default jsons filenames */
	public static final String
		DFLSERVERJSON = "server.json",
		DFLUSERJSON = "users.json",
		DFLPOSTJSON = "posts.json",
		DFLWALLETJSON = "wallets.json";
	
	/* Default name for the reward manager s*/
	public static final String REWMANAGERNAME = "RewardManager";
	
	/* Connection state */
	private transient State state;
	
	/* I/O & logging */
	private transient Logger logger;
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

	/* Map from tags to users that have that tags */
	private ConcurrentMap<String, NavigableSet<String>> tagsMap;
	/* TCP connection data */
	private String serverHost = null;
	private int tcpPort = 0;
	private transient InetSocketAddress tcpSockAddr = null;
	private transient ServerSocketChannel tcpListener = null;
	private int tcpTimeout = 0;
	/* Local port of the multicast socket */
	private int udpPort = 0;
	
	private transient Selector selector = null;
	
	/* Workers (elaborazione richieste -> prevedere un task per ognuna) */
	private int corePoolSize, maxPoolSize, keepAliveTime;
	private transient ThreadFactoryImpl workersFactory;

	private transient ExecutorService workers;
	/* MessageBuffer capacity */
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
	
	/* "Database" */
	private transient Table<String, User> users;
	private transient Table<Long, Post> posts;
	private transient Table<String, Wallet> wallets;
	
	/* RMI */
	private transient Registry rmiReg;
	private int regPort;
	private transient ServerRMIImpl svHandler;
	/* Id generator for the posts (restored after loading) */
	private IDGen postGen;
	
	/** Maintains an indication of whether or not the system had come to an illegal state */
	@NotNull
	private Pair<String, String> illegalState;
	
	/**
	 * Initializes a table by reading the given file and casting to the given type.
	 * @param <T> Type of keys.
	 * @param <V> Type of values.
	 * @param filename Filename of the serialized data.
	 * @param type Type of the resulting table.
	 * @return A Table object deserialized on success, null on failure. NOTE: The elements
	 *  of the table are NOT deserialized (i.e. their transient fields must be initialized).
	 * @throws IOException On I/O errors.
	 * @throws DeserializationException On table deserialization failure.
	 */
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
	
	/**
	 * Initializes JSON filenames for initializing transient fields.
	 * @param serverJson JSON file for server.
	 * @param userJson JSON file for users table.
	 * @param postJson JSON file for posts table.
	 * @param walletJson JSON file for wallets table.
	 * @param users Users table.
	 * @param posts Posts table.
	 * @param wallets Wallets table.
	 * @throws IOException On I/O errors.
	 * @throws AlreadyBoundException By {@link Registry#bind(String, Remote)}.
	 * @throws DeserializationException On failure when initializing transient fields of tables data.
	 */
	private void transientsInit(String serverJson, String userJson, String postJson, String walletJson, Table<String, User> users,
		Table<Long, Post> posts, Table<String, Wallet> wallets)
			throws IOException, AlreadyBoundException, DeserializationException {		
		this.serverJson = serverJson;
		this.userJson = userJson;
		this.postJson = postJson;
		this.walletJson = walletJson;
		this.transientsInit(users, posts, wallets);
	}
	
	/**
	 * Initializes server transient fields.
	 * @param users Users table.
	 * @param posts Posts table.
	 * @param wallets Wallets table.
	 * @throws IOException On I/O errors.
	 * @throws AlreadyBoundException By {@link Registry#bind(String, Remote)}.
	 * @throws DeserializationException On failure when initializing transient fields of tables data.
	 */
	private void transientsInit(Table<String, User> users, Table<Long, Post> posts, Table<String, Wallet> wallets)
			throws IOException, AlreadyBoundException, DeserializationException {
		if (postGen == null) this.postGen = new IDGen(1);
		Post.setGen(postGen);
		Map<Long, Double> iterationMap = new HashMap<>();
		this.bitcoinService = new BitcoinService();
		this.users = ( users != null ? users : new Table<String, User>() );
		this.posts = (posts != null ? posts : new Table<Long, Post>() );
		this.wallets = (wallets != null ? wallets : new Table<String, Wallet>() );
		for (Wallet w : this.wallets.getAll()) { w.deserialize(); }
		for (Post p : this.posts.getAll()) {
			p.deserialize();
			iterationMap.putIfAbsent(p.key(), p.getIteration());
		}
		for (User u : this.users.getAll()) { u.deserialize(this.users, this.posts, this.wallets); }
		PrintStream logStream = (logName != EMPTY ? new PrintStream(logName) : System.out);
		this.logger = new Logger(LOGSTR, ERRLOGSTR, logStream);
		logger.log(Common.jsonString(iterationMap));
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
			WinsomeServer.DFLKEEPALIVEUNIT,
			new LinkedBlockingQueue<Runnable>(),
			this.workersFactory,
			new ThreadPoolExecutor.AbortPolicy()
		);
		this.actReg = new ActionRegistry( new Pair<>(rewPeriod, rewUnit) );
		this.rewManager = new RewardManager(
			this,
			this.mcastAddr,
			this.udpPort,
			this.mcastPort,
			this.wallets,
			this.actReg,
			this.rwAuthPerc,
			this.rwCurPerc,
			iterationMap
		);
		this.rewManager.setName(REWMANAGERNAME);
		this.rewManager.setDaemon(true);
		this.svHandler = new ServerRMIImpl(this);
		this.rmiReg = LocateRegistry.createRegistry(regPort);
		this.rmiReg.bind(ServerRMI.REGSERVNAME, this.svHandler);
		
		this.state = State.INIT;
	}
	
	/**
	 * Initializes non-transient fields of the server: this constructor is called by {@link #createServer(Map)}
	 *  when there is no JSON data from which to initialize server.
	 * @param configMap Configuration map.
	 * @param users Users table.
	 * @param posts Posts table.
	 * @param wallets Wallets table.
	 * @throws IOException On I/O errors.
	 * @throws AlreadyBoundException By {@link #transientsInit(Table, Table, Table)}.
	 * @throws DeserializationException By {@link #transientsInit(Table, Table, Table)}.
	 */
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
		
		serverHost = ConfigUtils.setValueOrDefault(configMap, "server", newStr, DFLSERVERHOST);
		tcpPort = ConfigUtils.setValueOrDefault(configMap, "tcpport", newInt, DFLTCPPORT);
		udpPort = ConfigUtils.setValueOrDefault(configMap, "udpport", newInt, 0);
		tcpTimeout = ConfigUtils.setValueOrDefault(configMap, "tcptimeout", newInt, 0);
		
		tmp = ConfigUtils.setValueOrDefault(configMap, "corepoolsize", newInt, DFLCOREPOOLSIZE);
		corePoolSize = (tmp >= 0 ? tmp : DFLCOREPOOLSIZE);
		
		tmp = ConfigUtils.setValueOrDefault(configMap, "maxpoolsize", newInt, DFLMAXPOOLSIZE);
		maxPoolSize = (tmp >= 0 ? tmp : DFLMAXPOOLSIZE);
		
		tmp = ConfigUtils.setValueOrDefault(configMap, "keepalivetime", newInt, DFLKEEPALIVETIME);
		keepAliveTime = (tmp >= 0 ? tmp : DFLKEEPALIVETIME);
		
		tmp = ConfigUtils.setValueOrDefault(configMap, "buffercap", newInt, DFLBUFFERCAP);
		bufferCap = (tmp >= 0 ? tmp : DFLBUFFERCAP);
		
		regPort = ConfigUtils.setValueOrDefault(configMap, "regport", newInt, DFLREGPORT);
		mcastPort = ConfigUtils.setValueOrDefault(configMap, "mcastport", newInt, DFLMCASTPORT);
		mcastAddr = ConfigUtils.setValueOrDefault(configMap, "multicast", newStr, DFLMCASTADDR);
		rewPeriod = ConfigUtils.setValueOrDefault(configMap, "rwperiod", newLong, 1L);
		rwAuthPerc = ConfigUtils.setValueOrDefault(configMap, "rwauthperc", newDouble, DFLREWAUTH);
		rwCurPerc = ConfigUtils.setValueOrDefault(configMap, "rwcurperc", newDouble, DFLREWCURS);
		rewUnit = ConfigUtils.setValueOrDefault(configMap, "rwperiodunit", newTimeUnit, TimeUnit.SECONDS);
		
		tagsMap = new ConcurrentHashMap<>();
		illegalState = WinsomeServer.ILLSTATE_OK;
		this.transientsInit(users, posts, wallets);
	}
	
	/**
	 * Creates the single-instance server.
	 * @param configMap Configuration map.
	 * @return true on success, false on error.
	 * @throws Exception
	 */
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
			
			
			Table<String, Wallet> wallets = initTable(walletJson, ServerUtils.WALLETSTYPE);
			Table<Long, Post> posts = initTable(postJson, ServerUtils.POSTSTYPE);
			Table<String, User> users = initTable(userJson, ServerUtils.USERSTYPE);
			
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
	
	/** @return The single instance of the server if any, otherwise null. */
	public static WinsomeServer getServer() {
		try { WinsomeServer.SERVERLOCK.lock(); return server; }
		finally { WinsomeServer.SERVERLOCK.unlock(); }
	}

	/**
	 * Server mainloop.
	 * @return A pair (true, message) on success, a pair (false, message) on success. The message is to be
	 *  printed by main method when exiting.
	 * @throws IllegalStateException If signalled by another thread or for invalid flags for a SelectionKey.
	 * @throws NullPointerException If thrown by called methods.
	 * @throws IOException On I/O errors.
	 */
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
		logger.log("Server initialized, accepting for connection on port %d", tcpPort);
		int selected = 0;
		Iterator<SelectionKey> keysIter;
		SelectionKey selectKey, clientKey;
		SocketChannel client;
		Pair<String, String> illegalState;
		while (state == State.ACTIVE) {
			
			try { selected = selector.select(); }
			catch (ClosedSelectorException cse) { break; }
			finally {
				illegalState = this.getIllegalState();
				if ( !illegalState.equals(ILLSTATE_OK) ) {
					String message = String.format("%s : %s", illegalState.getKey(), illegalState.getValue());
					throw new IllegalStateException(message);
				}
			}
			if (state == State.CLOSING) break;
			else if (selected > 0) {
				logger.log("Selected %d key(s)", selected);
				keysIter = selector.selectedKeys().iterator();
				while (keysIter.hasNext()) {
					
					selectKey = keysIter.next();
					keysIter.remove();
					
					if (selectKey.isAcceptable()) {
						client = ((ServerSocketChannel)selectKey.channel()).accept();
						Socket sock = client.socket();
						logger.log("Accepted connection from %s:%d on port %d",
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
						logger.log("%s: it is (%s, %s - %s)", msgstr, msg.getIdStr(), msg.getParamStr(), msg.getArguments().toString());
						boolean sent = true;
						try { sent = msg.sendToChannel(client, buf); }
						catch (IOException ioe) { sent = false; }
						String idCode = msg.getIdStr();
						if (!sent || idCode.equals(Message.QUIT) || idCode.equals(Message.EXIT))
							{ this.closeConnection(selectKey); }
						else selectKey.interestOps(SelectionKey.OP_READ);
					} else throw new IllegalStateException("Unknown key state");
				}
			}
		}
		return new Pair<>(true, "Successful execution");
	}
	
	Long checkIdPost(String str) {
		try { return Long.parseLong(str); } catch (NumberFormatException ex) { return null; }
	}
		
	/**
	 * Signals the occurring of an Exception that brings the system to an illegal state
	 *  in a thread different from the one executing mainloop() by writing Exception name
	 *   and Exception message in {@link #illegalState}.
	 * @param ex The Exception for signalling data.
	 */
	void signalIllegalState(Exception ex) {
		try {
			ILLSTATELOCK.lock();
			String exName = ex.getClass().getSimpleName(), msg = ex.getMessage();
			illegalState.setKey(exName);
			illegalState.setValue(msg);
			selector.wakeup();
		} finally { ILLSTATELOCK.unlock(); }
	}
	
	/**
	 * Get the current value of {@link #illegalState}.
	 * @return
	 */
	Pair<String, String> getIllegalState() {
		try { ILLSTATELOCK.lock(); return illegalState; }
		finally { ILLSTATELOCK.unlock(); }
	}
	
	/**
	 * Closes a TCP connection with a client after a successful quit request
	 *  or an IO error when communicating with it.
	 * @param key SelectionKey corresponding to the client.
	 */
	void closeConnection(SelectionKey key) {
		SocketChannel client = (SocketChannel) key.channel();
		try {
			key.cancel(); 
			loggedMap.remove(client);
			unlogged.remove(client);
			client.close();
		} catch (IOException ioe) { throw new IllegalStateException(); }
	}
	
	/**
	 * @param client SocketChannel.
	 * @return The name of the user that is communicating with given channel
	 *  if anyone is logged in, otherwise null.
	 */
	String translateChannel(SocketChannel client) {
		Common.notNull(client);
		User u = loggedMap.get(client);
		if (u != null) return new String(u.key());
		else return null;
	}
	
	/**
	 * Formats a channel into a standard string printable for logging.
	 * @param client SocketChannel.
	 * @return Formatted string containing remote IP and port.
	 */
	@NotNull
	String formatChannel(SocketChannel client) {
		Common.notNull(client);
		Socket sock = client.socket();
		return String.format("(%s:%d)", sock.getInetAddress(), sock.getPort());
	}
	
	/**
	 * Registers a new user to the network.
	 * @param username Username.
	 * @param password Password.
	 * @param tags List of tags.
	 * @return A pair (true, message) on success, a pair (false, message) on error.
	 */
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
		logger.log("Registrato nuovo utente: '%s' con tags: '%s'", username, tags.toString());
		return new Pair<>(true, "Registrazione avvenuta con successo!");
	}
	
	/**
	 * Login.
	 * @param skey Selection key.
	 * @param args List of args as {username, password}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message login(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		String username = args.get(0), password = args.get(1);
		User user = users.get(username);
		if (user == null) return Message.newError(ServerUtils.U_NEXISTING, username);
		else if (user.checkPassword(password)) {
			synchronized (loggedMap) {
				if (loggedMap.containsValue(user)) return Message.newError(ServerUtils.U_ALREADY_LOGGED, username);
				else if (loggedMap.containsKey(client)) return Message.newError(ServerUtils.U_ANOTHER_LOGGED);
				else synchronized (unlogged) {
					if (unlogged.contains(client)) {
						unlogged.remove(client);
						loggedMap.put(client, user);
						logger.log("Channel '%s' reassigned to new user '%s'", this.formatChannel(client), username);
					} else throw new IllegalArgumentException(Common.excStr("Unknown channel: '%s'", client.toString()));
				}
			}
			List<String> followers = Serialization.serializeMap(user.getFollowers());
			return Message.newInfo(mcastAddr, mcastPort, rewManager.mcastMsgLen(), followers, ServerUtils.LOGIN_OK, username);
		} else {
			logger.log("Password checking failed for user '%s'", username);
			return Message.newError(ServerUtils.LOGIN_PWINV);
		}
	}
	
	/**
	 * Logout.
	 * @param skey Selection key.
	 * @param args List of args as {username}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message logout(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		String username = args.get(0);
		User user = users.get(username);
		if (user == null) return Message.newError(ServerUtils.U_NEXISTING, username);
		else if (!user.key().equals(username)) return Message.newError(ServerUtils.PERMDEN + ": " + ServerUtils.U_USERSNEQ, user.key(), username);
		else {
			synchronized (loggedMap) {
				if (!loggedMap.containsKey(client) || !loggedMap.containsValue(user))
					return Message.newError(ServerUtils.U_NLOGGED, username);
				else synchronized (unlogged) {
					loggedMap.remove(client);
					unlogged.add(client);
				}
			}
			return Message.newOK(ServerUtils.LOGOUT_OK, username);
		}
	}
	
	/**
	 * List of users currently following.
	 * @param skey Selection key.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message listFollowing(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		List<String> following = Serialization.serializeMap(user.getFollowing());
		return Message.newList(following, ServerUtils.OK);
	}
	
	/**
	 * List of users with a tag in common.
	 * @param skey Selection key.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message listUsers(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
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
		return Message.newList(result, ServerUtils.OK);
	}
	
	/**
	 * Follow user.
	 * @param skey Selection key.
	 * @param args List of args as {username}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message followUser(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		
		User follower = loggedMap.get(client);
		if (follower == null) return Message.newError(ServerUtils.U_NONELOGGED);
		
		User followed = users.get(args.get(0));
		if (followed == null) return Message.newError(ServerUtils.U_NEXISTING, args.get(0));
		
		int res = User.addFollower(follower, followed);
		if (res == 0) {
			try {
				if (!svHandler.addFollower(follower.key(), followed.key(), follower.tags()) ) {
					return Message.newError(ServerUtils.INTERROR);
				} else return Message.newOK(ServerUtils.OK);
				
			} catch (RemoteException rex) {
				logger.logStackTrace(rex);
				return Message.newError(ServerUtils.INTERROR);
			}
		} else if (res == 1) return Message.newOK(ServerUtils.FOLLOW_ALREADY, args.get(0));
		else return Message.newError(ServerUtils.INTERROR);
	}
	
	/**
	 * Unfollow user.
	 * @param skey Selection key.
	 * @param args List of args as {username}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message unfollowUser(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		User follower = loggedMap.get(client);
		if (follower == null) return Message.newError(ServerUtils.U_NONELOGGED);
		User followed = users.get(args.get(0));
		if (followed == null) return Message.newError(ServerUtils.U_NEXISTING, args.get(0));
		int res = User.removeFollower(follower, followed);
		if (res == 0) {
			try {
				if ( !svHandler.removeFollower(follower.key(), followed.key()) ) {
					return Message.newError(ServerUtils.INTERROR);
				} else return Message.newOK(ServerUtils.OK);
				
			} catch (RemoteException rex) {
				logger.logStackTrace(rex);
				return Message.newError(ServerUtils.INTERROR);
			}
		} else if (res == 1) return Message.newOK(ServerUtils.UNFOLLOW_ALREADY, args.get(0));
		else return Message.newError(ServerUtils.INTERROR);
	}
	
	/**
	 * View blog.
	 * @param skey Selection key.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message viewBlog(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		else return Message.newList(user.getBlog(), ServerUtils.OK);
	}
	
	/**
	 * Create a post.
	 * @param skey Selection key.
	 * @param args List of args as {title, content}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message createPost(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		String title = args.get(0), content = args.get(1);
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		String author = new String(user.key());
		Action a = Action.newCreatePost(author);
		this.actReg.putAction(a);
		try {
			long idPost = user.createPost(title, content);
			a.setIdPost(idPost);
			this.actReg.endAction(a);
			return Message.newOK("Post creato correttamente (id = %d)", idPost);
		} catch (DataException de) {
			this.actReg.abortAction(a);
			return Message.newError(ServerUtils.INTERROR);
		}
	}
	
	/**
	 * Show feed.
	 * @param skey Selection key.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message showFeed(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		List<String> feed = user.getFeed();
		return Message.newList(feed, Message.OK);
	}
	
	/**
	 * Show feed.
	 * @param skey Selection key.
	 * @param args List of args as {idPost}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message showPost(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(ServerUtils.POST_INVID, args.get(0));
		else idPost = id.longValue();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		List<String> posts;
		try {
			posts = user.getPost(idPost);
			Common.allAndState(posts.size() >= 4);
			String title = posts.remove(0), content = posts.remove(0);
			String likes = posts.remove(0), dislikes = posts.remove(0);
			return Message.newPost(title, content, likes, dislikes, posts, Message.OK); }
		catch (DataException de) { return Message.newError(de.getMessage()); }
	}
	
	/**
	 * Delete post.
	 * @param skey Selection key.
	 * @param args List of args as {idPost}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message deletePost(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(ServerUtils.POST_INVID, args.get(0));
		else idPost = id.longValue();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
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
			if (msg.equals(DataException.NOT_AUTHOR)) return Message.newError("%s: %s", ServerUtils.PERMDEN, ServerUtils.POST_NAUTHOR);
			else return Message.newError(ServerUtils.INTERROR);
		}
	}
	
	
	/**
	 * Rewin post.
	 * @param skey Selection key.
	 * @param args List of args as {idPost}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message rewinPost(SelectionKey skey, List<String> args) {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(ServerUtils.POST_INVID, args.get(0));
		else idPost = id.longValue();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		try {
			if (user.rewinPost(idPost)) return Message.newOK(ServerUtils.OK);
			else return Message.newError(ServerUtils.REWON_ALREADY);
		} catch (DataException de) {
			String msg = de.getMessage();
			if (msg.equals(DataException.NOT_IN_FEED)) return Message.newError(ServerUtils.POST_NINFEED, idPost);
			else if (msg.equals(DataException.SAME_AUTHOR))
				return Message.newError("%s: %s", ServerUtils.PERMDEN, ServerUtils.POST_AUTHOR);
			else return Message.newError(ServerUtils.INTERROR);
		}
	}
	
	
	/**
	 * Rate post.
	 * @param skey Selection key.
	 * @param args List of args as {idPost, vote}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message ratePost(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(ServerUtils.POST_INVID, args.get(0));
		else idPost = id.longValue();
		
		String vote = args.get(1);
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		String actor = new String(user.key());
		boolean like;
		Post p = this.posts.get(idPost);
		if (p == null) return Message.newError(ServerUtils.POST_NEXISTS, idPost);
		String author = new String(p.getAuthor());
		if ( author.equals(user.key()) ) return Message.newError("%s: %s", ServerUtils.PERMDEN, ServerUtils.POST_AUTHOR);
		
		try { like = Post.getVote(vote); }
		catch (DataException de) { return Message.newError(ServerUtils.INV_VOTE_SYNTAX); }
		Action a = Action.newRatePost(like, actor, author, idPost);		
		try {
			this.actReg.putAction(a);
			if (user.ratePost(idPost, like)) {
				this.actReg.endAction(a);
				return Message.newOK(ServerUtils.OK);
			} else { this.actReg.abortAction(a); return Message.newError(ServerUtils.VOTED_ALREADY); }
		} catch (DataException de) {
			this.actReg.abortAction(a);
			String msg = de.getMessage();
			if (msg.equals(DataException.NOT_IN_FEED)) return Message.newError(ServerUtils.POST_NINFEED, idPost);
			else return Message.newError(ServerUtils.INTERROR);
		}
	}
	
	/**
	 * Add comment.
	 * @param skey Selection key.
	 * @param args List of args as {idPost, content}.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message addComment(SelectionKey skey, List<String> args) throws InterruptedException {
		SocketChannel client = (SocketChannel)skey.channel();
		long idPost;
		Long id = this.checkIdPost(args.get(0));
		if (id == null) return Message.newError(ServerUtils.POST_INVID, args.get(0));
		else idPost = id.longValue();
		String comment = args.get(1);
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		String actor = new String(user.key());
		Post p = this.posts.get(idPost);
		if (p == null) return Message.newError(ServerUtils.POST_NEXISTS, idPost);
		String author = new String(p.getAuthor());
		Action a = Action.newAddComment(actor, author, idPost);
		try {
			this.actReg.putAction(a);
			int ncomm = user.addComment(idPost, comment);
			a.setNComments(ncomm);
			this.actReg.endAction(a);
			return Message.newOK(ServerUtils.OK);
		} catch (DataException de) {
			this.actReg.abortAction(a);
			switch (de.getMessage()) {
				case DataException.NOT_IN_FEED : { return Message.newError(ServerUtils.POST_NINFEED, idPost); }
				case DataException.SAME_AUTHOR : { return Message.newError("%s : %s", ServerUtils.PERMDEN, ServerUtils.POST_AUTHOR); }
				case DataException.UNADD_COMMENT :
				default : 
				{ return Message.newError(ServerUtils.INTERROR); }
			}
		}
	}
	
	/**
	 * Get wallet in wincoin.
	 * @param skey Selection key.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message getWallet(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		try {
			List<String> history = user.getWallet();
			double value = Double.parseDouble(history.get(0));
			return Message.newWallet(value, history.subList(1, history.size()), ServerUtils.OK);
		} finally { }
	}
	
	/**
	 * Get wallet in bitcoin and wincoin.
	 * @param skey Selection key.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message getWalletInBitcoin(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		User user = loggedMap.get(client);
		if (user == null) return Message.newError(ServerUtils.U_NONELOGGED);
		double value, btcValue;
		try {
			List<String> history = user.getWallet();
			value = Double.parseDouble(history.get(0));
			btcValue = bitcoinService.convert(value);
			return Message.newBtcWallet(btcValue, value, history.subList(1, history.size()), ServerUtils.OK);
		} catch (IOException ioe) { return Message.newError(ServerUtils.BTC_CONV); }
		finally { }
	}
		
	/**
	 * Quit request.
	 * @param skey Selection key.
	 * @return A Message object to send back to the client.
	 */
	@NotNull
	Message quitReq(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		if (loggedMap.containsKey(client)) return Message.newError(ServerUtils.PERMDEN + ": " + ServerUtils.U_STILL_LOGGED);
		else if (!unlogged.contains(client)) return Message.newError(ServerUtils.UNKNOWN_CHAN);
		else { unlogged.remove(client); return Message.newQuit(Message.OK); }
	}
	
	protected final int bufferCap() { return bufferCap; }
	
	protected final Selector selector() { return selector; }
	
	/**
	 * Update the iteration fields of the posts still alive.
	 * @param map Map idPost -> iteration num.
	 * @return true on success, false otherwise.
	 */
	protected final boolean updateIters(Map<Long, Double> map) {
		Common.notNull(map);
		Post p;
		for (long id : map.keySet()) {
			p = posts.get(id);
			if (p == null) return false;
			else p.setIteration(map.get(id));
		}
		return true;
	}
	
	protected final Logger logger() { return logger; }
	
	/**
	 * Serializes server data into 4 JSON files: one for the server, one for the users,
	 *  one for the posts and one for the wallets.
	 * @throws IllegalStateException If thrown by called methods.
	 */
	private void serialize() throws IllegalStateException {
		Gson gson = Serialization.GSON;
		logger.log("Serializing on (%s, %s, %s, %s)", userJson, postJson, walletJson, serverJson);
		
		try ( FileOutputStream w = new FileOutputStream(walletJson); ){
			String jsond = gson.toJson(wallets);
			w.write(jsond.getBytes());
		} catch (IOException exc) { logger.logStackTrace(exc); }
		
		try ( FileOutputStream p = new FileOutputStream(postJson); ){
			String jsond = gson.toJson(posts);
			p.write(jsond.getBytes());
		} catch (IOException exc) { logger.logStackTrace(exc); }
		
		try ( FileOutputStream u = new FileOutputStream(userJson); ){
			String jsond = gson.toJson(users);
			u.write(jsond.getBytes());
		} catch (IOException exc) { logger.logStackTrace(exc); }
		
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
		if (state == State.ACTIVE || state == State.CLOSING) {
			this.state = State.CLOSED;
			
			selector.close();
			logger.log("Selector closed");
			
			rewManager.interrupt();
			rewManager.join();
			logger.log("Rew manager joined");
			
			tcpListener.close();
			logger.log("TCP Listener closed");
			
			try { rmiReg.unbind(ServerRMI.REGSERVNAME); }
			catch (Exception ex) { Debug.debugExc(ex); }
			logger.log("RMI Registry closed");
			
			workers.shutdown();
			workersFactory.interruptAll();
			workersFactory.joinAll();
			logger.log("Workers pool closed");
			
			for (SocketChannel chan : loggedMap.keySet()) chan.close();
			for (SocketChannel chan : unlogged) chan.close();
			logger.log("All SocketChannels closed");
			
			this.serialize();
			logger.log("Serialization done");
			
			loggedMap.clear();
			unlogged.clear();
			logger.log("Data cleared");
			
			this.logger.close();
		}
	}
	
	@NotNull
	public String toString() { return Common.jsonString(this); }
}