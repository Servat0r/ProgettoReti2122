package winsome.test.parsers;

import java.io.FileInputStream;

import winsome.client.command.*;

public final class CommandParserTestMain {
	
	public static void main(String[] args) {
		Command cmd;
		try ( CommandParser parser = new CommandParser(new FileInputStream("cmd_examples.txt")) ){
			while (parser.hasNextCmd()) {
				cmd = parser.nextCmd();
				System.out.println( (cmd != null ? cmd.toString() : "match not found") );
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		}
	}
}