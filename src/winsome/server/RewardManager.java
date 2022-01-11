package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.net.*;

import winsome.server.data.*;
import winsome.util.*;
import winsome.annotations.NotNull;
import winsome.server.action.*;

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
	private ActionRegistry registry;
	private RewardCalculator<Integer, List<Integer>> calculator;
	private State state;
	
	private DatagramPacket buildPacket() {
		String msg = NOTIFYMSG + new Date().toString().substring(0, 19);
		return new DatagramPacket(msg.getBytes(), msg.length(), address, mcastPort);
	}
	
	public final int mcastMsgLen() { return 19 + NOTIFYMSG.length(); }
	
	/*
	 * 1. Crea il socket di multicast e invia le notifiche lì
	 * 2. Aggiorna i portafogli degli utenti.
	 */
	public RewardManager(WinsomeServer server, String mcastAddr, int socketPort, int mcastPort, Table<String, Wallet> wallets, ActionRegistry registry,
		double rewAuth, double rewCur, Map<Long, Double> iterationMap) throws IOException {
		
		Common.notNull(server, mcastAddr, wallets, registry);
		Common.allAndArgs(mcastPort >= 0, rewAuth >= 0.0, rewCur >= 0, rewAuth + rewCur == TOTREWPERC);
		socket = new MulticastSocket(socketPort);
		address = InetAddress.getByName(mcastAddr);
		state = State.INIT;
		this.server = server;
		this.mcastPort = mcastPort;
		this.wallets = wallets;
		this.registry = registry;
		this.calculator = new RewardCalculatorImpl(rewAuth, rewCur, iterationMap);
	}
	
	
	public RewardManager(WinsomeServer server, String mcastAddr, int socketPort, int mcastPort,
		Table<String, Wallet> wallets, Pair<Long, TimeUnit> pair, double rwAuthPerc, double rwCurPerc,
		Map<Long, Double> iterationMap) throws IOException {
		this(server, mcastAddr, socketPort, mcastPort, wallets, new ActionRegistry(pair), rwAuthPerc,
			rwCurPerc, iterationMap);
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
			Common.allAndState(registry.open());
			List<Action> completed = new ArrayList<>();
			Map<String, Double> rewards;
			DatagramPacket packet;
			
			while (!this.isClosed()) {
				if (registry.getActions(completed)) {
					rewards = calculator.computeReward(completed);
					for (String user : rewards.keySet()) {
						Wallet w = wallets.get(user);
						Common.allAndState( w.newTransaction(rewards.get(user)) );
					}
					packet = buildPacket();
					socket.send(packet);
					logger.log("Reward update notify sent (#%d time)", time);
					time++;
				} else if (!this.isClosed()) throw new IllegalStateException();
				completed.clear();
			}
			socket.leaveGroup(mcastaddr, net);
		} catch (InterruptedException ie) {
			logger.log("Exception caught: %s : %s", ie.getClass().getSimpleName(), ie.getMessage());
		} catch (Exception ex) {
			logger.logStackTrace(ex);
			if (ex.getClass().equals(IllegalStateException.class)) server.signalIllegalState(ex);
		} finally {
			socket.close();
			registry.close();
			logger.log("Registry closed");
			if ( !server.updateIters(((RewardCalculatorImpl)calculator).getIterationMap()) ) {
				logger.log("Failed to update posts iteration");
				server.signalIllegalState(new DataException());
			} else logger.log("Posts iterations updated");
			logger.log("RewardManager service ended");
		}
	}
	
	public boolean isClosed() { synchronized (state) { return (state == State.CLOSED); } }
	
	@NotNull
	public String toString() { return Common.jsonString(this); }
}