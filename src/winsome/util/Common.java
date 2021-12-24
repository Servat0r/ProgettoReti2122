package winsome.util;

import java.util.*;
import java.util.concurrent.*;

/**
 * Operazioni e metodi comuni.
 * @author Salvatore Correnti.
 */
public final class Common {
	
	private Common() {}

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
	private static final String DEBUGSTR = "DEBUG : ";
	public static final String DEBUGPROP = "debugMode";
	
	public static void setDebug() { System.setProperty(DEBUGPROP, "true"); }
	public static void resetDebug() { System.setProperty(DEBUGPROP, "false"); }
	public static void clearDebug() { System.clearProperty(DEBUGPROP); }
	
	public static void debugln(String fname, Object obj) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true")) Common.printLn(DEBUGSTR + fname + ": " + obj.toString());
	}
	
	public static void debugln(Object obj) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		Common.debugln(fname, obj);
	}
		
	public static void debugf(String fname, String format, Object... objs) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true")) Common.printf(DEBUGSTR + fname + ": " + format, objs);
	}
	
	public static void debugf(String format, Object... objs) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		Common.debugf(fname, format, objs);
	}
	/* DEBUGGING */
	
	/* PARAMS CHECKING */
	public static void notNull(Object ...objs) {
		for (Object obj : objs) if (obj == null) throw new NullPointerException();
	}
	
	public static void positive(int ...nums) {
		for (int n : nums) if (n <= 0) throw new IllegalArgumentException();
	}
	
	public static void notNeg(int ...nums){
		for (int n : nums) if (n < 0) throw new IllegalArgumentException();
	}
	
	public static void checkAll(boolean ...conds) {
		for (int i = 0; i < conds.length; i++) if (!conds[i]) throw new IllegalArgumentException("Condition #" + (i+1) +
				" NOT satisfied!");
	}
	/* PARAMS CHECKING */
	
	/* BUILDING VALUES / DATA STRUCTURES */
	public static Integer[] newIntegerArray(int length, int defValue) {
		if (length <= 0) throw new IllegalArgumentException(); 
		Integer[] result = new Integer[length];
		for (int i = 0; i < length; i++) result[i] = defValue;
		return result;
	}
	
	public static int sum(Collection<Integer> coll) {
		int s = 0;
		synchronized (coll) { for (Integer i : coll) s += i; }
		return s;
	}
	
	public static <K> int sum(ConcurrentMap<K, Integer> map){
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
	public static <K,V> ConcurrentMap<K, V> newConcurrentHashMapFromArrays(K[] keys, V[] values){
		if (keys.length != values.length) return null;
		int size = keys.length;
		ConcurrentMap<K,V> map = new ConcurrentHashMap<>();
		for (int i = 0; i < size; i++) map.put(keys[i], values[i]);
		return map;
	}
	
	public static <K,V> HashMap<K, V> newHashMapFromArrays(K[] keys, V[] values){
		if (keys.length != values.length) return null;
		int length = keys.length;
		HashMap<K,V> map = new HashMap<>();
		for (int i = 0; i < length; i++) map.put(keys[i], values[i]);
		return map;
	}
	
	public static <T> HashSet<T> newHashSetFromArray(T[] values){
		HashSet<T> set = new HashSet<>();
		for (int i = 0; i < values.length; i++) set.add(values[i]);
		return set;
	}
	/* BUILDING VALUES / DATA STRUCTURES */	
}