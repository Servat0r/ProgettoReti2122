package winsome.common.msg;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
	
	public static final String
		OK = "ok", /* Operazione con successo + eventuali risultati */
		ERR = "error", /* Errore + indicazione sintassi/processing + eventuali argomenti */
		FWLIST = "fwersList", /* Lista di followers (fornita dopo la login) */
		MCAST = "multicast", /* Indirizzi di multicast (fornita dopo la login) */
		REG = "register", /* Richiesta di registrazione */
		LOGIN = "login", /* Richiesta di login */	
		LOGOUT = "logout",
		LIST = "list",
		FOLLOW = "follow",
		UNFOLLOW = "unfollow",
		BLOG = "blog",
		POST = "post",
		SHOW = "show",
		DELETE = "delete",
		REWIN = "rewin",
		RATE = "rate",
		COMMENT = "comment",
		WALLET = "wallet",
		HELP = "help",
		QUIT = "quit",
		EXIT = "exit";
	
	public static final String
		EMPTY = "",
		INFO = "info",
		USERS = "users",
		FOLLOWERS = "followers",
		FOLLOWING = "following",
		FEED = "feed",
		BTC = "btc",
		CMD = "cmd";
	
	public static final List<String> COMMANDS = Arrays.asList(
		OK, ERR, FWLIST, MCAST, REG, LOGIN, LOGOUT, LIST, FOLLOW, UNFOLLOW, BLOG, POST, SHOW,
		DELETE, REWIN, RATE, COMMENT, WALLET, HELP, QUIT, EXIT
	);
	
	public static final Map<String, List<String>> CODES = Common.newHashMapFromLists(
		Arrays.asList(OK, LIST, SHOW, WALLET, HELP),
		/*
		 * EMPTY -> messaggio (di conferma)
		 * INFO -> messaggio, ip, port(, udp?), <utente : tags>
		 * LIST -> messaggio, <listItem> (<utente # tags> per utenti, <id : author : title> per post)
		 * POST -> messaggio, title, content, likes, dislikes, <comment>
		 * WALLET -> messaggio, valore, <transazione>
		 */
		Arrays.asList(
			Arrays.asList(EMPTY, INFO, LIST, POST, WALLET),
			Arrays.asList(USERS, FOLLOWERS, FOLLOWING),
			Arrays.asList(FEED, POST),
			Arrays.asList(EMPTY, BTC),
			Arrays.asList(EMPTY, CMD)
		)
	);
		
	private static byte[] readNBytes(InputStream in, int length) throws IOException {
		Common.checkAll(in != null, length >= 0);
		List<Byte> total = new ArrayList<>();
		int bread = 0, res;
		while (bread < length) {
			res = in.read();
			if (res == -1) break;
			else total.add((byte)res);
			bread++;
		}
		byte[] result = new byte[bread];
		for (int i = 0; i < bread; i++) result[i] = total.get(i);
		return result;
	}

	private final byte idCode;
	private final byte paramCode;
	private final int argN;
	private final List<String> arguments;
	private int length;

	public static final byte[] getCode(String id, String param) throws MessageException {
		Common.notNull(id);
		if (param == null) param = EMPTY;
		byte[] result = new byte[] {-1, -1}; // -1 -> not given
		result[0] = (byte)COMMANDS.indexOf(id);
		if (result[0] == -1) throw new MessageException(Common.excStr("Invalid command"));
		List<String> cvect = CODES.get(id);
		if (result[0] >= 0 && (cvect != null)) result[1] = (byte)cvect.indexOf(param);
		return result;
	}
	
	public static final byte[] getCode(String id) throws MessageException { return getCode(id, null); }
	
	public static final String[] getIdParam(byte idCode, byte paramCode) throws MessageException {
		Common.checkAll(idCode >= 0, idCode < (byte)COMMANDS.size(), paramCode >= -1);
		String id = new String(COMMANDS.get((int)idCode));
		String param = new String(EMPTY);
		List<String> l = CODES.get(id);
		if (l != null) {
			if ( paramCode == -1 || (paramCode >= (byte)l.size()) ) throw new MessageException(Common.excStr("Invalid param"));
			param = new String( COMMANDS.get((int)paramCode) );
		}
		return new String[] {id, param};
	}

	/*
	 * EMPTY -> messaggio (di conferma)
	 * INFO -> messaggio, ip, port(, udp?), <utente : tags>
	 * LIST -> messaggio, <listItem> (<utente : tags> per utenti, <id : author : title> per post)
	 * POST -> messaggio, title, content, likes, dislikes, <comment>
	 * WALLET -> messaggio, valore, <transazione>
	 */
	public static Message newConfirm(String message) throws MessageException {
		return new Message( OK, EMPTY, Arrays.asList(message) );
	}

	public static Message newInfo(String message, String ip, int port, List<String> users) throws MessageException {
		List<String> args = Arrays.asList(message, ip, Integer.toString(port));
		if (!args.addAll(users)) throw new MessageException(Common.excStr("Failed to create argument list"));
		return new Message(OK, INFO, args);
	}

	public static Message newList(String message, List<String> items) throws MessageException {
		List<String> args = Arrays.asList(message);
		if (!args.addAll(items)) throw new MessageException(Common.excStr("Failed to create argument list"));
		return new Message(OK, LIST, args);
	}

	public static Message newPost(String message, String title, String content, long likes, long dislikes, List<String> comments)
		throws MessageException {
		List<String> args = Arrays.asList(message, title, content, Long.toString(likes), Long.toString(dislikes));
		if (!args.addAll(comments)) throw new MessageException(Common.excStr("Failed to create argument list"));
		return new Message(OK, POST, args);
	}

	public static Message newWallet(String message, double value, List<String> transactions) throws MessageException {
		List<String> args = Arrays.asList(message, Double.toString(value));
		if (!args.addAll(transactions)) throw new MessageException(Common.excStr("Failed to create argument list"));
		return new Message(OK, WALLET, args);
	}

	public static Message newBtcWallet(String message, double btcValue, double value, List<String> transactions)
		throws MessageException {
		List<String> args = Arrays.asList(message, Double.toString(btcValue), Double.toString(value));
		if (!args.addAll(transactions)) throw new MessageException(Common.excStr("Failed to create argument list"));
		return new Message(OK, WALLET, args);
	}

	public static Message newError(String message) throws MessageException {
		return new Message(ERR, EMPTY, Arrays.asList(message));
	}

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

	public final String getIdStr() {
		Common.checkAll(idCode >= 0, idCode < (byte)COMMANDS.size());
		return new String(COMMANDS.get((int)idCode));
	}
	
	public final String getParamStr() throws MessageException {
		Common.checkAll(paramCode >= -1);
		String id = this.getIdStr();
		String param = new String(EMPTY);
		List<String> l = CODES.get(id);
		if (l != null) {
			if ( paramCode == -1 || (paramCode >= (byte)l.size()) ) throw new MessageException(Common.excStr("Invalid param"));
			param = new String( COMMANDS.get((int)paramCode) );
		}
		return param;
	}
	
	public final String[] getIdParam() throws MessageException {
		return new String[] {getIdStr(), getParamStr()};
	}
	
	public final byte getIdCode() { return idCode; }
	public final byte getParamCode() { return paramCode; }
	public final int getArgN() { return argN; }
	public final List<String> getArguments() { return arguments; }
	public final int getLength() { return length; }
	
	public final boolean sendToChannel(WritableByteChannel chan, MessageBuffer buf) throws IOException {
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
		} catch (SocketException ex) { return false; }
	}
	
	public final boolean sendToStream(OutputStream out) throws IOException {
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
		} catch (SocketException ex) { return false; }
	}

	public static final Message recvFromChannel(ReadableByteChannel chan, MessageBuffer buf)
			throws IOException, MessageException {
		Common.notNull(chan, buf);
		String cArg;
		buf.clear();
		byte[] readArr = buf.writeAllToArray(6, chan);
		if (readArr == null) return null; /* EOS reached etc. */
		String[] strCodes = Message.getIdParam(readArr[0], readArr[1]);
		int argN = Common.intFromByteArray(readArr, 2);
		Common.debugf("after reading %d bytes: argN = %d%n", readArr.length, argN);		
		List<String> arguments = new ArrayList<>();
		for (int i = 0; i < argN; i++) {
			readArr = buf.writeAllToArray(4, chan);
			int clen = Common.intFromByteArray(readArr);
			readArr = buf.writeAllToArray(clen, chan);
			cArg = new String(readArr);
			Common.debugf("arg #%d : %s (%d long)%n", i, cArg, clen);
			arguments.add(cArg);
		}
		return new Message(strCodes[0], strCodes[1], arguments);
	}
	
	public static final Message recvFromStream(InputStream in) throws IOException, MessageException {
		Common.notNull(in);
		byte[] byteCodes = readNBytes(in, 2); /* idCode, paramCode */
		if (byteCodes.length < 2) return null; /* EOS reached or Exception thrown */
		String[] strCodes = Message.getIdParam(byteCodes[0], byteCodes[1]);
		Integer argN = Common.intFromByteArray(readNBytes(in, 4));
		if (argN == null) return null;
		List<String> arguments = new ArrayList<>();
		Integer clen;
		for (int i = 0; i < argN; i++) {
			clen = Common.intFromByteArray(readNBytes(in, 4));
			if (clen == null) return null;
			byte[] nextStr = readNBytes(in, clen);
			if (nextStr.length < clen) return null;
			arguments.add( new String(nextStr) );
		}
		return new Message(strCodes[0], strCodes[1], arguments);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					sb.append( (first ? ", " : "") + f.getName() + " = " + f.get(this) );
					if (!first) first = true;
				}
			}
		} catch (IllegalAccessException ex) { return null; }
		sb.append("]");
		return sb.toString();
	}
}