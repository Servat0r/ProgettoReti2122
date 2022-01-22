package winsome.util;

import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.*;
import java.util.regex.*;

import com.google.gson.Gson;
import com.google.gson.stream.*;

import winsome.annotations.NotNull;


/**
 * Common tools and operations.
 * @author Salvatore Correnti.
 */
public final class Common {
	
	public static final String SEPAR = ": ";
	
	private Common() {}
	
	public static void printParams(String className, String methodName) throws ClassNotFoundException {
		List<Method> methods = Arrays.asList(Class.forName(className).getDeclaredMethods());
		List<String> res = new ArrayList<>();
		for (Method m : methods) {
			if (m.getName().equals(methodName)) {
				Arrays.asList(m.getParameters()).forEach( param -> res.add(param.getName() + ": " + param.getType()) );
			}
		}
		String str = CollectionsUtils.strReduction(res, "\n", "{\n", "\n}", s -> s);
		System.out.println(str);
	}
	
	public static void printParams(Class<?> cl, String methodName) throws ClassNotFoundException
		{ printParams(cl.getCanonicalName(), methodName); }
	
	public static void genericDesc(Object obj, List<String> objParams, Map<String, Type> fieldParams){
		Common.notNull(obj, objParams, fieldParams);
		Class<?> cl = obj.getClass();
		
		objParams.add(cl.toGenericString());
		for (TypeVariable<?> tv : cl.getTypeParameters()) objParams.add(tv.getTypeName());
		
		for (Field field : cl.getDeclaredFields()) fieldParams.put(field.getName(), field.getGenericType());
	}
	
	/**
	 * Factory of regex matching functions.
	 * @param regex Pattern to match.
	 * @return A function of the form String -> Integer that tries to match
	 *  its input string with the regex pattern. 
	 */
	@NotNull
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
		if (obj == null) return "null";
		String cname = obj.getClass().getSimpleName(), jsond = Serialization.GSON.toJson(obj, obj.getClass());
		return String.format("%s: %s", cname, jsond);
	}
	
	@NotNull
	public static <T> String toString(T obj, Gson gson, BiFunction<JsonWriter, T, Exception> jsonSerializer, Lock...locks) {
		if (obj == null) return "null";
		Common.notNull(jsonSerializer);
		boolean lock = true;
		for (int i = 0; i < locks.length; i++) if (locks[i] == null) lock = false; 
		StringWriter str = new StringWriter();
		try ( JsonWriter writer = gson.newJsonWriter(str) ){
			if (lock) for (int i = 0; i < locks.length; i++) locks[i].lock();
			Exception ex = jsonSerializer.apply(writer, obj);
			if (ex != null) throw ex;
			else return String.format("%s: %s", obj.getClass().getSimpleName(), str.toString());
		} catch (Exception ex) { return Common.jsonString(obj); }
		finally { if (lock) for (int i = locks.length-1; i >= 0; i--) locks[i].unlock(); }
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
		return (fname + SEPAR + msg);
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
		for (int i = 0; i < objs.length; i++) {
			if (objs[i] == null) {
				throw new NullPointerException( excStr("arg%d%s%s", i, SEPAR, msg) );
			}
		}
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
	
	public static double round(double d, int c) {
		String fmt = "%." + c + "f";
		return Double.parseDouble( String.format(fmt, d).replace(",", ".") );
	}
	
	public static final String encodeDate(Calendar c) {
		Common.notNull(c);
		String fmt = "%d/%d/%d %d:%d:%d:%d"; //day/month/year hours:minutes:seconds:milliseconds
		int
			year = c.get(Calendar.YEAR),
			month = c.get(Calendar.MONTH),
			day = c.get(Calendar.DAY_OF_MONTH),
			hours = c.get(Calendar.HOUR_OF_DAY),
			minutes = c.get(Calendar.MINUTE),
			seconds = c.get(Calendar.SECOND),
			millis = c.get(Calendar.MILLISECOND);
		return String.format(fmt, day, month, year, hours, minutes, seconds, millis);
	}
	
	public static final Calendar decodeDate(String str) {
		Common.notNull(str);
		String[] dateHour = str.split(" ");
		Common.allAndArgs(dateHour.length == 2);
		String[] dateData = dateHour[0].split("/");
		String[] hourData = dateHour[1].split(":");
		Common.allAndArgs(dateData.length == 3, hourData.length == 4);
		int
			year = Integer.parseInt(dateData[2]),
			month = Integer.parseInt(dateData[1]),
			day = Integer.parseInt(dateData[0]),
			hours = Integer.parseInt(hourData[0]),
			minutes = Integer.parseInt(hourData[1]),
			seconds = Integer.parseInt(hourData[2]),
			millis = Integer.parseInt(hourData[3]);
		Calendar c = Calendar.getInstance();
		c.set(year, month, day, hours, minutes, seconds);
		c.set(Calendar.MILLISECOND, millis);
		return c;
	}
	
	/**
	 * Return the sum of the elements of a collection of integers.
	 * @param coll A Collection of integers.
	 * @return The sum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static int intSum(Collection<Integer> coll) {
		Common.collectionNotNull(coll);
		return CollectionsUtils.collAccumulate(coll, (a,b) -> a+b, 0);
	}
	
	/**
	 * Return the sum of the elements of a collection of doubles.
	 * @param coll A Collection of doubles.
	 * @return The sum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static double doubleSum(Collection<Double> coll) {
		Common.collectionNotNull(coll);
		return CollectionsUtils.collAccumulate(coll, (a,b) -> a+b, 0.0);
	}
		
	/**
	 * Return the sum of the elements of a collection of longs.
	 * @param coll A Collection of longs.
	 * @return The sum of the element in coll.
	 * @throws NullPointerException if coll == null or contains a null element.
	 */
	public static long longSum(Collection<Long> coll) {
		Common.collectionNotNull(coll);
		return CollectionsUtils.collAccumulate(coll, (a,b) -> a+b, 0L);
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
	
	
	public static String printFields(Object obj) {
		Common.notNull(obj);
		List<Field> fields = Arrays.asList(obj.getClass().getDeclaredFields());
		fields.forEach(f -> f.setAccessible(true));
		List<String> datas = new ArrayList<>();
		
		CollectionsUtils.remap(fields, datas, f -> {
			try {
				String format = "%s : %s : '%s'", internal;
				if (f.get(obj) == null) internal = "null";
				else if ( f.get(obj).getClass().isArray() ) {
					Object[] o = (Object[])f.get(obj);
					List<Object> l = new ArrayList<>();
					for (Object item : o) l.add(item);
					internal = CollectionsUtils.strReduction(l, ", ", "[", "]", Objects::toString);
				} else internal = Objects.toString(f.get(obj));
				return String.format(format, f.getName(), f.getType().getSimpleName(), internal);
			} catch (IllegalArgumentException | IllegalAccessException e) { e.printStackTrace(); return null; }
		});
		
		fields.forEach(f -> f.setAccessible(false));
		
		return CollectionsUtils.strReduction(datas, "; ", "{", "}", Objects::toString);
	}
	
	public static String printFieldsNames(Object obj) {
		Common.notNull(obj);
		List<Field> fields = Arrays.asList(obj.getClass().getDeclaredFields());
		List<String> datas = new ArrayList<>();
		CollectionsUtils.remap( fields, datas, f -> f.getName() + " : " + f.getType().getSimpleName() );		
		return CollectionsUtils.strReduction(datas, "; ", "{", "}", Objects::toString);
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