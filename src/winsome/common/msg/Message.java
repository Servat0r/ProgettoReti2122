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

/**
 * This class represents a message exchangeable among client and server.
 * A message is made up by:
 *	- an identifier of the request ({@link #idCode});
 *	- a parameter of the request ({@link #paramCode});
 *	- a variable number of "arguments" represented by strings.
 *  This class is used both by {@link winsome.client.WinsomeClient} and {@link winsome.server.WinsomeServer}
 *  for exchanging messages on their TCP connection, and supports message transferral over both
 *  channels and streams.
 * @author Salvatore Correnti
 * @see MessageBuffer
 */
public final class Message {
	
	/* Error messages */
	public static final String
	INV_ID_BYTE = "Invalid identifier: '%d'",
	INV_PARAM_BYTE = "Invalid param: '%s'",
	INV_ID_STR = "Invalid identifier: '%s'",
	INV_PARAM_STR = "Invalid param: '%s'",
	UNKNOWN_MSG = "Unknwown message";
	
	/* Identifier strings (some of them are also used as parameters for response messages (OK/ERR)) */
	public static final String
		OK = "ok", /* Successful operation */
		ERR = "error", /* Error */
		REG = "register", /* Register */
		LOGIN = "login", /* Login */	
		LOGOUT = "logout", /* Logout */
		LIST = "list", /* List (users/followers/following); OK param for sending users/following/blog/feed */
		FOLLOW = "follow",
		UNFOLLOW = "unfollow",
		BLOG = "blog",
		POST = "post", /* Also used as OK param for sending post data and as SHOW param for requesting post data */
		SHOW = "show",
		DELETE = "delete",
		REWIN = "rewin",
		RATE = "rate",
		COMMENT = "comment",
		WALLET = "wallet", /* Also used as OK param for sending wallet data */
		HELP = "help",
		QUIT = "quit", EXIT = "exit"; /* Also used as OK params for sending confirmation messages for regular client exits */
	
	/* Parameter strings */
	public static final String
		EMPTY = "", /* For messages with no (real) parameters */
		INFO = "info", /* OK param for sending multicast data and followers list */
		USERS = "users",
		FOLLOWERS = "followers",
		FOLLOWING = "following",
		USLIST = "userlist",
		PSLIST = "postlist",
		FEED = "feed",
		BTC = "btc",
		NOTIFY = "notify";
	
	public static final List<String> COMMANDS = CollectionsUtils.toList(
		OK, ERR, REG, LOGIN, LOGOUT, LIST, FOLLOW, UNFOLLOW, BLOG, POST, SHOW,
		DELETE, REWIN, RATE, COMMENT, WALLET, HELP, QUIT, EXIT
	);
	
	private static final List<String> emptyList = CollectionsUtils.toList(EMPTY);
	
	public static final Map<String, List<String>> CODES = CollectionsUtils.newHashMapFromCollections(
		COMMANDS,
		Arrays.asList(
			Arrays.asList(EMPTY, INFO, USLIST, PSLIST, POST, WALLET, QUIT, EXIT),
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
			Arrays.asList(EMPTY, BTC, NOTIFY),
			emptyList,
			emptyList,			
			emptyList		
		)
	);
	
	private final int idCode, paramCode, argN;
	@NotNull
	private final String idStr, paramStr;
	@NotNull
	private final List<String> arguments;
	private int length; /* Total length of the message */
	
	/**
	 * Converts a couple (id, param) representing id and param of a Message object into its corresponding
	 * integer couple as integer array.
	 * @param id Message identifier.
	 * @param param Message param.
	 * @return An integer array containing encoding of (id, param).
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
	
	/** See {@link #getCode(String, String)} */
	@NotNull
	public static final int[] getCode(String id) throws MessageException { return getCode(id, null); }
	
	/**
	 * Decodes (idCode, paramCode) into a couple (id, param) representing id and param of the current message.
	 * @param idCode Int representing id.
	 * @param paramCode Int representing param (-1 if there are no params).
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
	
	/**
	 * Creates a new OK message with a formatted confirmation text message for the receiver.
	 * @param format Format string.
	 * @param objs Objects to format.
	 * @return A new (OK, EMPTY) Message object.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newOK(String format, Object...objs) {
		String message = String.format(format, objs);
		try { return new Message( OK, EMPTY, CollectionsUtils.toList(message) ); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * Creates a new ERR message with a formatted confirmation text message for the receiver.
	 * @param format Format string.
	 * @param objs Objects to format.
	 * @return A new (ERR, EMPTY) Message object.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newError(String format, Object...objs) {
		String message = String.format(format, objs);
		try{ return new Message(ERR, EMPTY, CollectionsUtils.toList(message)); } catch (MessageException mex) { return null; }
	}

	/**
	 * Creates a new info message (multicast data + followers list) with a formatted text message for the receiver.
	 * @param ip Multicast group IP address.
	 * @param port Multicast group port.
	 * @param users Followers.
	 * @param fmt Format string.
	 * @param objs Objects to format.
	 * @return A new (OK, INFO) Message object.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newInfo(String ip, int port, int mcastMsgLen, List<String> users, String fmt, Object...objs) {
		String message = String.format(fmt, objs);
		List<String> args = CollectionsUtils.toList(users, message, ip, Integer.toString(port), Integer.toString(mcastMsgLen));
		try { return new Message(OK, INFO, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * Creates a new list message (username + tags).
	 * @param items User info / Post info strings.
	 * @param fmt Format string.
	 * @param objs Objects to format.
	 * @return A new (OK, USLIST) Message object.
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newUserList(List<String> items, String fmt, Object...objs) {
		String message = String.format(fmt, objs);
		List<String> args = CollectionsUtils.toList(items, message);
		try { return new Message(OK, USLIST, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * Creates a new list message (id + author + title).
	 * @param items User info / Post info strings.
	 * @param fmt Format string.
	 * @param objs Objects to format.
	 * @return A new (OK, PSLIST) Message object.
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newPostList(List<String> items, String fmt, Object...objs) {
		String message = String.format(fmt, objs);
		List<String> args = CollectionsUtils.toList(items, message);
		try { return new Message(OK, PSLIST, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * Creates a new post message (title, content, votes, comments, initial text message).
	 * @param title Title.
	 * @param content Content.
	 * @param likes Positive rates.
	 * @param dislikes Negative rates.
	 * @param comments Comments.
	 * @param fmt Format string.
	 * @param objects Objects to format.
	 * @return A new (OK, POST) Message object.
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newPost(String title, String content, String likes, String dislikes,
		List<String> comments, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = CollectionsUtils.toList(comments, message, title, content, likes, dislikes);
		try { return new Message(OK, POST, args); } catch (MessageException mex) { return null; }
	}
		
	/**
	 * Creates a new wallet message.
	 * @param value Wincoin wallet value.
	 * @param transactions List of transactions as formatted in {@link winsome.server.data.Wallet#history()}.
	 * @param fmt Format string.
	 * @param objects Objects to format.
	 * @return A new (OK, WALLET) Message object.
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newWallet(double value, List<String> transactions, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = CollectionsUtils.toList(transactions, message, Double.toString(value));
		try { return new Message(OK, WALLET, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * Creates a new wallet message with the value of the wincoin -> bitcoin conversion as first argument.
	 * @param btcValue Bitcoin value.
	 * @param value Wincoin value.
	 * @param transactions List of transactions as formatted in {@link winsome.server.data.Wallet#history()}.
	 * @param fmt Format string.
	 * @param objects Objects to format.
	 * @return A new (OK, WALLET) Message object.
	 * @throws MessageException If thrown by constructor or if {@link List#addAll(Collection)} fails.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newBtcWallet(double btcValue, double value, List<String> transactions,
		String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = CollectionsUtils.toList(transactions, message, Double.toString(btcValue), Double.toString(value));
		try { return new Message(OK, WALLET, args); } catch (MessageException mex) { return null; }
	}
	
	/**
	 * Create a new (response) message for confirming client exit after a {@link #EXIT} or {@link QUIT} request.
	 * @param fmt Format string.
	 * @param objects Objects to format.
	 * @return A new (OK, QUIT) Message object.
	 */
	public static Message newQuit(String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = CollectionsUtils.toList(message);
		try { return new Message(OK, QUIT, args); } catch (MessageException mex) { return null; }		
	}
	
	/**
	 * Converts a {@link Command} to a Message object.
	 * @param cmd Input command.
	 * @return A Message Object corresponding to the given command. 
	 * @throws MessageException If thrown by Message constructor.
	 */
	@NotNull
	public static Message newMessageFromCmd(Command cmd) throws MessageException {
		Common.notNull(cmd);
		List<String> args = CollectionsUtils.toList(cmd.getArgs());
		return new Message(cmd.getId(), cmd.getParam(), args);
	}
	
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
	
	/**
	 * Encodes a Message into a byte array to send to a stream/channel.
	 * Encoded format is a byte concatenation of:
	 *  1) 4 bytes for message length;
	 *  2) 4 bytes for message id;
	 *  3) 4 bytes for message param;
	 *  4) 4 bytes for the other number of arguments;
	 *  5) for each argument: 4 bytes for the length of byte encoding of the string followed by that byte encoding.
	 * @return A byte array containing the encoded message.
	 */
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
	
	@NotNull
	public final String getIdStr() { return this.idStr; }
	
	@NotNull
	public final String getParamStr() { return this.paramStr; }
	
	@NotNull
	public final String[] getIdParam() {
		return new String[] {idStr, paramStr};
	}
	
	public final int getIdCode() { return idCode; }
	public final int getParamCode() { return paramCode; }
	public final int getArgN() { return argN; }
	public final List<String> getArguments() { return arguments; }
	public final int getLength() { return length; }
	
	/**
	 * Sends a message to a channel using a MessageBuffer as support.
	 * @param chan Output channel.
	 * @param buf Support buffer.
	 * @throws IOException On I/O errors.
	 */
	public final boolean sendToChannel(WritableByteChannel chan, MessageBuffer buf) throws IOException {
		Common.notNull(chan, buf);
		buf.clear();
		byte[] data = this.encode();
		try { buf.readAllFromArray(data, chan); return true; }
		catch (SocketException ex) { return false; }
	}
	
	/**
	 * Sends a message to a stream.
	 * @param out Output stream.
	 * @throws IOException On I/O errors.
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
	 *  Message constructor.
	 * @throws NullPointerException If chan == null or buf == null.
	 */
	@NotNull
	public static final Message recvFromChannel(ReadableByteChannel chan, MessageBuffer buf)
			throws IOException, MessageException {
		Common.notNull(chan, buf);
		String cArg;
		buf.clear();
		byte[] readArr = buf.writeAllToArray(Integer.BYTES, chan);
		Common.allAndConnReset(readArr != null, readArr.length == Integer.BYTES); /* EOS reached etc. */
		int length = Common.intFromByteArray(readArr);
		readArr = buf.writeAllToArray(length, chan);
		Common.allAndConnReset(readArr != null, readArr.length == length); /* EOS reached etc. */
		
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
	 * Receives a Message object from a stream.
	 * @param in Input stream.
	 * @return A Message object as decoded by the received data.
	 * @throws IOException On I/O errors.
	 * @throws MessageException If the built message is not correct.
	 * @throws NullPointerException If in == null.
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