package winsome.common.msg;

import java.io.*;
import java.net.SocketException;
import java.nio.channels.*;
import java.util.*;

import winsome.client.command.*;
import winsome.util.*;

/*
 * messaggio scambiato su tcp = {
 *   1 byte per idCode
 *   1 byte per paramCode
 *   4 byte per argN
 *   per ogni argomento {
 *     4 byte di lunghezza della stringa
 *     bytes della stringa
 *   }
 * }
 * length(msg) = 6 + 4 * msg.argN + Sum_(i=0..(argN-1))(length(arguments[i]))
 */

public final class Message {
	
	public static final List< Pair<String, String[]> > CODES = Arrays.asList(
		new Pair<>("ok", null),
		new Pair<>("error", new String[] {null, "parameter", "argument"}),
		new Pair<>("register", null),
		new Pair<>("login", null),
		new Pair<>("logout", null),
		new Pair<>("list", new String[] {"users", "followers", "following"}),
		new Pair<>("post", null),
		new Pair<>("rate", null),
		new Pair<>("comment", null),
		new Pair<>("blog", null),
		new Pair<>("show", new String[] {"feed", "post"}),
		new Pair<>("wallet", new String[] {null, "btc"}),
		new Pair<>("follow", null),
		new Pair<>("unfollow", null),
		new Pair<>("help", new String[] {null, "cmd"}),
		new Pair<>("quit", null),
		new Pair<>("exit", null)
	);
	
	public static final int MSGBUF_SIZE = 4096; //4KB
	
	public static final int ERR_SIMPLE = 0;
	public static final int ERR_PARAM = 1;
	public static final int ERR_ARG = 2;
		
	public static final byte[] getCode(String id, String param) throws MessageException {
		Common.notNull(id);
		byte[] result = new byte[] {-1, -1}; // -1 -> not given
		Pair<String, String[]> cpair;
		String[] cparams;
		for (int i = 0; i < CODES.size(); i++) {
			cpair = CODES.get(i);
			if (cpair.getKey().equals(id)) {
				result[0] = (byte)i;
				if (param != null) {
					cparams = cpair.getValue();
					if (cparams == null) throw new MessageException();
					int index = 0;
					while (index < cparams.length) {
						if ( (cparams[index] != null) && cparams[index].equals(param)) {
							result[1] = (byte)index;
							break;
						}
						index++;
					}
					if (index >= cparams.length) throw new MessageException();
				}
			}
		}
		return result;
	}
	
	public static final byte[] getCode(String id) throws MessageException { return getCode(id, null); }
	
	public static final String[] getIdParam(byte idCode, byte paramCode) {
		if ( (idCode >= 0) && (idCode < (byte)CODES.size()) ) {
			Pair<String, String[]> pair = CODES.get((int)idCode);
			String[] result = new String[2];
			result[0] = new String(pair.getKey());
			if (paramCode >= 0) {
				if (paramCode < (byte)pair.getValue().length)
					result[1] = new String(pair.getValue()[(int)paramCode]);
				else return null;
			} else result[1] = null;
			return result;
		} else return null; /* Invalid idCode */
	}
	
	private final byte idCode;
	private final byte paramCode;
	private final int argN;
	private final List<String> arguments;
	private int length;

	public Message(String id, String param, List<String> arguments) throws MessageException {
		Common.notNull(id);
		byte[] codes = Message.getCode(id, param);
		this.idCode = codes[0];
		this.paramCode = codes[1];
		this.argN = (arguments != null ? arguments.size() : 0);
		this.arguments = arguments;
		int argsLen = 0;
		if (arguments != null) {
			for (int i = 0; i < argN; i++) argsLen += arguments.get(i).getBytes().length;
		}
		this.length = 6 + 4 * this.argN + argsLen;
	}
	
	public Message(Command cmd) throws MessageException { this(cmd.getId(), cmd.getParam(), cmd.getArgs()); }
	
	public Message(byte idCode, byte paramCode, List<String> arguments) throws MessageException {
		this.idCode = idCode;
		this.paramCode = paramCode;
		this.argN = (arguments != null ? arguments.size() : 0);
		this.arguments = arguments;
		int argsLen = 0;
		if (arguments != null) {
			for (int i = 0; i < argN; i++) argsLen += arguments.get(i).getBytes().length;
		}
		this.length = 6 + 4 * this.argN + argsLen;
	}
	
	public static Message newOKMessage() throws MessageException { return new Message("ok", null, null); }
	
	public static Message newErrorMessage(int errType, String errVal, String errMsg) throws MessageException {
		String param;
		try { param = CODES.get(1).getValue()[errType]; }
		catch (Exception ex) { throw new MessageException(); }
		if (param != null && errVal == null) throw new MessageException();
		List<String> arguments = new ArrayList<>();
		if (errVal != null) arguments.add(errVal);
		if (errMsg != null) arguments.add(errMsg);
		return new Message("error", param, arguments);
	}
	
	public static Message newErrorMessage(int errType, String errVal) throws MessageException {
		return Message.newErrorMessage(errType, errVal, null);
	}
	
	public final byte getIdCode() { return idCode; }

	public final byte getParamCode() { return paramCode; }

	public final int getArgN() { return argN; }

	public final List<String> getArguments() { return arguments; }

	public final int getLength() { return length; }
	
	public boolean sendToChannel(WritableByteChannel chan, MessageBuffer buf) throws IOException {
		Common.notNull(chan, buf);
		String cstr;
		buf.clear();
		try {
			buf.readAllFromArray(new byte[] {idCode, paramCode}, chan);
			buf.readAllFromArray(Common.intToByteArray(argN), chan);
			for (int i = 0; i < argN; i++) {
				cstr = arguments.get(i);
				buf.readAllFromArray(Common.intToByteArray(cstr.length()), chan);
				buf.readAllFromArray(cstr.getBytes(), chan);
			}
			return true;
		} catch (SocketException ex) {
			System.out.println("Connection closed by other peer");
			return false;
		}
	}
	
	public static Message recvFromChannel(ReadableByteChannel chan, MessageBuffer buf)
			throws IOException, MessageException {
		Common.notNull(chan, buf);
		String cArg;
		buf.clear();
		byte[] readArr = buf.readAllToArray(6, chan);
		if (readArr == null) return null; /* EOS reached etc. */
		String[] strCodes = Message.getIdParam(readArr[0], readArr[1]);
		int argN = Common.intFromByteArray(readArr, 2);
		Common.debugf("after reading %d bytes: argN = %d%n", readArr.length, argN);		
		List<String> arguments = new ArrayList<>();
		for (int i = 0; i < argN; i++) {
			readArr = buf.readAllToArray(4, chan);
			int clen = Common.intFromByteArray(readArr);
			readArr = buf.readAllToArray(clen, chan);
			cArg = new String(readArr);
			Common.debugf("arg #%d : %s (%d long)%n", i, cArg, clen);
			arguments.add(cArg);
		}
		return new Message(strCodes[0], strCodes[1], arguments); //TODO Completare!
	}
	
	public boolean sendToStream(OutputStream out) throws IOException {
		Common.notNull(out);
		byte[] cArg;
		try {
			out.write(new byte[] {this.idCode, this.paramCode});
			out.write(Common.intToByteArray(this.argN));
			for (int i = 0; i < this.argN; i++) {
				cArg = this.arguments.get(i).getBytes();
				out.write(Common.intToByteArray(cArg.length));
				out.write(cArg);
			}
			return true;
		} catch (SocketException ex) {
			Common.printLn("Connection closed by other peer"); //Eventualmente sostituire con un PrintStream di log
			return false;
		}
	}
	
	public static Message recvFromStream(InputStream in) throws IOException, MessageException {
		Common.notNull(in);
		try {
			byte[] byteCodes = in.readNBytes(2); /* idCode, paramCode */
			if (byteCodes.length < 2) return null; /* EOS reached or Exception thrown */
			String[] strCodes = Message.getIdParam(byteCodes[0], byteCodes[1]);
			Integer argN = Common.intFromByteArray(in.readNBytes(4));
			if (argN == null) return null;
			List<String> arguments = new ArrayList<>();
			Integer clen;
			for (int i = 0; i < argN; i++) {
				clen = Common.intFromByteArray(in.readNBytes(4));
				if (clen == null) return null;
				byte[] nextStr = in.readNBytes(clen);
				if (nextStr.length < clen) return null;
				arguments.add( new String(nextStr) );
			}
			return new Message(strCodes[0], strCodes[1], arguments);
		} catch (SocketException ex) {
			Common.printLn("Connection closed by other peer");
			return null;
		}
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Message {id = " + (int)this.idCode);
		sb.append("; param = " + (int)this.paramCode);
		sb.append("; argN = " + this.argN);
		sb.append("; args = " + arguments.toString());
		sb.append("; msgLength = " + length + " bytes}");
		return sb.toString();
	}	
}