package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.net.*;

import winsome.server.data.*;
import winsome.util.*;
import winsome.server.action.*;

final class RewardManager extends Thread {
	
	private static final double TOTREWPERC = 100.0;
	private static final String NOTIFYMSG = "There is a new reward for you!";
	private static enum State { INIT, OPEN, CLOSED; };
	
	public static final Integer EXIT_SUCCESS = 0, EXIT_FAILURE = 1;
	
	private int mcastPort;
	private MulticastSocket socket;
	private InetAddress address;
	private Table<String, Wallet> wallets;
	private ActionRegistry registry;
	private RewardCalculator calculator;
	private State state;
	private DatagramPacket packet;
	private Integer exitCode;
	
	/*
	 * 1. Crea il socket di multicast e invia le notifiche lì
	 * 2. Aggiorna i portafogli degli utenti.
	 */
	public RewardManager(String mcastAddr, int mcastPort, Table<String, Wallet> wallets, ActionRegistry registry,
		double rewAuth, double rewCur) throws IOException {
		
		Common.notNull(mcastAddr, wallets, registry);
		Common.andAllArgs(mcastPort >= 0, rewAuth >= 0.0, rewCur >= 0, rewAuth + rewCur == TOTREWPERC);
		socket = new MulticastSocket(mcastPort);
		address = InetAddress.getByName(mcastAddr);
		state = State.INIT;
		this.mcastPort = mcastPort;
		this.wallets = wallets;
		this.registry = registry;
		this.calculator = new RewardCalculator(rewAuth, rewCur);
		this.packet = new DatagramPacket(NOTIFYMSG.getBytes(), NOTIFYMSG.length(), this.address, this.mcastPort);
		this.exitCode = null;
	}
	
	
	@SuppressWarnings("deprecation")
	public void run() {
		try {
			state = State.OPEN;
			socket.joinGroup(address);
			Common.allAndState(registry.open());
			List<Action> completed = new ArrayList<>();
			Map<String, Double> rewards;
			while (!this.isClosed()) {
				if (registry.getActions(completed)) {
					rewards = calculator.computeReward(completed);
					for (String user : rewards.keySet()) {
						Wallet w = wallets.get(user);
						Common.allAndState( w.newTransaction(rewards.get(user)) );
					}
					socket.send(packet);
					System.out.println("Notifying sent");
				} else if (!this.isClosed()) throw new IllegalStateException();
				completed.clear();
			}
			socket.leaveGroup(address);
			this.setExitCode(EXIT_SUCCESS);
		} catch (Exception ex) { Common.debugExc(ex); this.setExitCode(EXIT_FAILURE); }
		finally { socket.close(); registry.close(); }
	}
	
	public RewardManager(String mcastAddr, int mcastPort, Table<String, Wallet> wallets, long rewPeriod,
		TimeUnit rewUnit, int rwAuthPerc, int rwCurPerc) throws IOException {
		this(mcastAddr, mcastPort, wallets, new ActionRegistry(rewPeriod, rewUnit), rwAuthPerc, rwCurPerc);
	}
	
	public boolean isClosed() {
		boolean res;
		synchronized (state) { res = (state == State.CLOSED); }
		return res;
	}
	
	public void close() { synchronized (state) { state = State.CLOSED; } }
	
	public synchronized Integer getExitCode() { return exitCode; }
	
	public synchronized void setExitCode(Integer code) { exitCode = code; }
}