package winsome.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.regex.*;

import winsome.annotations.NotNull;


/**
 * Common tools and operations.
 * @author Salvatore Correnti.
 */
public final class Common {
	
	private Common() {}
	
	@NotNull
	/**
	 * Factory of regex matching functions.
	 * @param regex Pattern to match.
	 * @return A function of the form String -> Integer that tries to match
	 *  its input string with the regex pattern. 
	 */
	public static ToIntFunction<String> regexMatcher(String regex){
		Common.notNull(regex);
		return (str) -> {
			Matcher m = Pattern.compile(regex).matcher(str);
			Integer index = Integer.valueOf(m.find() ? m.end() : -1);
			return index;
		};
	}
	
	
	/* STRING MANIPULATION */
	@NotNull
	public static String quote(String str) {
		Common.notNull(str);
		return new String("\"" + str + "\"");
	}
	
	@NotNull
	/**
	 * If first and last character of the input string are both ("), removes them.
	 * @param str String to "dequote".
	 * @return A substring with the starting and ending (") removed if present,
	 *  the same string otherwise.
	 */
	public static String dequote(String str) {
		Common.notNull(str);
		if (str.startsWith("\"") && str.endsWith("\"")) return str.substring(1, str.length()-1);
		else return new String(str);
	}
	
	@NotNull
	/**
	 * Returns a string whose value is this string, with all leading white space removed. 
	 * If this String object represents an empty string, or if all code points in this
	 * string are white spaces, then an empty string is returned.
	 * @param str The string to strip.
	 * @return A substring of the input string starting from the first character that is
	 * not a whitespace one.
	 */
	public static String stripLeading(String str) {
		Common.notNull(str);
		if (str.isEmpty()) return str;
		char[] chars = str.toCharArray();
		int leading = 0;
		while (leading < chars.length) {
			if ( Character.isWhitespace(chars[leading]) ) leading++;
			else break;
		}
		int length = str.length() - leading;
		if (length <= 0) return new String("");
		else return new String(chars, leading, length);
	}
	
	@NotNull
	/**
	 * Returns a string whose value is this string, with all trailing white spaces removed. 
	 *  If this String object represents an empty string, or if all characters in this
	 *  string are white space, then an empty string is returned. 
	 * @param str The string to strip.
	 * @return A substring of the input string starting with all the trailing white spaces
	 * removed.
	 */
	public static String stripTrailing(String str) {
		Common.notNull(str);
		if (str.isEmpty()) return str;
		char[] chars = str.toCharArray();
		int trailing = chars.length - 1;
		while (trailing >= 0) {
			if ( Character.isWhitespace(chars[trailing]) ) trailing--;
			else break;
		}
		int len = trailing + 1;
		if (len <= 0) return new String("");
		else return new String(chars, 0, len);
	}
	
	@NotNull
	/**
	 * Returns a string whose value is this string, with all leading and trailing white spaces removed. 
	 *  If this String object represents an empty string,or if all code points in this string are white
	 *  space, then an empty string is returned. Otherwise, returns a substring of this string beginning
	 *  with the first code point that is not a white space up to and including the last code point that
	 *  is not a white space. 
	 * @param str
	 * @return
	 */
	public static String strip(String str) {
		Common.notNull(str);
		if (str.isEmpty()) return str;
		char[] chars = str.toCharArray();
		int leading = 0, trailing = chars.length - 1;
		while (leading < chars.length) {
			if (Character.isWhitespace(chars[leading])) leading++;
			else break;
		}
		while (trailing >= leading) {
			if (Character.isWhitespace(chars[trailing])) trailing--;
			else break;
		}
		int len = 1 + trailing - leading;
		if (len <= 0 || leading >= chars.length) return new String("");
		else return new String(chars, leading, len);
	}
	/* STRING MANIPULATION */
	
	
	/* PRINTING */
	/**
	 * Utility for printing formatted strings to a given PrintStream with automatic '\n' adding.
	 * @param stream Output PrintStream.
	 * @param format Format string.
	 * @param objs Object(s) to format.
	 */
	public static void printfln(PrintStream stream, String format, Object ...objs) {
		Common.notNull(format);
		String msg = String.format(format, objs);
		stream.println(msg);
	}
	
	/**
	 * {@link #printfln(PrintStream, String, Object...)} with PrintStream set to System.out.
	 * @param format Format string.
	 * @param objs Object(s) to format.
	 */
	public static void printfln(String format, Object ...objs) { printfln(System.out, format, objs); }
	
	@NotNull
	/**
	 * This method is used as support for toString() methods in other classes.
	 * @param obj Object to represent as a string.
	 * @return A string representation of the given object in the form \<ClassName\>: \<Json form\>.
	 */
	public static String jsonString(Object obj) {
		Common.notNull(obj);
		String cname = obj.getClass().getSimpleName(), jsond = Serialization.GSON.toJson(obj, obj.getClass());
		return String.format("%s: %s", cname, jsond);
	}
	/* PRINTING */
	
	
	/* EXCEPTIONS MESSAGES FORMATTING */
	/**
	 * Formats a message for using in creating Exception messages.
	 * @param format Format for creating output string.
	 * @param objs Objects to "insert" in formatted string.
	 * @return A formatted string of the form "calling method" : "formatted message".
	 */
	public static String excStr(String format, Object ...objs) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		String msg = String.format(format, objs);
		return (fname + Debug.DBGSEPAR + msg);
	}
	
	
	/* EXCEPTIONS MESSAGES FORMATTING */
	
	/* PARAMS CHECKING */
	private static final String EXCSTR = "Condition #%d is NOT satisfied!";
	
	/**
	 * Checks if all objects passed are not null and otherwise throws a NullPointerException
	 *  with a formatted message.
	 * @param msg Message to print into a formatted string if a NullPointerException is thrown.
	 * @param objs One or more objects to check.
	 * @throws NullPointerException If any of the items in objs is null.
	 */
	public static void notNull(String msg, Object ...objs) throws NullPointerException {
		for (int i = 0; i < objs.length; i++) if (objs[i] == null)
			throw new NullPointerException( excStr("arg #%d%s%s", i+1, Debug.DBGSEPAR, msg) );
	}
	
	/**
	 * {@link #notNull(String, Object...)} with the first parameter with a default value.
	 * @param objs One or more objects to check.
	 * @throws NullPointerException If any of the items in objs is null.
	 */
	public static void notNull(Object ...objs) throws NullPointerException { notNull("is null!", objs); }
	
	/**
	 * Checks if a collection of elements is null or contains any null items, and if yes
	 *  throws a NullPointerException.
	 * @param <T> Type of the items in the collection.
	 * @param coll The collection to test.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static <T> void collectionNotNull(Collection<T> coll) {
		Common.notNull(coll);
		for (T elem : coll) Common.notNull(elem);
	}
	
	/**
	 * Checks if all conditions are satisfied and otherwise throws an IllegalArgumentException
	 * with a standardized formatted message.
	 * @param conds One or more conditions to check.
	 * @throws IllegalArgumentException If any of the conditions is not satisfied.
	 */
	public static void allAndArgs(boolean ...conds) {
		for (int i = 0; i < conds.length; i++)
			if (!conds[i]) throw new IllegalArgumentException(Common.excStr(EXCSTR, i+1));
	}
	
	/**
	 * Checks if all conditions are satisfied and otherwise throws an IllegalStateException
	 * with a standardized formatted message.
	 * @param conds One or more conditions to check.
	 * @throws IllegalStateException If any of the conditions is not satisfied.
	 */
	public static void allAndState(boolean ...conds) {
		for (int i = 0; i < conds.length; i++)
			if (!conds[i]) throw new IllegalStateException(Common.excStr(EXCSTR, i+1));
	}
	
	public static final String CONNRESET = "Connection reset by peer";
	
	/**
	 * Checks if all conditions are satisfied and otherwise throws an IllegalStateException
	 * with a standardized message indicating connection reset.
	 * @param conds One or more conditions to check.
	 * @throws IOException If any of the conditions is not satisfied.
	 */
	public static void allAndConnReset(boolean ...conds) throws IOException {
		for (boolean b : conds) if (!b) throw new IOException(CONNRESET);
	}
	
	/* PARAMS CHECKING */
	
	/* BUILDING VALUES / DATA STRUCTURES */
	/**
	 * Return the sum of the elements of a collection of integers.
	 * @param coll A Collection of integers.
	 * @return The sum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static int intSum(Collection<Integer> coll) {
		Common.collectionNotNull(coll);
		int s = 0;
		for (Integer i : coll) s += i;
		return s;
	}
	
	/**
	 * Return the sum of the elements of a collection of doubles.
	 * @param coll A Collection of doubles.
	 * @return The sum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static double doubleSum(Collection<Double> coll) {
		Common.collectionNotNull(coll);
		double s = 0.0;
		for (Double i : coll) s += i;
		return s;
	}
		
	/**
	 * Return the sum of the elements of a collection of longs.
	 * @param coll A Collection of longs.
	 * @return The sum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static long longSum(Collection<Long> coll) {
		Common.collectionNotNull(coll);
		long s = 0;
		for (Long i : coll) s += i;
		return s;
	}
	
	/**
	 * Return the maximum of the elements of a collection of integers.
	 * @param coll A Collection of integers.
	 * @return The maximum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static int max(Collection<Integer> coll) {
		Common.collectionNotNull(coll);
		boolean set = false;
		int result = 0;
		for (int val : coll) {
			if (!set) { result = val; set = true; }
			else result = Math.max(val, result);
		}
		return result;
	}
	
	/**
	 * Return the minimum of the elements of a collection of integers.
	 * @param coll A Collection of integers.
	 * @return The minimum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static int min(Collection<Integer> coll) {
		Common.collectionNotNull(coll);
		boolean set = false;
		int result = 0;
		for (int val : coll) {
			if (!set) { result = val; set = true; }
			else result = Math.min(val, result);
		}
		return result;
	}
	
	/**
	 * Transforms an integer into a byte array.
	 * @param num The num to transform.
	 * @return A byte array of {@link Integer#BYTES} items representing the encoded int.
	 */
	public static byte[] intToByteArray(final int num) {
		ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);
		buf.putInt(num);
		Common.allAndState(buf.array().length == Integer.BYTES);
		return buf.array();
	}
	
	public static int intsToByteArray(byte[] result, int startIndex, int... nums) {
		Common.allAndArgs(result != null, startIndex >= 0, startIndex + Integer.BYTES * nums.length <= result.length);
		for (int i = 0; i < nums.length; i++) {
			byte[] res = Common.intToByteArray(nums[i]);
			for (int j = 0; j < Integer.BYTES; j++) result[startIndex + j] = res[j];
			startIndex += Integer.BYTES;
		}
		return Integer.BYTES * nums.length;
	}
	
	/**
	 * Appends to the byte array result, starting from startIndex, a sequence of {@link Integer#BYTES} bytes representing
	 * the length of the byte array corresponding to the string, then the byte array of the string itself.
	 * @param result Byte array in which to write the encoded string.
	 * @param startIndex Starting index from which to write the encoded string.
	 * @param str String to be encoded.
	 * @return The number of bytes written.
	 */
	public static int lengthStringToByteArray(byte[] result, int startIndex, String str) {
		Common.allAndArgs(result != null, str != null, startIndex >= 0, startIndex + str.getBytes().length <= result.length);
		byte[] bstr = str.getBytes();
		int len = bstr.length;
		int res = Common.intsToByteArray(result, startIndex, len);
		for (int i = 0; i < len; i++) result[startIndex + res + i] = bstr[i];
		res += len;
		return res;
	}
	
	/**
	 * Decodes a byte array starting from the offset specified by startIndex as got by the {@link #intToByteArray(int)}
	 *  or {@link #intsToByteArray(byte[], int, int...)} into its corresponding integer.
	 * @param arr Source byte array.
	 * @param startIndex Offset from which to get the decoded integer.
	 * @return Decoded integer.
	 * @throws NullPointerException If arr == null.
	 * @throws IllegalArgumentException If startIndex < 0 or startIndex + {@link Integer#BYTES} > arr.length.
	 */
	public static Integer intFromByteArray(byte[] arr, int startIndex) {
		Common.notNull(arr);
		Common.allAndArgs(startIndex >= 0, startIndex + Integer.BYTES <= arr.length);
		int num = 0;
		byte[] aux = new byte[Integer.BYTES];
		for (int i = 0; i < Integer.BYTES; i++) aux[i] = arr[startIndex + i];
		ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);
		buf.put(arr, startIndex, Integer.BYTES);
		buf.flip();
		num = buf.getInt();
		return num;
	}
	
	public static Integer intFromByteArray(byte[] arr) { return intFromByteArray(arr, 0); }
	
	public static final String UNSUFF_LEN = "Number of byte(s) read is less than %d";
	
	/**
	 * Reads up to a specified number of bytes from the input stream. This method blocks until the requested
	 *  number of bytes have been read, end of stream is detected, or an exception is thrown. This method does
	 *  not close the input stream. The length of the returned array equals the number of bytes read from the
	 *  stream. If length is zero, then no bytes are read and an empty byte array is returned. Otherwise, up
	 *  to length bytes are read from the stream. Fewer than length bytes may be read if end of stream is encountered.
	 * @param in InputStream.
	 * @param length Target number of bytes to read.
	 * @param enforceLength If true, an IOException is thrown if less than length bytes are read from the stream.
	 * @return A byte array of at most length bytes (or exactly length bytes if enforceLength == true) on success.
	 * @throws IOException If an IO error occurs while reading from stream or, if enforceLength == true, fewer than
	 *  length bytes are read from stream.
	 */
	public static byte[] readNBytes(InputStream in, int length, boolean enforceLength) throws IOException {
		Common.allAndArgs(in != null, length >= 0);
		List<Byte> total = new ArrayList<>();
		int bread = 0, res;
		while (bread < length) {
			res = in.read();
			if (res == -1) break;
			else total.add((byte)res);
			bread++;
		}
		
		Common.allAndState(bread <= length);
		if (enforceLength && (bread != length)) throw new IOException( String.format(UNSUFF_LEN, length) );
		
		byte[] result = new byte[bread];
		for (int i = 0; i < bread; i++) result[i] = total.get(i).byteValue();
		return result;
	}
	
	/**
	 * The same as {@link #readNBytes(InputStream, int, boolean)} with enforceLength == true.
	 */
	public static byte[] readNBytes(InputStream in, int length) throws IOException
		{ return Common.readNBytes(in, length, true); }
	
	/**
	 * A method similar to {@link Arrays#asList(Object...)} but the returned list is modifiable,
	 * and with the option to append another list after the elements specified in head.
	 * @param <T> Type of the resulting list.
	 * @param tail Sublist to append after the elements of head.
	 * @param head Elements to convert to a list.
	 * @return A list made up by the elements in head, followed by tail.
	 */
	@SafeVarargs
	public static <T> List<T> toList(List<T> tail, T... head){
		List<T> list = new ArrayList<>();
		for (int i = 0; i < head.length; i++) list.add(head[i]);
		list.addAll(tail);
		return list;
	}
	
	/**
	 * A method similar to {@link Arrays#asList(Object...)} but the returned list is modifiable.
	 * @param <T> Type of the resulting list.
	 * @param head Elements to convert to a list.
	 * @return A list made up by the elements in head.
	 */
	@SafeVarargs
	public static <T> List<T> toList(T... head){
		List<T> list = new ArrayList<>();
		for (int i = 0; i < head.length; i++) list.add(head[i]);
		return list;
	}
	
	/**
	 * Creates a new ConcurrentHashMap from a couple of lists of the same length associating for each i = 1,...,length 
	 *  the i-th element of the first list to the i-th element of the second.
	 * @param <K> Type of ConcurrentMap keys.
	 * @param <V> Type of ConcurrentMap values.
	 * @param keys Keys of the Map.
	 * @param values Values of the Map.
	 * @return A ConcurrentHashMap as already described on success.
	 * @throws IllegalArgumentException If keys.size() != values.size().
	 */
	public static <K,V> ConcurrentMap<K, V> newConcurrentHashMapFromLists(List<K> keys, List<V> values){
		Common.allAndArgs(keys.size() == values.size());
		int size = keys.size();
		ConcurrentMap<K,V> map = new ConcurrentHashMap<>();
		for (int i = 0; i < size; i++) map.put(keys.get(i), values.get(i));
		return map;
	}
	
	/**
	 * Creates a new HashMap from a couple of lists of the same length associating for each i = 1,...,length 
	 *  the i-th element of the first list to the i-th element of the second.
	 * @param <K> Type of Map keys.
	 * @param <V> Type of Map values.
	 * @param keys Keys of the Map.
	 * @param values Values of the Map.
	 * @return A HashMap as already described on success.
	 * @throws IllegalArgumentException If keys.size() != values.size().
	 */
	public static <K,V> HashMap<K, V> newHashMapFromLists(List<K> keys, List<V> values){
		if (keys.size() != values.size()) return null;
		int size = keys.size();
		HashMap<K,V> map = new HashMap<>();
		for (int i = 0; i < size; i++) map.put(keys.get(i), values.get(i));
		return map;
	}
	
	/**
	 * Creates a new string by concatenating length copies of c, or an empty string if length == 0.
	 * @param length Length of the target string.
	 * @param c Character from which to get the output string.
	 * @return A new String of length copies of c if length > 0, or an empty string otherwise.
	 * @throws IllegalArgumentException If length < 0.
	 */
	public static String newCharSeq(int length, char c) {
		Common.allAndArgs(length >= 0);
		StringBuilder sb = new StringBuilder();
		sb.append("");
		for (int i = 0; i < length; i++) { sb.append(c); }
		return sb.toString();
	}
	/* BUILDING VALUES / DATA STRUCTURES */
	
	/* SLEEP */
	public static boolean sleep(long millis) {
		try { Thread.sleep(millis); return true; }
		catch (InterruptedException ie) { return false; }
	}
	/* SLEEP */
}