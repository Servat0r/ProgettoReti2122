package winsome.test.msg;

import java.net.*;
import java.io.*;

import winsome.client.command.CommandParser;
import winsome.common.msg.*;
import winsome.util.Common;

public final class MessageNIOTestClientMain {

	public static final int PORT = 5000;
	public static final String HOST = "localhost";
	
	public static void main(String[] args) {
		Message msg;
		Socket socket = null;
		try ( CommandParser parser = new CommandParser(new FileInputStream("cmd_examples.txt")) ){
			socket = new Socket(HOST, PORT);
			Common.printLn("Socket connected to " + socket.getInetAddress() + 
					" @ " + socket.getPort());
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			while (parser.hasNextCmd()) {
				msg = new Message(parser.nextCmd());
				Common.printLn(msg);
				if (!msg.sendToStream(out)) break;
				Common.debugln("#client_main", "message sent");
				Thread.sleep(50);
				msg = Message.recvFromStream(in);
				if (msg == null) break;
				Common.printLn(msg);
			}
		} catch (SocketException sex) {
			sex.printStackTrace(System.out);
			if (socket.isClosed()) Common.printLn("Connection closed by the server");
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		} finally { try { socket.close(); } catch (Exception ex) {ex.printStackTrace(System.out);} }
	}
}