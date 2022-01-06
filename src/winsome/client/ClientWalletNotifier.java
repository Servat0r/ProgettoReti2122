package winsome.client;

import winsome.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

final class ClientWalletNotifier extends Thread implements Closeable {

	private final InetAddress mcastIP;
	private MulticastSocket socket;
	private final PrintStream out, err;
	private byte[] buffer;
	private List<String> notifies;
	
	public ClientWalletNotifier(WinsomeClient client, int port, String mcastAddr, int msgLen) throws IOException {
		Common.notNull(client, mcastAddr); Common.andAllArgs(port >= 0, msgLen > 0);
		this.out = client.getOut();
		this.err = client.getErr();
		this.mcastIP = InetAddress.getByName(mcastAddr);
		if (!this.mcastIP.isMulticastAddress()) {
			this.err.printf("Error: '%s' is not a valid multicast address!%n", mcastAddr);
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
			this.out.println(prefix + "Wallet notifying service started");
			while (true) {
				DatagramPacket packet = new DatagramPacket(this.buffer, this.buffer.length);
				this.socket.receive(packet);
				this.notifies.add( new String(packet.getData()) );
			}
		} catch (IOException e) {
			if (grouped) {
				try { if (!socket.isClosed()) socket.leaveGroup(addr, net); }
				catch (IOException e1) { e1.printStackTrace(out); }
				finally { out.println(prefix + "Wallet notifying service ended"); }
			} else {
				String msg = new String(mcastIP.getAddress());
				this.err.printf(prefix + "Error: could not join multicast group at '%s'!%n", msg);
			}
		}
	}
	
	public void close() throws IOException { this.socket.close(); }	
}