package winsome.client;

import winsome.util.Common;

import java.io.*;
import java.net.*;

final class ClientWalletNotifier extends Thread implements Closeable {

	private final InetAddress mcastIP;
	private MulticastSocket socket;
	private final PrintStream out, err;
	private byte[] buffer;
	
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
	}
	
	@SuppressWarnings("deprecation")
	public void run() {
		boolean grouped = false;
		String prefix = this.getClass().getSimpleName() + ": ";
		/* 
		 * InetSocketAddress addr = new InetSocketAddress(mcastIP, socket.getLocalPort());
		 * NetworkInterface net = NetworkInterface.getByInetAddress(mcastIP);
		 */
		try {
			this.socket.joinGroup(mcastIP);
			/* this.socket.joinGroup(addr, net); */
			grouped = true;
			this.out.println(prefix + "Wallet notifying service started");
			while (true) {
				DatagramPacket packet = new DatagramPacket(this.buffer, this.buffer.length);
				this.socket.receive(packet);
				this.out.println( new String(packet.getData()) );
			}
		} catch (IOException e) {
			e.printStackTrace(this.err);
			if (grouped)
				try {this.socket.leaveGroup(mcastIP); }
				/* this.socket.leaveGroup(addr, net); */
				catch (IOException e1) { e1.printStackTrace(this.err); }
				finally { this.out.println(prefix + "Wallet notifying service ended"); }
			else {
				String addr = new String(mcastIP.getAddress());
				this.err.printf(prefix + "Error: could not join multicast group at '%s'!%n", addr);
			}
		}
	}
	
	public void close() throws IOException { this.socket.close(); }	
}