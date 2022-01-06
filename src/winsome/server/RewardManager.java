package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.net.*;

import winsome.server.data.*;
import winsome.util.*;
import winsome.annotations.NotNull;
import winsome.server.action.*;

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
	private RewardCalculator calculator;
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
	public RewardManager(WinsomeServer server, String mcastAddr, int mcastPort, Table<String, Wallet> wallets, ActionRegistry registry,
		double rewAuth, double rewCur) throws IOException {
		
		Common.notNull(server, mcastAddr, wallets, registry);
		Common.andAllArgs(mcastPort >= 0, rewAuth >= 0.0, rewCur >= 0, rewAuth + rewCur == TOTREWPERC);
		socket = new MulticastSocket(mcastPort);
		address = InetAddress.getByName(mcastAddr);
		state = State.INIT;
		this.server = server;
		this.mcastPort = mcastPort;
		this.wallets = wallets;
		this.registry = registry;
		this.calculator = new RewardCalculator(rewAuth, rewCur);
	}
	
	
	public void run() {
		server.log("RewardManager service started");
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
					server.log("Reward update notify sent (#%d time)", time);
					time++;
				} else if (!this.isClosed()) throw new IllegalStateException();
				completed.clear();
			}
			socket.leaveGroup(mcastaddr, net);
		} catch (Exception ex) {
			server.logStackTrace(ex);
			if (ex.getClass().equals(IllegalStateException.class)) server.signalIllegalState(ex);
		} finally { socket.close(); registry.close(); server.log("RewardManager service ended"); }
	}
	
	public RewardManager(WinsomeServer server, String mcastAddr, int mcastPort,
		Table<String, Wallet> wallets, Pair<Long, TimeUnit> pair, int rwAuthPerc, int rwCurPerc)
		throws IOException {
		this(server, mcastAddr, mcastPort, wallets, new ActionRegistry(pair), rwAuthPerc, rwCurPerc);
	}
	
	public boolean isClosed() { synchronized (state) { return (state == State.CLOSED); } }
	
	@NotNull
	public String toString() {
		String jsond = Serialization.GSON.toJson(this, RewardManager.class);
		return String.format("%s: %s", this.getClass().getSimpleName(), jsond);
	}
}