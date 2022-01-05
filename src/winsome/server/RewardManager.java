package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.*;

import winsome.server.data.*;
import winsome.util.*;
import winsome.annotations.NotNull;
import winsome.server.action.*;

final class RewardManager extends Thread {
	
	private static final double TOTREWPERC = 100.0;
	private static final String NOTIFYMSG = "New reward @ ";
	private static enum State { INIT, OPEN, CLOSED; };
	
	private LinkedBlockingQueue<Result> result;
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
	public RewardManager(LinkedBlockingQueue<Result> result, String mcastAddr, int mcastPort, Table<String, Wallet> wallets, ActionRegistry registry,
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
		this.result = result;
	}
	
	
	@SuppressWarnings("deprecation")
	public void run() {
		try {
			state = State.OPEN;
			socket.joinGroup(address);
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
					System.out.println("Notifying sent"); //TODO Magari un log in futuro
				} else if (!this.isClosed()) throw new IllegalStateException();
				completed.clear();
			}
			socket.leaveGroup(address);
			result.add(Result.newSuccess());
		} catch (ConnResetException iex) { }
		catch (IOException ioe) {}
		catch (InterruptedException ie) {}
		catch (Exception ex) {
			Debug.debugExc(ex);
			result.add(Result.newGenError());
		} finally { socket.close(); registry.close(); }
	}
	
	public RewardManager(LinkedBlockingQueue<Result> threadRes, String mcastAddr, int mcastPort,
		Table<String, Wallet> wallets, Pair<Long, TimeUnit> pair, int rwAuthPerc, int rwCurPerc)
		throws IOException {
		this(threadRes, mcastAddr, mcastPort, wallets, new ActionRegistry(pair), rwAuthPerc, rwCurPerc);
	}
	
	public boolean isClosed() {
		boolean res;
		synchronized (state) { res = (state == State.CLOSED); }
		return res;
	}
	
	public void close() { synchronized (state) { state = State.CLOSED; } }
	
	@NotNull
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		boolean first = false;
		Field[] fields = this.getClass().getDeclaredFields();
		Object obj;
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
				try {obj = f.get(this);} catch (IllegalAccessException ex) {continue;}
				sb.append( (first ? ", " : "") + "\n" + f.getName() + " = " + obj);
				first = true;
			}
		}
		sb.append("\n]");
		return sb.toString();		
	}
}