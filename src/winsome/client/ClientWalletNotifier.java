package winsome.client;

import winsome.util.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.LinkedBlockingQueue;

final class ClientWalletNotifier extends Thread implements Closeable {

	private final InetAddress mcastIP;
	private MulticastSocket socket;
	private byte[] buffer;
	private LinkedBlockingQueue<String> notifies;
	private WinsomeClient client;
	
	
	public ClientWalletNotifier(WinsomeClient client, int port, String mcastAddr, int msgLen) throws IOException {
		Common.notNull(client, mcastAddr); Common.andAllArgs(port >= 0, msgLen > 0);
		this.client = client;
		this.mcastIP = InetAddress.getByName(mcastAddr);
		if (!this.mcastIP.isMulticastAddress()) {
			this.client.logger().log("Error: '%s' is not a valid multicast address!%n", mcastAddr);
			throw new IllegalArgumentException();
		}
		this.socket = new MulticastSocket(port);
		this.buffer = new byte[msgLen];
		this.notifies = client.walletNotifyingList();
	}
	
	public void run() {
		boolean grouped = false;
		String prefix = this.getClass().getSimpleName() + ": ";
		InetSocketAddress addr = new InetSocketAddress(mcastIP, socket.getLocalPort());
		NetworkInterface net = null;
		
		try { net = NetworkInterface.getByInetAddress(mcastIP); }
		catch (SocketException se) { se.printStackTrace(); return; }
		try {
			this.socket.joinGroup(addr, net);
			grouped = true;
			client.logger().log(prefix + "Wallet notifying service started");
			while (true) {
				DatagramPacket packet = new DatagramPacket(this.buffer, this.buffer.length);
				this.socket.receive(packet);
				try { this.notifies.put( new String(packet.getData()) ); }
				catch (InterruptedException ie) { client.logger().logStackTrace(ie); return; }
			}
		} catch (IOException e) {
			if (grouped) {
				try { if (!socket.isClosed()) socket.leaveGroup(addr, net); }
				catch (IOException e1) { client.logger().logStackTrace(e1); }
				finally { client.logger().log(prefix + "Wallet notifying service ended"); }
			} else {
				String msg = new String(mcastIP.getAddress());
				client.logger().log(prefix + "Error: could not join multicast group at '%s'!%n", msg);
			}
		}
	}
	
	public void close() throws IOException { this.socket.close(); }	
}