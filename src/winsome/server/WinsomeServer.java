package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.channels.*;
import java.rmi.*;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.*;

import winsome.common.config.ConfigUtils;
import winsome.common.rmi.*;
import winsome.server.action.*;
import winsome.server.data.*;
import winsome.util.*;

public final class WinsomeServer implements AutoCloseable {
	
	/* URL per la conversione in bitcoin (prevedere un task per questo) */
	private static final String BTC_CONV_URL = 
			"https://www.random.org/integers/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new";
	
	private static final Type
		USERSTYPE = new TypeToken< Table<String, User> >() {}.getType(),
		POSTSTYPE = new TypeToken< Table<String, User> >(){}.getType();
		
	private static final int
		COREPOOLSIZE = Runtime.getRuntime().availableProcessors(),
		MAXPOOLSIZE = 2 * COREPOOLSIZE,
		KEEPALIVETIME = 60;
	
	private static final TimeUnit KEEPALIVEUNIT = TimeUnit.SECONDS;
	
	public static final Type TYPE = new TypeToken<WinsomeServer>() {}.getType();
	
	public static final String
		DFLSERVERJSON = "server.json",
		DFLUSERJSON = "users.json",
		DFLPOSTJSON = "posts.json",
		DFLWALLETJSON = "wallets.json";
	
	/* I/O */
	private transient InputStream in = System.in;
	private transient PrintStream out = System.out;
	private transient PrintStream err = System.err;
	private String[] streams = new String[] {null, null, null};
	
	/* Nomi dei file json (da leggere dal file di configurazione) */
	private transient String
		serverJson = null,
		userJson = null,
		postJson = null;
	
	/* Gestione delle connessioni TCP con i client */
	private transient Map<SocketChannel, User> chanMap; //Mappa ogni username con un channel
	private Map<String, NavigableSet<User>> tagsMap;
	private transient Set<SocketChannel> sockets;
	private String serverHost = null;
	private int tcpPort = 0;
	private transient InetSocketAddress tcpSockAddr = null;
	private int tcpTimeout = 0;
	private int udpPort = 0;
	private transient ServerSocketChannel tcpListener = null;
	private transient Selector selector = null;
	
	/* Workers (elaborazione richieste -> prevedere un task per ognuna) */
	private int corePoolSize, maxPoolSize, keepAliveTime;
	private transient ExecutorService workers;
		
	/* Thread di calcolo ricompense ("writer" dell'actReg) + notifica client su multicast */
	private String mcastAddr;
	private int mcastPort;
	private long rewPeriod;
	private double rwAuthPerc, rwCurPerc;
	private TimeUnit rewUnit;
	private transient RewardManager rewManager;
	
	/* "Database" (verrà serializzato a parte) */
	private transient Table<String, User> users;
	private transient Table<Long, Post> posts;
	private transient Table<String, Wallet> wallets;
	
	/* RMI */
	private transient Registry rmiReg;
	private int regPort;
	private transient ServerInterfaceImpl svHandler; //OK
	
	private IDGen postGen;
	
	private void init(String serverJson, String userJson, String postJson, Table<String, User> users,
		Table<Long, Post> posts, Table<String, Wallet> wallets)
			throws IOException, AlreadyBoundException, DeserializationException {		
		this.serverJson = serverJson;
		this.userJson = userJson;
		this.postJson = postJson;
		this.init(users, posts, wallets);
	}

	private void init(Table<String, User> users, Table<Long, Post> posts, Table<String, Wallet> wallets)
			throws IOException, AlreadyBoundException, DeserializationException {
		this.users = ( users != null ? users : new Table<>() );
		this.posts = (posts != null ? posts : new Table<>() );
		this.wallets = (wallets != null ? wallets : new Table<>() );
		for (Wallet w : this.wallets.getAll()) w.deserialize();
		for (Post p : this.posts.getAll()) p.deserialize();
		for (User u : this.users.getAll()) u.deserialize(this.users, this.posts, this.wallets);
		this.in = (streams[0] != null ? new FileInputStream(streams[0]) : System.in);
		this.out = (streams[1] != null ? new PrintStream(streams[1]) : System.out);
		this.err = (streams[2] != null ? new PrintStream(streams[2]) : System.err);
		this.tcpSockAddr = new InetSocketAddress(InetAddress.getByName(serverHost), tcpPort);
		this.chanMap = new HashMap<>();
		this.sockets = new HashSet<>();
		this.tcpListener = ServerSocketChannel.open();
		this.tcpListener.socket().bind(this.tcpSockAddr);
		this.tcpListener.socket().setSoTimeout(tcpTimeout);
		this.tcpListener.configureBlocking(false);
		this.selector = Selector.open();
		this.tcpListener.register(selector, SelectionKey.OP_ACCEPT);
		this.workers = new ThreadPoolExecutor(
			this.corePoolSize,
			this.maxPoolSize,
			this.keepAliveTime,
			KEEPALIVEUNIT,
			new LinkedBlockingQueue<Runnable>(),
			new ThreadFactoryImpl(),
			new ThreadPoolExecutor.AbortPolicy()
		);
		this.rewManager = new RewardManager(
			this.mcastAddr,
			this.mcastPort,
			this.wallets,
			new ActionRegistry(this.rewPeriod, this.rewUnit),
			this.rwAuthPerc,
			this.rwCurPerc
		);
		this.svHandler = new ServerInterfaceImpl(this);
		this.rmiReg = LocateRegistry.createRegistry(regPort);
		this.rmiReg.bind(ServerInterface.REGSERVNAME, this.svHandler);		
	}

	private WinsomeServer(Map<String, String> configMap, Table<String, User> users, Table<Long, Post> posts,
		Table<String, Wallet> wallets) throws IOException, AlreadyBoundException, DeserializationException {
		
		Function<String, String> newStr = ConfigUtils.newStr;
		Function<String, Integer> newInt = ConfigUtils.newInt;
		Function<String, Long> newLong = ConfigUtils.newLong;
		Function<String, Double> newDouble = ConfigUtils.newDouble;
		Function<String, TimeUnit> newTimeUnit = ConfigUtils.newTimeUnit;
		
		Integer tmp;
		if (configMap != null) {
			
			streams[0] = ConfigUtils.setValue(configMap, "input", newStr);
			streams[1] = ConfigUtils.setValue(configMap, "output", newStr);
			streams[2] = ConfigUtils.setValue(configMap, "error", newStr);
						
			serverJson = ConfigUtils.setValue(configMap, "serverjson", newStr);
			userJson = ConfigUtils.setValue(configMap, "userjson", newStr);
			postJson = ConfigUtils.setValue(configMap, "postjson", newStr);			
			
			serverHost = ConfigUtils.setValue(configMap, "server", newStr);
			tcpPort = ConfigUtils.setValue(configMap, "tcpport", newInt);
			udpPort = ConfigUtils.setValue(configMap, "udpport", newInt);
			tcpTimeout = ConfigUtils.setValue(configMap, "tcptimeout", newInt);
			
			tmp = ConfigUtils.setValue(configMap, "corepoolsize", newInt);
			corePoolSize = (tmp != null && tmp >= 0 ? tmp : COREPOOLSIZE);
			
			tmp = ConfigUtils.setValue(configMap, "maxpoolsize", newInt);
			maxPoolSize = (tmp != null && tmp >= 0 ? tmp : MAXPOOLSIZE);
			
			tmp = ConfigUtils.setValue(configMap, "keepalivetime", newInt);
			keepAliveTime = (tmp != null && tmp >= 0 ? tmp : KEEPALIVETIME);
						
			regPort = ConfigUtils.setValue(configMap, "regport", newInt);
			mcastPort = ConfigUtils.setValue(configMap, "mcastport", newInt);
			mcastAddr = ConfigUtils.setValue(configMap, "multicast", newStr);
			rewPeriod = ConfigUtils.setValue(configMap, "rwperiod", newLong);
			rwAuthPerc = ConfigUtils.setValue(configMap, "rwauthperc", newDouble);
			rwCurPerc = ConfigUtils.setValue(configMap, "rwcurperc", newDouble);
			rewUnit = ConfigUtils.setValue(configMap, "rwperiodunit", newTimeUnit);
			
			tagsMap = new HashMap<>();
			
			postGen = new IDGen();
			Post.setGen(postGen);
			
			this.init(users, posts, wallets);			
		}
	}
	
	private static <T extends Comparable<T>,V extends Indexable<T>> Table<T, V> initTable(String filename, Type type)
		throws IOException, DeserializationException {
		Table<T, V> table = null;
		JsonReader reader = Serialization.fileReader(filename);
		if (reader != null) {
			try { table = Serialization.GSON.fromJson(reader, type); }
			catch (JsonIOException | JsonSyntaxException ex) { Common.debugExc(ex); table = null; }
			finally { reader.close(); }
		}
		if (table != null) table.deserialize();
		return table;
	}
	
	public static WinsomeServer newServer(Map<String, String> configMap) throws Exception {
		Common.notNull(configMap);
		String
			serverJson = ConfigUtils.setValue(configMap, "serverjson", ConfigUtils.newStr, DFLSERVERJSON),
			userJson = ConfigUtils.setValue(configMap, "userjson", ConfigUtils.newStr, DFLUSERJSON),
			postJson = ConfigUtils.setValue(configMap, "postjson", ConfigUtils.newStr, DFLPOSTJSON),
			walletJson = ConfigUtils.setValue(configMap, "walletjson", ConfigUtils.newStr, DFLWALLETJSON);
		
		Table<Long, Post> posts = initTable(postJson, POSTSTYPE);
		Table<String, Wallet> wallets = initTable(walletJson, Wallet.TYPE);
		Table<String, User> users = initTable(userJson, USERSTYPE);
		
		JsonReader serverReader = Serialization.fileReader(serverJson);
		WinsomeServer server = null;
		if (serverReader != null) {
			try { server = Serialization.GSON.fromJson(serverReader, TYPE); }
			catch (JsonIOException | JsonSyntaxException ex) { Common.debugln(ex); server = null; }
			finally { serverReader.close(); }
		}
		if (server != null) server.init(serverJson, userJson, postJson, users, posts, wallets);
		else server = new WinsomeServer(configMap, users, posts, wallets);
		return server;
	}
	
	public synchronized void serialize() throws IllegalStateException {
		Gson gson = Serialization.GSON;
		try (
			JsonWriter usersWriter = Serialization.fileWriter(userJson);
			JsonWriter postsWriter = Serialization.fileWriter(postJson);
			JsonWriter serverWriter = Serialization.fileWriter(serverJson);
		){
			gson.toJson(users, USERSTYPE, usersWriter);
			gson.toJson(posts, POSTSTYPE, postsWriter);
			gson.toJson(this, TYPE, serverWriter);
		} catch (Exception ex) { Common.debugExc(ex); throw new IllegalStateException(); }
	}
	
	boolean registerUser(String username, String password, List<String> tags) { return true; }
	
	public void close() throws Exception { }
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			boolean first = false;
			Field[] fields = this.getClass().getDeclaredFields();
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					sb.append( (first ? ", " : "") + "\n" + f.getName() + " = " + f.get(this) );
					first = true;
				}
			}
		} catch (IllegalAccessException ex) { ex.printStackTrace(err); }
		sb.append("\n]");
		return sb.toString();
	}
}