package winsome.test;

import java.net.*;
import java.io.*;

import winsome.common.msg.*;

public final class MessageExchangeTestServerMain {

	public static final String HOST = "localhost";
	public static final int PORT = 5000;
	
	public static void main(String[] args) {
		Message msg;
		Socket socket = null;
		try (ServerSocket listener = new ServerSocket(PORT)){
			while (true) {
				try {
					System.out.println("Listening for connection from " + listener.getInetAddress() +
							" @ " + listener.getLocalPort());
					socket = listener.accept();
					System.out.println("Accepted connection from " + socket.getInetAddress() + 
							" @ " + socket.getPort());
					InputStream in = socket.getInputStream();
					OutputStream out = socket.getOutputStream();
					while (true) {
						msg = Message.recvFromStream(in);
						if (msg == null) break; //Connection closed
						System.out.println(msg.toString() + "\n");
						for (int i = 0; i < msg.getArgN(); i++) {
							String cArg = msg.getArguments().get(i);
							msg.getArguments().set(i, cArg.toUpperCase());
						}
						Thread.sleep(50);
						if (!msg.sendToStream(out)) break; //Connection closed
					}
				} finally { socket.close(); }
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
			System.exit(1);
		}
	}
}