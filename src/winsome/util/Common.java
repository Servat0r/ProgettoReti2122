package winsome.util;

import java.util.*;
import java.util.concurrent.*;

/**
 * Operazioni e metodi comuni.
 * @author Salvatore Correnti.
 */
public final class Common {
	
	private Common() {}
	
	public static void notNull(Object obj) { if (obj == null) throw new NullPointerException(); }
	
	public static void positive(int n) { if (n <= 0) throw new IllegalArgumentException(); }
	
	public static void notNeg(int n) {if (n < 0) throw new IllegalArgumentException(); }
	
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
	
	public static Integer intFromByteArray(byte[] arr) {
		if (arr.length != 4) return null;
		int num = 0;
		num += ( ((int)arr[0]) << 24);
		num += ( ((int)arr[1]) << 16);
		num += ( ((int)arr[2]) << 8);
		num += ( ((int)arr[3]) << 0);
		return num;
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
}