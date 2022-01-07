package winsome.common.msg;

import java.io.*;
import java.net.SocketException;
import java.nio.channels.*;
import java.util.*;

import winsome.annotations.NotNull;
import winsome.client.command.*;
import winsome.util.*;

/*
 * messaggio scambiato su tcp = {
 *   4 byte per lunghezza resto messaggio
 *   4 byte per idCode
 *   4 byte per paramCode
 *   4 byte per argN
 *   per ogni argomento {
 *     4 byte di lunghezza della stringa
 *     bytes della stringa
 *   }
 * }
 * length(msg) = (4 +) 12 + 4 * msg.argN + Sum_(i=0..(argN-1))(length(arguments[i]))
 */

public final class Message {
	
	public static final String
	INV_ID_BYTE = "Invalid identifier: '%d'",
	INV_PARAM_BYTE = "Invalid param: '%s'",
	INV_ID_STR = "Invalid identifier: '%s'",
	INV_PARAM_STR = "Invalid param: '%s'",
	UNKNOWN_MSG = "Unknwown message";
	
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
		NOTIFY = "notify",
		CMD = "cmd";
	
	public static final List<String> COMMANDS = Common.toList(
		OK, ERR, FWLIST, MCAST, REG, LOGIN, LOGOUT, LIST, FOLLOW, UNFOLLOW, BLOG, POST, SHOW,
		DELETE, REWIN, RATE, COMMENT, WALLET, HELP, QUIT, EXIT
	);
	
	private static final List<String> emptyList = Common.toList(EMPTY);
	
	public static final Map<String, List<String>> CODES = Common.newHashMapFromLists(
		COMMANDS,
		/*
		 * EMPTY -> messaggio (di conferma)
		 * INFO -> messaggio, ip, port(, udp?), <utente : tags>
		 * LIST -> messaggio, <listItem> (<utente # tags> per utenti, <id : author : title> per post)
		 * POST -> messaggio, title, content, likes, dislikes, <comment>
		 * WALLET -> messaggio, valore, <transazione>
		 */
		Arrays.asList(
			Arrays.asList(EMPTY, INFO, LIST, POST, WALLET, QUIT, EXIT),
			emptyList,
			emptyList,			
			emptyList,			
			emptyList,			
			emptyList,			
			emptyList,			
			Arrays.asList(USERS, FOLLOWERS, FOLLOWING),
			emptyList,			
			emptyList,			
			emptyList,			
			emptyList,			
			Arrays.asList(FEED, POST),
			emptyList,			
			emptyList,			
			emptyList,			
			emptyList,			
			Arrays.asList(EMPTY, BTC),
			Arrays.asList(EMPTY, CMD),
			emptyList,			
			emptyList		
		)
	);
	
	private final int idCode, paramCode, argN;
	@NotNull
	private final String idStr, paramStr;
	@NotNull
	private final List<String> arguments;
	private int length;
	
	/**
	 * Converts a couple (id, param) representing id and param of a Message object into its corresponding
	 * byte couple as byte array.
	 * @param id Message identifier.
	 * @param param Message param.
	 * @return A byte array containing encoding of (id, param).
	 * @throws MessageException If id corresponds to an invalid command or param corresponds to an invalid param.
	 * @throws IllegalArgumentException If id == null.
	 */
	@NotNull
	public static final int[] getCode(String id, String param) throws MessageException {
		Common.notNull(id);
		if (param == null) param = EMPTY;
		int[] result = new int[] {-1, -1}; // -1 -> not given
		result[0] = COMMANDS.indexOf(id);
		if (result[0] == -1) throw new MessageException(Common.excStr(INV_ID_STR, id));
		List<String> cvect = CODES.get(id);
		result[1] = cvect.indexOf(param);
		if (result[1] == -1) throw new MessageException(Common.excStr(INV_PARAM_STR, param));
		return result;
	}
	
	@NotNull
	public static final int[] getCode(String id) throws MessageException { return getCode(id, null); }
	
	/**
	 * Decodes (idCode, paramCode) into a couple (id, param) representing id and param of the current message.
	 * @param idCode Byte representing id.
	 * @param paramCode Byte representing param (-1 if there are no params).
	 * @return A String array of length 2 containing the decoded strings.
	 * @throws MessageException For unrecognized id or param.
	 */
	@NotNull
	public static final String[] getIdParam(int idCode, int paramCode) throws MessageException {
		if ( !(idCode >= 0 && idCode < COMMANDS.size()) )
			throw new MessageException(Common.excStr(INV_ID_BYTE, idCode));
		
		if ( !(paramCode >= -1) )
			throw new MessageException(Common.excStr(INV_PARAM_BYTE, paramCode));
		
		String id = new String(COMMANDS.get(idCode));
		String param = new String(EMPTY);
		List<String> l = CODES.get(id);
		if (l == null) throw new IllegalStateException();
		if ( paramCode == -1 || (paramCode >= l.size()) )
			throw new MessageException(Common.excStr( INV_PARAM_BYTE, paramCode));
		param = new String( l.get(paramCode) );
		return new String[] {id, param};
	}

	/*
	 * EMPTY -> messaggio (di conferma)
	 * INFO -> messaggio, ip, port(, udp?), <utente : tags>
	 * LIST -> messaggio, <listItem> (<utente : tags> per utenti, <id : author : title> per post)
	 * POST -> messaggio, title, content, likes, dislikes, <comment>
	 * WALLET -> messaggio, valore, <transazione>
	 */
	/**
	 * Creates a new OK message with a confirmation text message for the receiver.
	 * @param message Text message for the receiver.
	 * @return A new OK Message object.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newOK(String format, Object...objs) {
		String message = String.format(format, objs);
		try { return new Message( OK, EMPTY, Common.toList(message) ); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * 
	 * @param message Text message for the receiver.
	 * @return
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newError(String format, Object...objs) {
		String message = String.format(format, objs);
		try{ return new Message(ERR, EMPTY, Common.toList(message)); } catch (MessageException mex) { return null; }
	}

	/**
	 * Creates a new info message (multicast data + followers list) for the receiver.
	 * @param message Text message for the receiver.
	 * @param ip Multicast group IP address.
	 * @param port Multicast group port.
	 * @param users Followers.
	 * @return A new INFO Message object.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newInfo(String ip, int port, int mcastMsgLen, List<String> users, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = Common.toList(users, message, ip, Integer.toString(port), Integer.toString(mcastMsgLen));
		try { return new Message(OK, INFO, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * Creates a new list message (username+tags for "list following" and "list users", 
	 * id+author+title for "show feed" and "blog").
	 * @param message Text message for the receiver.
	 * @param items User info / Post info strings.
	 * @return A new LIST Message object.
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newList(List<String> items, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = Common.toList(items, message);
		try { return new Message(OK, LIST, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * 
	 * @param message Text message for the receiver.
	 * @param title
	 * @param content
	 * @param likes
	 * @param dislikes
	 * @param comments
	 * @return
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newPost(String title, String content, long likes, long dislikes,
		List<String> comments, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = Common.toList(comments, message, title, content, Long.toString(likes), Long.toString(dislikes));
		try { return new Message(OK, POST, args); } catch (MessageException mex) { return null; }
	}
	
	public static Message newPost(List<String> postData, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = Common.toList(postData, message);
		try { return new Message(OK, POST, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * 
	 * @param message Text message for the receiver.
	 * @param value
	 * @param transactions
	 * @return
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newWallet(double value, List<String> transactions, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = Common.toList(transactions, message, Double.toString(value));
		try { return new Message(OK, WALLET, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * 
	 * @param message Text message for the receiver.
	 * @param btcValue
	 * @param value
	 * @param transactions
	 * @return
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newBtcWallet(double btcValue, double value, List<String> transactions,
		String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = Common.toList(transactions, message, Double.toString(btcValue), Double.toString(value));
		try { return new Message(OK, WALLET, args); } catch (MessageException mex) { return null; }
	}
	
	public static Message newQuit(String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = Common.toList(message);
		try { return new Message(OK, QUIT, args); } catch (MessageException mex) { return null; }		
	}
	
	@NotNull
	public static Message newMessageFromCmd(Command cmd) throws MessageException {
		Common.notNull(cmd);
		List<String> args = Common.toList(cmd.getArgs());
		return new Message(cmd.getId(), cmd.getParam(), args);
	}

	/**
	 * 
	 * @param id
	 * @param param
	 * @param arguments
	 * @throws MessageException
	 */
	public Message(String id, String param, List<String> arguments) throws MessageException {
		Common.notNull(id);
		if (param == null) param = Message.EMPTY;
		int[] codes = Message.getCode(id, param);
		this.idCode = codes[0];
		this.paramCode = codes[1];
		this.idStr = id;
		this.paramStr = param;
		this.arguments = (arguments != null ? arguments : new ArrayList<>());
		this.argN = this.arguments.size();
		int argsLen = 0;
		for (int i = 0; i < argN; i++) argsLen += arguments.get(i).getBytes().length;
		this.length = 3 * Integer.BYTES + Integer.BYTES * this.argN + argsLen;
	}
	
	/**
	 * 
	 * @param idCode
	 * @param paramCode
	 * @param arguments
	 * @throws MessageException
	 */
	public Message(int idCode, int paramCode, List<String> arguments) throws MessageException {
		Common.notNull(arguments);
		
		String[] strCodes = Message.getIdParam(idCode, paramCode);
		
		this.idCode = idCode;
		this.paramCode = paramCode;		
		this.idStr = strCodes[0];
		this.paramStr = strCodes[1];
		this.arguments = (arguments != null ? arguments : new ArrayList<>());
		this.argN = this.arguments.size();
		int argsLen = 0;
		for (int i = 0; i < argN; i++) argsLen += arguments.get(i).getBytes().length;
		this.length = 3 * Integer.BYTES + Integer.BYTES * this.argN + argsLen;
	}

	public final byte[] encode() {
		byte[] result = new byte[this.length + Integer.BYTES];
		int index = 0;
		index += Common.intsToByteArray(result, index, this.length, this.idCode, this.paramCode, this.argN);
		for (int i = 0; i < this.argN; i++) {
			index += Common.lengthStringToByteArray(result, index, this.arguments.get(i));
		}
		Common.allAndState(index == result.length);
		return result;
	}
	
	/**
	 * 
	 * @return
	 */
	@NotNull
	public final String getIdStr() { return this.idStr; }
	
	/**
	 * 
	 * @return
	 * @throws MessageException
	 */
	@NotNull
	public final String getParamStr() { return this.paramStr; }
	
	/**
	 * 
	 * @return
	 * @throws MessageException
	 */
	@NotNull
	public final String[] getIdParam() throws MessageException {
		return new String[] {idStr, paramStr};
	}
	
	public final int getIdCode() { return idCode; }
	public final int getParamCode() { return paramCode; }
	public final int getArgN() { return argN; }
	public final List<String> getArguments() { return arguments; }
	public final int getLength() { return length; }
	
	/**
	 * 
	 * @param chan
	 * @param buf
	 * @throws IOException
	 */
	public final boolean sendToChannel(WritableByteChannel chan, MessageBuffer buf) throws IOException {
		Common.notNull(chan, buf);
		buf.clear();
		byte[] data = this.encode();
		try { buf.readAllFromArray(data, chan); return true; }
		catch (SocketException | ConnResetException ex) { return false; }
	}
	
	/**
	 * 
	 * @param out
	 * @throws IOException
	 */
	public final boolean sendToStream(OutputStream out) throws IOException {
		Common.notNull(out);		
		byte[] data = this.encode();
		try { out.write(data); return true; } catch (SocketException se) { return false; }
	}

	/**
	 * Reads a complete message from a channel using a MessageBuffer as support.
	 * @param chan Input channel.
	 * @param buf MessageBuffer from which data "transients".
	 * @return A Message object representing the message received on success, null if message
	 * reading has not correctly completed (e.g. EOS closed by other peer).
	 * @throws IOException Thrown by {@link MessageBuffer#writeAllToArray(int, ReadableByteChannel)}
	 * @throws MessageException Thrown by {@link #getIdParam(byte, byte)} (invalid id/param) or by
	 * Message constructor.
	 */
	@NotNull
	public static final Message recvFromChannel(ReadableByteChannel chan, MessageBuffer buf)
			throws IOException, MessageException {
		Common.notNull(chan, buf);
		String cArg;
		buf.clear();
		byte[] readArr = buf.writeAllToArray(Integer.BYTES, chan);
		if (!(readArr != null && readArr.length == Integer.BYTES)) throw new ConnResetException(); /* EOS reached etc. */
		int length = Common.intFromByteArray(readArr);
		readArr = buf.writeAllToArray(length, chan);
		if (!(readArr != null && readArr.length == length)) throw new ConnResetException(); /* EOS reached etc. */
		int idCode = Common.intFromByteArray(readArr), paramCode = Common.intFromByteArray(readArr, Integer.BYTES);
		String[] strCodes = Message.getIdParam(idCode, paramCode);
		int argN = Common.intFromByteArray(readArr, Integer.BYTES * 2);
		int index = 3 * Integer.BYTES;
		List<String> arguments = new ArrayList<>();
		for (int i = 0; i < argN; i++) {
			int clen = Common.intFromByteArray(readArr, index);
			index += Integer.BYTES;
			cArg = new String(readArr, index, clen);
			index += clen;
			arguments.add(cArg);
		}
		return new Message(strCodes[0], strCodes[1], arguments);
	}
	
	/**
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 * @throws MessageException
	 */
	@NotNull
	public static final Message recvFromStream(InputStream in) throws IOException, MessageException {
		Common.notNull(in);
		
		byte[] lengthArr = Common.readNBytes(in, Integer.BYTES);
		int length = Common.intFromByteArray(lengthArr);
		
		byte[] result = Common.readNBytes(in, length);
		
		int idCode = Common.intFromByteArray(result);
		int paramCode = Common.intFromByteArray(result, Integer.BYTES);
		int argN = Common.intFromByteArray(result, 2 * Integer.BYTES);
		
		String[] strCodes = Message.getIdParam(idCode, paramCode);
		
		int index = 3 * Integer.BYTES;
				
		List<String> arguments = new ArrayList<>();
		int clen;
		for (int i = 0; i < argN; i++) {
			clen = Common.intFromByteArray(result, index);
			index += Integer.BYTES;
			String str = new String(result, index, clen);
			index += clen;
			arguments.add(str);
		}
		return new Message(strCodes[0], strCodes[1], arguments);
	}
	
	@NotNull
	public String toString() { return Common.jsonString(this); }
}