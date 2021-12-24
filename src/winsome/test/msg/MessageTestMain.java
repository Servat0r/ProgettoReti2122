package winsome.test.msg;

import java.io.FileInputStream;

import winsome.client.command.*;
import winsome.common.msg.*;

public final class MessageTestMain {
	
	public static void main(String[] args) {
		Command cmd;
		Message m;
		try ( CommandParser parser = new CommandParser(new FileInputStream("cmd_examples.txt")) ){
			while (parser.hasNextCmd()) {
				cmd = parser.nextCmd();
				System.out.println( (cmd != null ? cmd.toString() : "match not found") );
				m = new Message(cmd);
				System.out.println(m.toString());
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		}
	}
	
}