package winsome.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;


/**
 * Common tools and operations.
 * @author Salvatore Correnti.
 */
public final class Common {
	
	private Common() {}
	
	/* THREADS */
	public static boolean sleep(long millis) {
		try { Thread.sleep(millis); return true; } 
		catch (InterruptedException ex) { return false; }
	}
	
	public static boolean sleep(long millis, int nanos) {
		try { Thread.sleep(millis, nanos); return true; }
		catch (InterruptedException ex) { return false; }
	}
	/* THREADS */
	
	/* QUOTING */
	public static String quote(String str) {
		Common.notNull(str);
		return new String("\"" + str + "\"");
	}
	
	public static String dequote(String str) {
		Common.notNull(str);
		if (str.startsWith("\"") && str.endsWith("\"")) return str.substring(1, str.length()-1);
		else return new String(str);
	}
	/* QUOTING */
	
	
	/* PRINTING */	
	public static void printfln(PrintStream stream, String format, Object ...objs) {
		Common.notNull(format);
		String msg = String.format(format, objs);
		stream.println(msg);
	}
	
	public static void printfln(String format, Object ...objs) { printfln(System.out, format, objs); }
	
	public static void printfErrln(String format, Object ...objs) { printfln(System.err, format, objs); }
	
	public static String jsonString(Object obj) {
		Common.notNull(obj);
		String cname = obj.getClass().getSimpleName(), jsond = Serialization.GSON.toJson(obj, obj.getClass());
		return String.format("%s: %s", cname, jsond);
	}
	/* PRINTING */
	
	
	/* EXCEPTIONS MESSAGES FORMATTING */
	public static String excStr(String format, Object ...objs) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		String msg = String.format(format, objs);
		return (fname + Debug.DBGSEPAR + msg);
	}
	
	public static boolean isConnReset(IOException ioe) {
		Common.notNull(ioe);
		return ioe.getMessage().contains("Connection reset by peer");
	}
	/* EXCEPTIONS MESSAGES FORMATTING */
	
	/* PARAMS CHECKING */
	private static final String EXCSTR = "Condition #%d is NOT satisfied!";
	
	public static void notNull(String msg, Object ...objs) {
		for (int i = 0; i < objs.length; i++) if (objs[i] == null)
			throw new NullPointerException( excStr("arg #%d%s%s", i+1, Debug.DBGSEPAR, msg) );
	}
	
	public static void notNull(Object ...objs) { notNull("is null!", objs); }
	
	public static <T> void collectionNotNull(Collection<T> coll) {
		Common.notNull(coll);
		for (T elem : coll) Common.notNull(elem);
	}
	
	public static void positive(int ...nums) {
		for (int n : nums) if (n <= 0) throw new IllegalArgumentException();
	}
	
	public static void notNeg(int ...nums){
		for (int n : nums) if (n < 0) throw new IllegalArgumentException();
	}
		
	public static void andAllArgs(boolean ...conds) {
		for (int i = 0; i < conds.length; i++)
			if (!conds[i]) throw new IllegalArgumentException(Common.excStr(EXCSTR, i+1));
	}
	
	public static void allAndState(boolean ...conds) {
		for (int i = 0; i < conds.length; i++)
			if (!conds[i]) throw new IllegalStateException(Common.excStr(EXCSTR, i+1));
	}
	
	public static void orAll(boolean ...conds) {
		for (int i = 0; i < conds.length; i++) if (conds[i]) return;
		throw new IllegalArgumentException(Common.excStr("No condition satisfied"));
	}
	
	public static boolean andAll(Object obj, boolean ...conds) {
		for (boolean cond : conds) if (!cond) return false;
		return true;
	}
	/* PARAMS CHECKING */
	
	/* BUILDING VALUES / DATA STRUCTURES */
	public static Integer[] newIntegerArray(int length, int defValue) {
		if (length <= 0) throw new IllegalArgumentException(); 
		Integer[] result = new Integer[length];
		for (int i = 0; i < length; i++) result[i] = defValue;
		return result;
	}
	
	public static int intSum(Collection<Integer> coll) {
		Common.collectionNotNull(coll);
		int s = 0;
		for (Integer i : coll) s += i;
		return s;
	}
	
	public static double doubleSum(Collection<Double> coll) {
		Common.collectionNotNull(coll);
		double s = 0.0;
		for (Double i : coll) s += i;
		return s;
	}
		
	public static long longSum(Collection<Long> coll) {
		Common.collectionNotNull(coll);
		long s = 0;
		for (Long i : coll) s += i;
		return s;
	}
	
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
	
	public static <K> int intMapSum(Map<K, Integer> map){
		int s = 0;
		synchronized (map) { for (Integer v : map.values()) s += v; }
		return s;
	}
	
	public static byte[] intToByteArray(final int num) {
		ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);
		buf.putInt(num);
		Common.allAndState(buf.array().length == Integer.BYTES);
		return buf.array();
	}
	
	public static int intsToByteArray(byte[] result, int startIndex, int... nums) {
		Common.andAllArgs(result != null, startIndex >= 0, startIndex + Integer.BYTES * nums.length <= result.length);
		for (int i = 0; i < nums.length; i++) {
			byte[] res = Common.intToByteArray(nums[i]);
			for (int j = 0; j < Integer.BYTES; j++) result[startIndex + j] = res[j];
			startIndex += Integer.BYTES;
		}
		return Integer.BYTES * nums.length;
	}
	
	public static int lengthStringToByteArray(byte[] result, int startIndex, String str) {
		Common.andAllArgs(result != null, str != null, startIndex >= 0, startIndex + str.getBytes().length <= result.length);
		byte[] bstr = str.getBytes();
		int len = bstr.length;
		int res = Common.intsToByteArray(result, startIndex, len);
		for (int i = 0; i < len; i++) result[startIndex + res + i] = bstr[i];
		res += len;
		return res;
	}
	
	
	public static Integer intFromByteArray(byte[] arr, int startIndex) {
		Common.notNull(arr); Common.notNeg(startIndex);
		if (startIndex + 3 >= arr.length) throw new IllegalArgumentException();
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
	
	public static byte[] readNBytes(InputStream in, int length) throws IOException {
		Common.andAllArgs(in != null, length >= 0);
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
	
	@SafeVarargs
	public static <T> List<T> toList(List<T> tail, T... head){
		List<T> list = new ArrayList<>();
		for (int i = 0; i < head.length; i++) list.add(head[i]);
		list.addAll(tail);
		return list;
	}
	
	@SafeVarargs
	public static <T> List<T> toList(T... head){
		List<T> list = new ArrayList<>();
		for (int i = 0; i < head.length; i++) list.add(head[i]);
		return list;
	}
	
	/**
	 * Crea una nuova ConcurrentHashMap da una coppia di array della stessa lunghezza associando per ogni i l'i-esimo elemento 
	 * del primo array all'i-esimo elemento del secondo.
	 * @param <K> Tipo delle chiavi della HashMap.
	 * @param <V> Tipo dei valori della HashMap.
	 * @param keys Chiavi della HashMap.
	 * @param values Valori della HashMap.
	 * @return Una HashMap come già descritta in caso di successo, null altrimenti.
	 */
	public static <K,V> ConcurrentMap<K, V> newConcurrentHashMapFromLists(List<K> keys, List<V> values){
		if (keys.size() != values.size()) return null;
		int size = keys.size();
		ConcurrentMap<K,V> map = new ConcurrentHashMap<>();
		for (int i = 0; i < size; i++) map.put(keys.get(i), values.get(i));
		return map;
	}
	
	public static <K,V> HashMap<K, V> newHashMapFromLists(List<K> keys, List<V> values){
		if (keys.size() != values.size()) return null;
		int size = keys.size();
		HashMap<K,V> map = new HashMap<>();
		for (int i = 0; i < size; i++) map.put(keys.get(i), values.get(i));
		return map;
	}
	
	public static <T> HashSet<T> newHashSetFromArray(List<T> values){
		HashSet<T> set = new HashSet<>();
		for (int i = 0; i < values.size(); i++) set.add(values.get(i));
		return set;
	}
	
	public static String newCharSeq(int length, char c) {
		Common.notNeg(length);
		StringBuilder sb = new StringBuilder();
		sb.append("");
		for (int i = 0; i < length; i++) { sb.append(c); }
		return sb.toString();
	}
	/* BUILDING VALUES / DATA STRUCTURES */
}