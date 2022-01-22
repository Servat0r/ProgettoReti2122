package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

import com.google.gson.*;
import com.google.gson.stream.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.rmi.AlreadyBoundException;
import java.rmi.*;
import java.rmi.registry.*;

import winsome.annotations.NotNull;
import winsome.common.config.ConfigUtils;
import winsome.common.msg.*;
import winsome.common.rmi.*;
import winsome.server.data.*;
import winsome.util.*;

/**
 * Winsome server, implemented as a singleton.
 * @author Salvatore Correnti
 */
public final class WinsomeServer implements AutoCloseable {
	/* Instance of the server */
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
	
	public static final Gson gson() { return Serialization.GSON; }
	
	public static final Exception jsonSerializer(JsonWriter writer, WinsomeServer server) {
		Common.notNull(writer, server);
		BiFunction<JsonWriter, NavigableSet<String>, Exception> encoder = 
		(wr, set) -> {
			try {
				wr.beginArray();
				for (String str : set) wr.value(str);
				wr.endArray();
				return null;
			} catch (Exception ex) { return ex; }
		};		
		
		try {			
			writer.beginObject();
			Serialization.writeMap(writer, false, server.tagsMap, "tagsMap", s -> s, encoder);
			Serialization.writeFields(gson(), writer, false, server, "postGen", "illegalState");
			writer.endObject();
			return null;
		} catch (Exception ex) { return ex; }
	}
	
	public static final WinsomeServer jsonDeserializer(JsonReader reader) {
		Common.notNull(reader);
		WinsomeServer server;
		if (WinsomeServer.server == null) server = new WinsomeServer();
		else server = WinsomeServer.server;
		Function<JsonReader, NavigableSet<String>> decoder = 
		rd -> {
			try {
				NavigableSet<String> set = new TreeSet<>();
				rd.beginArray();
				while (rd.hasNext()) set.add(rd.nextString());
				rd.endArray();
				return set;
			} catch (Exception ex) { ex.printStackTrace(); return null; }
		};
		
		try {
			reader.beginObject();
			Serialization.readMap(reader, server.tagsMap, "tagsMap", s->s, decoder);
			Serialization.readFields(gson(), reader, server, "postGen", "illegalState");
			reader.endObject();
			Post.setGen(server.postGen);
			WinsomeServer.server = server;
			return server;
		} catch (Exception ex) { ex.printStackTrace(); return null; }
	}
	
	public static final String
		DFLSERVERHOST = "127.0.0.1",
		DFLMCASTADDR = "239.255.32.32";
	
	public static final int
		DFLTCPPORT = 6666,
		DFLMCASTPORT = 44444,
		DFLREGPORT = 7777;
	
	private static final String
		LOGHEAD = "SERVER",
		LOGSTART = "{ ",
		LOGEND = " }",
		LOGSEPAR = ": ",		
		ERRSEPAR1 = ": ERROR ",
		EMPTY = "";
	
	/* Static locks for a singleton instance */
	private static final ReentrantLock
		ILLSTATELOCK = new ReentrantLock(),
		SERVERLOCK = new ReentrantLock();
		
	
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
	private AtomicLong postGen;
	
	/** Maintains an indication of whether or not the system had come to an illegal state */
	@NotNull
	private Pair<String, String> illegalState;
		
	@NotNull
	private static <T extends Comparable<T>, V extends Indexable<T>> Table<T, V> initTable(String jsonFile,
		Function<JsonReader, V> deserializer){
		Table<T, V> table;
		try { table = Table.fromJson(jsonFile, deserializer); }
		catch (IOException | DeserializationException ex) { table = new Table<>(); }
		return table;
	}

	/**
	 * Initializes non-transient fields of the server: this constructor is called by {@link #createServer(Map)}
	 *  when there is no JSON data from which to initialize server.
	 * @param users Users table.
	 * @param posts Posts table.
	 * @param wallets Wallets table.
	 * @throws IOException On I/O errors.
	 * @throws AlreadyBoundException By {@link #transientsInit(Table, Table, Table)}.
	 * @throws DeserializationException By {@link #transientsInit(Table, Table, Table)}.
	 */
	private WinsomeServer() {
		tagsMap = new ConcurrentHashMap<>();
		postGen = new AtomicLong(1);
		illegalState = WinsomeServer.ILLSTATE_OK;
	}

	private void configFieldsInit(Map<String, String> configMap) {
		Common.notNull(configMap);		
		Integer tmp;			
		
		Function<String, String> newStr = ConfigUtils.newStr;
		Function<String, Integer> newInt = ConfigUtils.newInt;
		Function<String, Long> newLong = ConfigUtils.newLong;
		Function<String, Double> newDouble = ConfigUtils.newDouble;
		Function<String, TimeUnit> newTimeUnit = ConfigUtils.newTimeUnit;
		
		logName = ConfigUtils.setValueOrDefault(configMap, "logger", newStr, (logName == null ? EMPTY : logName));
				
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
	private void transientsInit(Map<String, String> configMap) throws IOException, AlreadyBoundException,
			DeserializationException {
		this.configFieldsInit(configMap);
		this.bitcoinService = new BitcoinService();
		PrintStream logStream = (logName != EMPTY ? new PrintStream(logName) : System.out);
		this.logger = new Logger(LOGHEAD, LOGHEAD, LOGSTART, LOGEND, LOGSEPAR, LOGSEPAR,
			ERRSEPAR1, LOGSEPAR, logStream);
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
		this.rewManager = new RewardManager(
			this,
			this.mcastAddr,
			this.udpPort,
			this.mcastPort,
			this.wallets,
			this.posts,
			new Pair<>(rewPeriod, rewUnit),
			this.rwAuthPerc,
			this.rwCurPerc
		);
		this.rewManager.setName(REWMANAGERNAME);
		this.rewManager.setDaemon(true);
		this.svHandler = new ServerRMIImpl(this);
		this.rmiReg = LocateRegistry.createRegistry(regPort);
		this.rmiReg.bind(ServerRMI.REGSERVNAME, this.svHandler);
		
		this.state = State.INIT;
	}
	
	
	/**
	 * Creates the single-instance server.
	 * @param configMap Configuration map.
	 * @return true on success, false on error.
	 * @throws Exception
	 */
	public static WinsomeServer createServer(Map<String, String> configMap) throws Exception {
		try {
			WinsomeServer.SERVERLOCK.lock();
			if (WinsomeServer.server != null) return WinsomeServer.server;
			else if (configMap == null) configMap = new HashMap<>();
			WinsomeServer.server = new WinsomeServer();
			WinsomeServer server = WinsomeServer.server;
			
			server.serverJson = ConfigUtils.setValueOrDefault(configMap, "serverjson", ConfigUtils.newStr, DFLSERVERJSON);
			server.userJson = ConfigUtils.setValueOrDefault(configMap, "userjson", ConfigUtils.newStr, DFLUSERJSON);
			server.postJson = ConfigUtils.setValueOrDefault(configMap, "postjson", ConfigUtils.newStr, DFLPOSTJSON);
			server.walletJson = ConfigUtils.setValueOrDefault(configMap, "walletjson", ConfigUtils.newStr, DFLWALLETJSON);
			
			server.wallets = initTable(server.walletJson, Wallet::jsonDeserializer);
			server.posts = initTable(server.postJson, Post::jsonDeserializer);
			server.users = initTable(server.userJson, User::jsonDeserializer);
			
			User.userDeserialization(server.users, server.posts, server.wallets);
			
			try ( JsonReader serverReader = Serialization.fileReader(server.serverJson) ){
				if (serverReader != null) {
					server = WinsomeServer.jsonDeserializer(serverReader);
					if (server != null) WinsomeServer.server = server;
					else throw new IllegalStateException("Server creation");
				}
			}
			
			if (!server.illegalState.equals(ILLSTATE_OK)) {
				String message = String.format("%s : %s", server.illegalState.getKey(), server.illegalState.getValue());
				throw new IllegalStateException(message);
			}
			
			try { server.transientsInit(configMap); WinsomeServer.server = server; }
			catch (Exception exc) { WinsomeServer.server = null; server.close(); throw exc; }
			
			Post.setGen(server.postGen);
			return WinsomeServer.server;
		} finally { WinsomeServer.SERVERLOCK.unlock(); }
	}
	
	/** @return The single instance of the server if any, otherwise null. */
	public static WinsomeServer getServer() {
		try {
			WinsomeServer.SERVERLOCK.lock();
			return WinsomeServer.server;
		} finally { WinsomeServer.SERVERLOCK.unlock(); }
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
						logger.log(msgstr);
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
		} catch (IOException ioe) {
			logger.logException(ioe);
			throw new IllegalStateException();
		}
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
		if (user == null) return new Pair<>(false, String.format(ServerUtils.REG_EXISTING, username));
		if (!users.add(user)) return new Pair<>(false, ServerUtils.INTERROR);
		NavigableSet<String> set; 
		for (String t : tags) {
			String tag = new String(t);
			set = tagsMap.get(tag);
			if (set == null) { set = new TreeSet<>(); tagsMap.put(tag, set); }
			set.add(new String(username));
		}
		logger.log("Registrato nuovo utente: '%s' con tags: '%s'", username, tags.toString());
		return new Pair<>(true, String.format(ServerUtils.REG_OK, username));
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
			try {
				List<String> followers = Serialization.serializeMap(user.getFollowers());
				return Message.newInfo(mcastAddr, mcastPort, rewManager.mcastMsgLen(), followers, ServerUtils.LOGIN_OK, username);
			} catch (Exception ex) {
				logger.logException(ex);
				return Message.newError(ServerUtils.INTERROR);
			}
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
		try {
			List<String> following = Serialization.serializeMap(user.getFollowing());
			return Message.newUserList(following, ServerUtils.OK);
		} catch (Exception ex) {
			logger.logException(ex);
			return Message.newError(ServerUtils.INTERROR);
		}
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
		try {
			result = Serialization.serializeMap(map);
			return Message.newUserList(result, ServerUtils.OK);
		} catch (Exception ex) {
			logger.logException(ex);
			return Message.newError(ServerUtils.INTERROR);
		}
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
				logger.logException(rex);
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
				logger.logException(rex);
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
		else return Message.newPostList(user.getBlog(), ServerUtils.OK);
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
		try {
			long idPost = user.createPost(title, content);
			return Message.newOK("Post creato correttamente (id = %d)", idPost);
		} catch (DataException de) {
			logger.logException(de);
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
		return Message.newPostList(feed, Message.OK);
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
		catch (DataException de) {
			logger.logException(de);
			return Message.newError(de.getMessage());
		}
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
		try {
			user.deletePost(idPost);
			return Message.newOK(Message.OK);
		} catch (DataException de) {
			logger.logException(de);
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
		boolean like;
		Post p = this.posts.get(idPost);
		if (p == null) return Message.newError(ServerUtils.POST_NEXISTS, idPost);
		String author = new String(p.getAuthor());
		if ( author.equals(user.key()) ) return Message.newError("%s: %s", ServerUtils.PERMDEN, ServerUtils.POST_AUTHOR);
		
		try { like = Post.getRate(vote); }
		catch (DataException de) { return Message.newError(ServerUtils.INV_VOTE_SYNTAX); }
		try {
			if (user.ratePost(idPost, like)) return Message.newOK(ServerUtils.OK);
			else return Message.newError(ServerUtils.VOTED_ALREADY);
		} catch (DataException de) {
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
		Post p = this.posts.get(idPost);
		if (p == null) return Message.newError(ServerUtils.POST_NEXISTS, idPost);
		try {
			user.addComment(idPost, comment);
			return Message.newOK(ServerUtils.OK);
		} catch (DataException de) {
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
	void quitReq(SelectionKey skey) {
		SocketChannel client = (SocketChannel)skey.channel();
		loggedMap.remove(client);
		unlogged.remove(client);
		this.closeConnection(skey);
	}
	
	protected final int bufferCap() { return bufferCap; }
	
	protected final Selector selector() { return selector; }
		
	protected final Logger logger() { return logger; }
	
	/**
	 * Serializes server data into 4 JSON files: one for the server, one for the users,
	 *  one for the posts and one for the wallets.
	 * @throws Exception If thrown by called methods.
	 */
	private void serialize() throws Exception {
		logger.log("Serializing on (%s, %s, %s, %s)", userJson, postJson, walletJson, serverJson);
		
		try { wallets.toJson(walletJson, Wallet::jsonSerializer); }
		catch (Exception ex) {
			logger.logException(ex);
			throw new IllegalStateException(Common.excStr("Unable to write to '%s'", walletJson));
		}
		
		try { posts.toJson(postJson, Post::jsonSerializer); }
		catch (Exception ex) {
			logger.logException(ex);
			throw new IllegalStateException(Common.excStr("Unable to write to '%s'", postJson));
		}
		
		try { users.toJson(userJson, User::jsonSerializer); }
		catch (Exception ex) {
			logger.logException(ex);
			throw new IllegalStateException(Common.excStr("Unable to write to '%s'", userJson));
		}
		
		try (JsonWriter serverWriter = Serialization.fileWriter(serverJson)){
			Exception ex = WinsomeServer.jsonSerializer(serverWriter, this);
			if (ex != null) {
				logger.logException(ex);
				throw new IllegalStateException(Common.excStr("Unable to write to '%s'", serverJson));
			}
		}
	}
		
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
			catch (Exception ex) { logger.logException(ex); }
			logger.log("RMI Registry closed");
			
			workers.shutdown();
			workersFactory.interruptAll();
			workersFactory.joinAll();
			logger.log("Workers pool closed");
			
			logger.log("Old actions saved");
			
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
	public String toString() { return Common.toString(this, gson(), WinsomeServer::jsonSerializer); }
}