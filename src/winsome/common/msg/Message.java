package winsome.common.msg;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.SocketException;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
	
	public static final Charset
		DFLCHARSET = Charset.defaultCharset(),
		ASCII = StandardCharsets.US_ASCII;
	
	/* Error messages */
	public static final String
	INV_ID_BYTE = "Invalid identifier: '%d'",
	INV_PARAM_BYTE = "Invalid param: '%s'",
	INV_ID_STR = "Invalid identifier: '%s'",
	INV_PARAM_STR = "Invalid param: '%s'",
	UNKNOWN_MSG = "Unknwown message";
	
	public static final int
		IDSHIFT		= (Short.BYTES + 1) * Byte.SIZE,
		OPTSHIFT	= Short.BYTES * Byte.SIZE,
		INTBYTES	= Integer.BYTES;
	
	/* Identifier strings (some of them are also used as parameters for response messages (OK/ERR)) */
	public static final int
		HSHAKE = 1 << IDSHIFT,
		OK = 2 << IDSHIFT, /* Successful operation */
		ERROR = 3 << IDSHIFT, /* Error */
		REGISTER = 4 << IDSHIFT, /* Register */
		LOGIN = 5 << IDSHIFT, /* Login */	
		LOGOUT = 6 << IDSHIFT, /* Logout */
		LIST = 7 << IDSHIFT, /* List (users/followers/following); OK param for sending users/following/blog/feed */
		FOLLOW = 8 << IDSHIFT,
		UNFOLLOW = 9 << IDSHIFT,
		BLOG = 10 << IDSHIFT,
		POST = 11 << IDSHIFT, /* Also used as OK param for sending post data and as SHOW param for requesting post data */
		SHOW = 12 << IDSHIFT,
		DELETE = 13 << IDSHIFT,
		REWIN = 14 << IDSHIFT,
		RATE = 15 << IDSHIFT,
		COMMENT = 16 << IDSHIFT,
		WALLET = 17 << IDSHIFT, /* Also used as OK param for sending wallet data */
		HELP = 18 << IDSHIFT,
		QUIT = 19 << IDSHIFT,
		EXIT = 20 << IDSHIFT, /* Also used as OK params for sending confirmation messages for regular client exits */
		MORE = 21 << IDSHIFT;
	
	/* Parameter strings */
	public static final int
		EMPTY = 0, /* For messages with no (real) parameters */
		INFO = 1, /* OK param for sending multicast data and followers list */
		USERS = 2,
		FOLLOWERS = 3,
		FOLLOWING = 4,
		USLIST = 5,
		PSLIST = 6,
		POSTDATA = 7,
		WALLETDATA = 8,
		FEED = 9,
		BTC = 10,
		NOTIFY = 11;
	
	public static final List<Integer> COMMANDS = CollectionsUtils.toList(
		HSHAKE, OK, ERROR, REGISTER, LOGIN, LOGOUT, LIST, FOLLOW, UNFOLLOW, BLOG,
		POST, SHOW, DELETE, REWIN, RATE, COMMENT, WALLET, HELP, QUIT, EXIT, MORE
	);
	
	public static final List<Integer> PARAMS = CollectionsUtils.toList(
		EMPTY, INFO, USERS, FOLLOWERS, FOLLOWING, USLIST,
		PSLIST, POSTDATA, WALLETDATA, FEED, BTC, NOTIFY
	);
		
	public static final Map<Integer, List<Integer>> CODES = CollectionsUtils.newHashMapFromCollections(
		Arrays.asList(OK, LIST, SHOW, WALLET),
		Arrays.asList(
			Arrays.asList(EMPTY, INFO, USLIST, PSLIST, POSTDATA, WALLETDATA),
			Arrays.asList(USERS, FOLLOWERS, FOLLOWING),
			Arrays.asList(FEED, POSTDATA),
			Arrays.asList(EMPTY, BTC, NOTIFY)
		)
	);
	
	private final int code, argN;
	@NotNull
	private final List<byte[]> arguments;
	private final String charset;
	private int length; /* Total length of the message */
	
	public static final int
		IDMASK		= (-1)*(int)Math.pow(2, IDSHIFT),	//0xff000000
		OPTSMASK	= 0xff << OPTSHIFT,					//0x00ff0000
		PARMASK		= (int)Math.pow(2, OPTSHIFT) - 1;	//0x0000ffff
	
	private static final Map<Integer, String> getCodeConsts() throws IllegalArgumentException, IllegalAccessException{
		Map<Integer, String> consts = new LinkedHashMap<>();
		Field[] fields = Message.class.getDeclaredFields();
		for (Field f : fields) {
			if ( Modifier.isStatic(f.getModifiers()) ) {
				int val = f.getInt(Message.class);
				int index;
				if ((val & IDMASK) == val) {
					index = COMMANDS.indexOf(val);
					if ( index >= 0 && index < COMMANDS.size() ) consts.put(val, f.getName().toLowerCase());
				} else if ((val & PARMASK) == val) {
					index = PARAMS.indexOf(val);
					if ( index >= 0 && index < PARAMS.size() ) consts.put(val, f.getName().toLowerCase());
				}
			}
		}
		return consts;
	}
	
	public static int toIDCode(String str) throws MessageException {
		Common.notNull(str);
		str = str.toUpperCase();
		Field f = null;
		int val = 0;
		try { f = Message.class.getDeclaredField(str); }
		catch (NoSuchFieldException nsfe) { throw new MessageException( Common.excStr(INV_ID_STR, str) ); }
		if (!Modifier.isStatic(f.getModifiers())) throw new MessageException( Common.excStr(INV_ID_STR, str) );
		try { val = f.getInt(Message.class); }
		catch (IllegalAccessException iae) { throw new MessageException("Internal error when fetching id code"); }
		if ((val & IDMASK) == val || !COMMANDS.contains(val)) throw new MessageException( Common.excStr(INV_ID_STR, str) );
		return val;
	}
	
	public static int toParamCode(String str) throws MessageException {
		Common.notNull(str);
		if (str.isEmpty()) return Message.EMPTY;
		str = str.toUpperCase();
		Field f = null;
		int val = 0;
		try { f = Message.class.getDeclaredField(str); }
		catch (NoSuchFieldException nsfe) { throw new MessageException( Common.excStr(INV_PARAM_STR, str) ); }
		if (!Modifier.isStatic(f.getModifiers())) throw new MessageException( Common.excStr(INV_PARAM_STR, str) );
		try { val = f.getInt(Message.class); }
		catch (IllegalAccessException iae) { throw new MessageException(); }
		if ((val & PARMASK) == val || !PARAMS.contains(val)) throw new MessageException( Common.excStr(INV_PARAM_STR, str) );
		return val;		
	}
	
	public static boolean checkCode(int code) throws MessageException {
		int idCode = code & IDMASK, paramCode = code & PARMASK, nopts = (code & OPTSMASK) >> OPTSHIFT;
		return Message.checkIdParam(idCode, paramCode, nopts);
	}
	
	public static boolean checkIdParam(int idCode, int paramCode, int nopts) throws MessageException {
		Common.allAndArgs(
			(idCode & PARMASK) == idCode, (paramCode & PARMASK) == paramCode,
			nopts >= 0, nopts < (2 << Byte.SIZE)
		);
		if (!COMMANDS.contains(idCode)) throw new MessageException(INV_ID_BYTE);
		if (!PARAMS.contains(paramCode)) throw new MessageException(INV_PARAM_BYTE);
		List<Integer> params = CODES.get(idCode);
		if (params == null) return (paramCode == Message.EMPTY);
		else return params.contains(paramCode);
	}
	
	public static boolean checkIdParam(int idCode, int paramCode) throws MessageException {
		return Message.checkIdParam(idCode, paramCode, 0);
	}
	
	public static int getCode(int idCode, int paramCode, int nopts) throws MessageException {
		if (!Message.checkIdParam(idCode, paramCode, nopts)) throw new MessageException();
		return idCode + paramCode + (nopts << OPTSHIFT);
	}
		
	public static int getCode(int idCode, int paramCode) throws MessageException {
		return Message.getCode(idCode, paramCode, 0);
	}
	
	@NotNull
	public static String[] toIdParamStrs(int idCode, int paramCode) throws MessageException {
		if (!Message.checkIdParam(idCode, paramCode, 0)) throw new MessageException();
		try {
			Map<Integer, String> names = Message.getCodeConsts();
			String[] res = new String[] {names.get(idCode), names.get(paramCode)};
			return res;
		} catch (IllegalAccessException iae) { throw new MessageException(); }
	}
	
	@NotNull
	public static String[] toIdParamStrs(int code) throws MessageException {
		int idCode = (code & IDMASK), paramCode = (code & PARMASK);
		return Message.toIdParamStrs(idCode, paramCode);
	}
	
	/**
	 * Creates a new OK message with a formatted confirmation text message for the receiver.
	 * @param format Format string.
	 * @param objs Objects to format.
	 * @return A new (OK, EMPTY) Message object.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newOK(String charset, String format, Object...objs) {
		String message = String.format(format, objs);
		try { return new Message( OK, EMPTY, CollectionsUtils.toList(message), charset ); }
		catch (MessageException mex) { return null; }
	}
	
	/**
	 * Creates a new ERR message with a formatted confirmation text message for the receiver.
	 * @param format Format string.
	 * @param objs Objects to format.
	 * @return A new (ERR, EMPTY) Message object.
	 * @throws IllegalArgumentException If thrown by constructor.
	 */
	public static Message newError(String charset, String format, Object...objs) {
		String message = String.format(format, objs);
		try{ return new Message(ERROR, EMPTY, CollectionsUtils.toList(message), charset); }
		catch (MessageException mex) { return null; }
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
	public static Message newInfo(String charset, String ip, int port, int mcastMsgLen, List<String> users, String fmt, Object...objs) {
		String message = String.format(fmt, objs);
		List<String> args = CollectionsUtils.toList(users, message, ip, Integer.toString(port), Integer.toString(mcastMsgLen));
		try { return new Message(OK, INFO, args, charset); }
		catch (MessageException mex) { return null; }
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
	public static Message newUserList(String charset, List<String> items, String fmt, Object...objs) {
		String message = String.format(fmt, objs);
		List<String> args = CollectionsUtils.toList(items, message);
		try { return new Message(OK, USLIST, args, charset); }
		catch (MessageException mex) { return null; }
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
	public static Message newPostList(String charset, List<String> items, String fmt, Object...objs) {
		String message = String.format(fmt, objs);
		List<String> args = CollectionsUtils.toList(items, message);
		try { return new Message(OK, PSLIST, args, charset); }
		catch (MessageException mex) { return null; }
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
	public static Message newPost(String charset, String title, String content, String likes, String dislikes,
		List<String> comments, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = CollectionsUtils.toList(comments, message, title, content, likes, dislikes);
		try { return new Message(OK, POSTDATA, args, charset); }
		catch (MessageException mex) { return null; }
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
	public static Message newWallet(String charset, double value, List<String> transactions, String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = CollectionsUtils.toList(transactions, message, Double.toString(value));
		try { return new Message(OK, WALLETDATA, args, charset); }
		catch (MessageException mex) { return null; }
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
	public static Message newBtcWallet(String charset, double btcValue, double value, List<String> transactions,
		String fmt, Object...objects) {
		String message = String.format(fmt, objects);
		List<String> args = CollectionsUtils.toList(transactions, message, Double.toString(btcValue), Double.toString(value));
		try { return new Message(OK, WALLETDATA, args, charset); }
		catch (MessageException mex) { return null; }
	}
		
	/**
	 * Converts a {@link Command} to a Message object.
	 * @param cmd Input command.
	 * @return A Message Object corresponding to the given command. 
	 * @throws MessageException If thrown by Message constructor.
	 */
	@NotNull
	public static Message newMessageFromCmd(Command cmd, String charset) throws MessageException {
		Common.notNull(cmd);
		List<String> args = CollectionsUtils.toList(cmd.getArgs());
		int
			cmdId = Message.toIDCode(cmd.getId()),
			cmdParam = Message.toParamCode(cmd.getParam());
		return new Message(cmdId, cmdParam, args, charset);
	}
		
	public Message(int idCode, int paramCode, List<String> arguments, String charset) throws MessageException {
		this(Message.getCode(idCode, paramCode), arguments, charset);
	}
	
	public Message(int idCode, int paramCode, List<String> arguments) throws MessageException {
		this(Message.getCode(idCode, paramCode), arguments, null);
	}
	
	public Message(int code, List<String> arguments, String charset) throws MessageException {
		if (!Message.checkCode(code)) throw new MessageException();
		
		int nopts = (charset != null ? 1 : 0);
		this.charset = (charset != null ? new String(charset.getBytes(ASCII), ASCII) : null);
		this.code = code + (nopts << OPTSHIFT);
		
		if (arguments == null) arguments = new ArrayList<>();
		this.arguments = new ArrayList<>();
		CollectionsUtils.remap(arguments, this.arguments, str -> {
			try { return str.getBytes(this.charset); }
			catch (UnsupportedEncodingException ex) { return str.getBytes(StandardCharsets.UTF_8); }
		});
		this.argN = this.arguments.size();
		byte[] chset = charset.getBytes(ASCII);
		int argsLen = chset.length + INTBYTES;
		for (int i = 0; i < argN; i++) argsLen += this.getArg(i).length;
		this.length = INTBYTES * (2 + this.argN) + argsLen;
	}
	
	public Message(int code, List<String> arguments) throws MessageException { this(code, arguments, null); }
	
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
		byte[] result = new byte[this.length + INTBYTES];
		int index = 0;
		index += Common.intsToByteArray(result, index, this.length, this.code, this.argN);		
		index += Common.lengthStringToByteArray(result, index, charset, ASCII);
		for (int i = 0; i < this.argN; i++) {
			byte[] curr = this.arguments.get(i);
			index += Common.lengthBytesToByteArray(result, index, curr);
		}
		Common.allAndState(index == result.length);
		return result;
	}
	
	public final int getCode() { return code; }
	public final int getIdCode() { return (code & IDMASK); }
	public final int getParamCode() { return (code & PARMASK); }
	public final int getArgN() { return argN; }
	public final List<byte[]> getArguments() { return arguments; }
	public final byte[] getArg(int index) { return arguments.get(index); }
	public final String getCharsetName() { return charset; }
	public final Charset getCharset() { return Charset.forName(charset); }
	public final boolean isEmpty() { return argN == 0; }
	
	public final byte[] getHeader() {
		byte[] header = new byte[2 * INTBYTES];
		Common.intsToByteArray(header, 0, code, argN);
		return header;
	}
	
	public final byte[] getLengths() {
		byte[] lengths = new byte[this.argN * INTBYTES];
		for (int i = 0; i < this.argN; i++)
			Common.intsToByteArray(lengths, i * INTBYTES, arguments.get(i).length);
		return lengths;
	}
	
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
	 * @throws MessageException Thrown by {@link #toIdParamStrs(byte, byte)} (invalid id/param) or by
	 *  Message constructor.
	 * @throws NullPointerException If chan == null or buf == null.
	 */
	@NotNull
	public static final Message recvFromChannel(ReadableByteChannel chan, MessageBuffer buf)
			throws IOException, MessageException {
		Common.notNull(chan, buf);
		String cArg;
		buf.clear();
		byte[] readArr = buf.writeAllToArray(INTBYTES, chan);
		Common.allAndConnReset(readArr != null, readArr.length == INTBYTES); /* EOS reached etc. */
		int length = Common.intFromByteArray(readArr);
		readArr = buf.writeAllToArray(length, chan);
		Common.allAndConnReset(readArr != null, readArr.length == length); /* EOS reached etc. */
		
		int code = Common.intFromByteArray(readArr);
		int argN = Common.intFromByteArray(readArr, INTBYTES);
		int index = 2 * INTBYTES;
		
		int chsetLen = Common.intFromByteArray(readArr, index);
		index += INTBYTES;
		
		String charset = new String(readArr, index, chsetLen, ASCII);
		index += chsetLen;
		
		List<String> arguments = new ArrayList<>();
		for (int i = 0; i < argN; i++) {
			int clen = Common.intFromByteArray(readArr, index);
			index += INTBYTES;
			cArg = new String(readArr, index, clen, charset);
			index += clen;
			arguments.add(cArg);
		}
		return new Message(code, arguments, charset);
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
		
		byte[] lengthArr = Common.readNBytes(in, INTBYTES);
		int length = Common.intFromByteArray(lengthArr);
		
		byte[] result = Common.readNBytes(in, length);
		
		int code = Common.intFromByteArray(result);		
		int argN = Common.intFromByteArray(result, INTBYTES);
		int index = 2 * INTBYTES;
		
		int chsetLen = Common.intFromByteArray(result, index);
		index += INTBYTES;
		
		String charset = new String(result, index, chsetLen, ASCII);
		index += chsetLen;
		
		List<String> arguments = new ArrayList<>();
		int clen;
		for (int i = 0; i < argN; i++) {
			clen = Common.intFromByteArray(result, index);
			index += INTBYTES;
			String str = new String(result, index, clen, charset);
			index += clen;
			arguments.add(str);
		}
		return new Message(code, arguments, charset);
	}
	
	@NotNull
	public String toString() { return Common.jsonString(this); }
}