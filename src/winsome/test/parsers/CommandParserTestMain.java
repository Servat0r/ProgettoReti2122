package winsome.test.parsers;

import java.io.*;
import java.util.HashMap;

import winsome.client.command.*;
import winsome.util.Common;

public final class CommandParserTestMain {
	
	public static void main(String[] args) {
		//Command cmd;
		try ( CommandParser parser = new CommandParser(new FileInputStream("cmd_examples.txt")) ){
			//PrintStream stream = new PrintStream("parser.txt");
			//stream.println(parser);
			//stream.close();
			for (CommandDef def : parser) {
				Common.printfln(def.getId());
				HashMap<String, CommandArgs> map = def.getArgs();
				for (String param : map.keySet()) {
					CommandArgs c = map.get(param);
					String str = (c != null ? c.getRegex() : "null");
					Common.printfln("  param = %s : regex = %s", param, str);
				}
			}
			//while (true) {
			//	cmd = parser.nextCmd();
			//	if (cmd == null || cmd.equals(Command.NULL)) break;
			//	System.out.println(cmd);
			//}
		} catch (Exception ex) { ex.printStackTrace(System.out); }
	}
}