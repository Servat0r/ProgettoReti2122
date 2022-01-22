package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.net.*;

import winsome.server.data.*;
import winsome.util.*;
import winsome.annotations.NotNull;

/**
 * Reward management service. This class handles rewards calculations and client multicast notifies.
 * @author Salvatore Correnti
 * @see RewardCalculatorImpl
 */
final class RewardManager extends Thread {
	
	private static final double TOTREWPERC = 100.0;
	private static final String NOTIFYMSG = "New reward @ ";
	private static enum State { INIT, OPEN, CLOSED; };
	
	private WinsomeServer server;
	private int mcastPort;
	private MulticastSocket socket;
	private InetAddress address;
	private transient Table<String, Wallet> wallets;
	private transient Table<Long, Post> posts;
	private final long period;
	private final TimeUnit periodUnit;
	private RewardCalculator < List<Rate>, Map<String,Pair<Integer,List<Comment>>> > calculator;
	private State state;
	
	/** Conversion map from MILLISECONDS to other TimeUnits not "less than" milliseconds. */
	private static final Map<String, Long> convMap = CollectionsUtils.newHashMapFromCollections(
		CollectionsUtils.toList("DAYS", "HOURS", "MILLISECONDS", "MINUTES", "SECONDS"),
		CollectionsUtils.toList(86_400_000L, 3_600_000L, 1L, 60_000L, 1000L)
	);
	
	/**
	 * Conversion of a period expressed in a TimeUnit into its equivalent in milliseconds.
	 * @param period Period.
	 * @param unit TimeUnit.
	 * @return The value of period converted in milliseconds.
	 */
	public static long normalize(long period, TimeUnit unit) {
		String str = unit.toString();
		Long val = convMap.get(str);
		if (val != null) return period * val;
		else throw new IllegalArgumentException("Invalid TimeUnit");
	}
	
	private DatagramPacket buildPacket() {
		String msg = NOTIFYMSG + new Date().toString().substring(0, 19);
		return new DatagramPacket(msg.getBytes(), msg.length(), address, mcastPort);
	}
	
	private void await(long timeout) throws InterruptedException {
		while (true) {
			long rem = timeout - System.currentTimeMillis();
			if (rem > 0) Thread.sleep(rem);
			else break;
		}
	}
	
	public final int mcastMsgLen() { return 19 + NOTIFYMSG.length(); }
	
	/*
	 * 1. Crea il socket di multicast e invia le notifiche lì
	 * 2. Aggiorna i portafogli degli utenti.
	 */
	public RewardManager(WinsomeServer server, String mcastAddr, int socketPort, int mcastPort,
			Table<String, Wallet> wallets, Table<Long, Post> posts, Pair<Long, TimeUnit> periods,
			double rewAuth, double rewCur) throws IOException {
		
		Common.notNull(server, mcastAddr, wallets, posts);
		Common.allAndArgs(mcastPort >= 0, rewAuth >= 0.0, rewCur >= 0, rewAuth + rewCur == TOTREWPERC);
		this.socket = new MulticastSocket(socketPort);
		this.address = InetAddress.getByName(mcastAddr);
		this.state = State.INIT;
		this.server = server;
		this.mcastPort = mcastPort;
		this.wallets = wallets;
		this.posts = posts;
		this.period = periods.getKey();
		this.periodUnit = periods.getValue();
		this.calculator = new RewardCalculatorImpl(rewAuth, rewCur);
	}
		
	public void run() {
		Logger logger = server.logger();
		logger.log("RewardManager service started");
		int time = 1;
		try {
			state = State.OPEN;
			NetworkInterface net = null;
			try { net = NetworkInterface.getByInetAddress(address); }	
			catch (SocketException se) {
				se.printStackTrace();
				WinsomeServer.getServer().signalIllegalState(se);
				return;
			}
			
			InetSocketAddress mcastaddr = new InetSocketAddress(address, mcastPort);
			socket.joinGroup(mcastaddr, net);
			Map<String, Double> rewards;
			DatagramPacket packet;
			
			long
				now = System.currentTimeMillis(),
				normPeriod = RewardManager.normalize(period, periodUnit),
				timeout = now + normPeriod;
			
			while (!this.isClosed()) {
				this.await(timeout);
				rewards = calculator.computeReward(posts, timeout);
				double totRew = Common.doubleSum(rewards.values());
				logger.log("Calculated rewards: %s", Double.toString(totRew));
				timeout += normPeriod;
				for (String user : rewards.keySet()) {
					Wallet w = wallets.get(user);
					Common.allAndState( w.newTransaction(rewards.get(user)) );
				}
				packet = buildPacket();
				socket.send(packet);
				logger.log("Reward update notify sent (#%d time)", time);
				time++;
			}
			socket.leaveGroup(mcastaddr, net);
		} catch (InterruptedException ie) {
			logger.log("Exception caught: %s : %s", ie.getClass().getSimpleName(), ie.getMessage());
		} catch (Exception ex) {
			logger.logException(ex);
			if (ex.getClass().equals(IllegalStateException.class)) server.signalIllegalState(ex);
		} finally {
			socket.close();
			logger.log("RewardManager service ended");
		}
	}
	
	public boolean isClosed() { synchronized (state) { return (state == State.CLOSED); } }
	
	@NotNull
	public String toString() { return Common.jsonString(this); }
}