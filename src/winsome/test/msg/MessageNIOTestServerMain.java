package winsome.test.msg;

import java.util.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;

import winsome.common.msg.*;
import winsome.util.*;

public final class MessageNIOTestServerMain {

	public static final String HOST = "localhost";
	public static final int PORT = 5000;
	
	private static final String LOGSTR = "SERVER : ";

	private static final int BYTE_BUF_CAP = 16;
	
	private static final String printClient(SocketChannel client) {
		Common.notNull(client);
		return "SocketChannel @ " + client.socket().getInetAddress() + ":" + client.socket().getPort();
	}
	
	private static void closeConnExit(SelectionKey key) throws IOException {
		Common.println(LOGSTR + "Connection closed for " + printClient((SocketChannel)key.channel()) );
		try { key.cancel(); key.channel().close(); }
		catch (IOException ioe) { System.exit(1); }
	}
	
	public static void main(String[] args) {
		Common.println(System.getProperty(Debug.DEBUGPROP));
		try (
			ServerSocketChannel listener = ServerSocketChannel.open();
			Selector selector = Selector.open();
		){
			listener.bind(new InetSocketAddress(PORT));
			listener.configureBlocking(false);
			listener.register(selector, SelectionKey.OP_ACCEPT);
			System.out.println(LOGSTR + "listening for connections on port " + PORT);
			while (true) {
				try {
					System.out.println("Selecting...");
					selector.select();
				} catch (IOException ioe) { ioe.printStackTrace(System.out); System.exit(1); }
				
				Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
				SelectionKey key;
				
				while (iterator.hasNext()) {
					key = iterator.next();
					iterator.remove();
					try {
						if (key.isAcceptable()) {
							ServerSocketChannel server = (ServerSocketChannel) key.channel();
							SocketChannel client = server.accept();
							System.out.println(LOGSTR + "Accepted connection from " + printClient(client));
							client.configureBlocking(false);
							SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
							Object[] objs = new Object[] {new MessageBuffer(BYTE_BUF_CAP), null};
							clientKey.attach(objs);
						} else {
							SocketChannel client = (SocketChannel) key.channel();
							Object[] objs = (Object[])key.attachment();
							MessageBuffer buffer = (MessageBuffer) objs[0];
							Message msg = (Message) objs[1];
							if (key.isReadable()) {
								System.out.println(LOGSTR + "Reading message...");
								msg = Message.recvFromChannel(client, buffer);
								if (msg != null) {
									System.out.println(LOGSTR + "Message received : " + msg.toString());
									int idCode = msg.getIdCode();
									int paramCode = msg.getParamCode();
									List<String> arguments = msg.getArguments();
									for (int i = 0; i < arguments.size(); i++) {
										arguments.set(i, arguments.get(i).toUpperCase());
									}
									key.interestOps(SelectionKey.OP_WRITE);
									objs[1] = new Message(idCode, paramCode, arguments);
								} else closeConnExit(key);
							} else if (key.isWritable()) {
								Common.println(LOGSTR + "Writing message...");								
								if (msg.sendToChannel(client, buffer)) {
									Common.println(LOGSTR + "Message sent : " + msg.toString());
									buffer.clear();
									objs[1] = null;
									key.interestOps(SelectionKey.OP_READ);
								} else closeConnExit(key);
							}
						}
					} catch (IOException | NotYetConnectedException ex) { closeConnExit(key); }
				} /* iterator.hasNext() */
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
			System.exit(1);
		}
	}
}