package winsome.test.msg;

import java.net.*;
import java.io.*;

import winsome.client.command.CommandParser;
import winsome.common.msg.*;
import winsome.util.Common;
import winsome.util.Debug;

public final class MessageNIOTestClientMain {

	public static final int PORT = 5000;
	public static final String HOST = "localhost";
	
	public static void main(String[] args) {
		Message msg;
		Socket socket = null;
		try ( CommandParser parser = new CommandParser(new FileInputStream("cmd_examples.txt")) ){
			socket = new Socket(HOST, PORT);
			Common.println("Socket connected to " + socket.getInetAddress() + 
					" @ " + socket.getPort());
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			while (parser.hasNextCmd()) {
				msg = Message.newMessageFromCmd(parser.nextCmd());
				Common.println(msg);
				if (!msg.sendToStream(out)) break;
				Debug.println("#client_main", "message sent");
				Thread.sleep(50);
				msg = Message.recvFromStream(in);
				if (msg == null) break;
				Common.println(msg);
			}
		} catch (SocketException sex) {
			sex.printStackTrace(System.out);
			if (socket.isClosed()) Common.println("Connection closed by the server");
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		} finally { try { socket.close(); } catch (Exception ex) {ex.printStackTrace(System.out);} }
	}
}