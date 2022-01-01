package winsome.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;


/**
 * Common tools and operations.
 * @author Salvatore Correnti.
 */
public final class Common {
	
	private Common() {}
	
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
	public static void printLn(Object obj) { System.out.println(obj); }
	public static void printLn() { System.out.println(); }
	public static void print(Object obj) { System.out.print(obj); }
	public static void printf(String format, Object... args) { System.out.printf(format, args); }
	public static void printErrLn(Object obj) { System.err.println(obj); }
	public static void printErrLn() { System.err.println(); }
	public static void printErr(Object obj) { System.err.print(obj); }
	public static void printfErr(String format, Object... args) { System.err.printf(format, args); }
	/* PRINTING */
	
	/* DEBUGGING */
	private static final String DBGSEPAR = ": ";
	private static PrintStream dbgStream = System.out;
	private static final String DEBUGSTR = "DEBUG" + DBGSEPAR;
	public static final String DEBUGPROP = "debug"; /* System property for enabling debugging. */
	public static final String DEBUGFILE = "debug.txt"; /* Default debug file. */
	
	/**
	 * Enables debug printings.
	 */
	public static void setDebug() { System.setProperty(DEBUGPROP, "true"); }
	
	/**
	 * Disables debug printings.
	 */
	public static void resetDebug() { System.setProperty(DEBUGPROP, "false"); }
	
	/**
	 * Clears debug property.
	 */
	public static void clearDebug() { System.clearProperty(DEBUGPROP); }
	
	/**
	 * Sets debug file to the specified filename.
	 * @param filename (Relative) path of the file.
	 */
	public static synchronized void setDbgFile(String filename) {
		Common.notNull(filename);
		try { dbgStream = new PrintStream(filename); }
		catch (Exception ex) { dbgStream = System.out; }
	}
	
	/**
	 * Sets debug file to default value.
	 */
	public static synchronized void setDbgFile() { setDbgFile(DEBUGFILE); }
	
	/**
	 * Sets debug printing to default stream (System.out).
	 */
	public static synchronized void resetDgbStream() { dbgStream = System.out; }
	
	/**
	 * Prints a line of the form "DEBUG: 'fname' : [obj.toString()]".
	 * @param fname Message of the debug line.
	 * @param obj Object to debug (prints 'null' if null, otherwise invokes obj.toString()).
	 */
	public static void debugln(String fname, Object obj) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true"))
			dbgStream.println( DEBUGSTR + fname + DBGSEPAR + (obj != null ? obj.toString() : "null") );
	}
	
	/**
	 * Debug printing indicating the invoking function and the current source code line.
	 * @param obj Object to debug (prints 'null' if null, otherwise invokes obj.toString()).
	 */
	public static void debugln(Object obj) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		int fline = Thread.currentThread().getStackTrace()[2].getLineNumber();
		Common.debugln(fname + (fline >= 0 ? " at line " + fline : ""), obj);
	}
	
	public static void debugf(String fname, String format, Object... objs) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true")) dbgStream.printf(DEBUGSTR + fname + DBGSEPAR + format, objs);
	}
	
	public static void debugf(String format, Object... objs) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		int fline = Thread.currentThread().getStackTrace()[2].getLineNumber();
		Common.debugf(fname + (fline >= 0 ? " at line" + fline : ""), format, objs);
	}
	
	public static void debugExc(Exception ex) { ex.printStackTrace(dbgStream); }
	
	public static void exit(int code) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true") && (dbgStream != System.out)) { dbgStream.close(); dbgStream = System.out; }
		System.exit(code);
	}
	/* DEBUGGING */
	
	/* EXCEPTIONS MESSAGES FORMATTING */
	public static String excStr(String msg) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		return (fname + DBGSEPAR + msg);
	}
	
	public static boolean isConnReset(IOException ioe) {
		Common.notNull(ioe);
		return ioe.getMessage().contains("Connection reset");
	}
	/* EXCEPTIONS MESSAGES FORMATTING */
	
	/* PARAMS CHECKING */
	public static void notNull(String msg, Object ...objs) {
		for (int i = 0; i < objs.length; i++) if (objs[i] == null)
			throw new NullPointerException( excStr("arg #" + (i+1) + DBGSEPAR + msg) );
	}
	
	public static void notNull(Object ...objs) { notNull("is null!", objs); }
	
	public static void positive(int ...nums) {
		for (int n : nums) if (n <= 0) throw new IllegalArgumentException();
	}
	
	public static void notNeg(int ...nums){
		for (int n : nums) if (n < 0) throw new IllegalArgumentException();
	}
	
	public static void andAllArgs(boolean ...conds) {
		for (int i = 0; i < conds.length; i++)
			if (!conds[i]) throw new IllegalArgumentException(Common.excStr("Condition #" + (i+1) + " NOT satisfied!"));
	}
	
	public static void allAndState(boolean ...conds) {
		for (int i = 0; i < conds.length; i++)
			if (!conds[i]) throw new IllegalStateException();
	}
	
	public static void orAll(boolean ...conds) {
		for (int i = 0; i < conds.length; i++) if (conds[i]) return;
		throw new IllegalArgumentException(Common.excStr("No condition satisfied"));
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
		int s = 0;
		for (Integer i : coll) s += i;
		return s;
	}
	
	public static double doubleSum(Collection<Double> coll) {
		double s = 0.0;
		for (Double i : coll) s += i;
		return s;
	}
	
	public static int max(Collection<Integer> coll) {
		boolean set = false;
		int result = 0;
		for (int val : coll) {
			if (!set) { result = val; set = true; }
			else result = Math.max(val, result);
		}
		return result;
	}
	
	public static <K> int sum(Map<K, Integer> map){
		int s = 0;
		synchronized (map) { for (Integer v : map.values()) s += v; }
		return s;
	}
	
	public static byte[] intToByteArray(final int num) {
		return new byte[] {
			(byte) ((num >> 24) & 0xff),
			(byte) ((num >> 16) & 0xff),
			(byte) ((num >> 8) & 0xff),
			(byte) ((num >> 0) & 0xff)
		};
	}
	
	public static Integer intFromByteArray(byte[] arr, int startIndex) {
		Common.notNull(arr); Common.notNeg(startIndex);
		if (startIndex + 3 >= arr.length) throw new IllegalArgumentException();
		int num = 0;
		num += ( ((int)arr[startIndex]) << 24);
		num += ( ((int)arr[startIndex + 1]) << 16);
		num += ( ((int)arr[startIndex + 2]) << 8);
		num += ( ((int)arr[startIndex + 3]) << 0);
		return num;
	}
	
	public static Integer intFromByteArray(byte[] arr) { return intFromByteArray(arr, 0); }
	
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
		for (int i = 0; i < length; i++) { sb.append(c); }
		return sb.toString();
	}
	/* BUILDING VALUES / DATA STRUCTURES */
}