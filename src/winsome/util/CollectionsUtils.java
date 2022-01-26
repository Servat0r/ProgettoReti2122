package winsome.util;

import java.io.PrintStream;
import java.util.*;
import java.util.function.*;

public final class CollectionsUtils {
	
	public static final String
		DFLSTRINIT	= "{",
		DFLSTREND	= "}",
		DFLSTRSEPAR	= " ";
	
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
	public static <K,V> Map<K, V> newHashMapFromCollections(Collection<K> keys, Collection<V> values){
		if (keys.size() != values.size()) return null;
		int size = keys.size();
		Iterator<K> keysIter = keys.iterator();
		Iterator<V> valsIter = values.iterator();
		Map<K,V> map = new HashMap<>();
		for (int i = 0; i < size; i++) map.put( keysIter.next(), valsIter.next() );
		return map;
	}
	
	public static <K,V> void fillMap(Map<K,V> map, Collection<K> keys, Collection<V> values) {
		Common.notNull(map, keys, values);
		Common.allAndArgs(keys.size() == values.size());
		int size = keys.size();
		Iterator<K> keysIter = keys.iterator();
		Iterator<V> valsIter = values.iterator();
		for (int i = 0; i < size; i++) map.put( keysIter.next(), valsIter.next() );
	}

	public static <P, Q, R> void merge(Collection<R> target, Collection<P> ps, Collection<Q> qs, BiFunction<P,Q,R> merger){
		Common.notNull(target, ps, qs);
		Common.allAndArgs(ps.size() == qs.size());
		Iterator<P> piter = ps.iterator();
		Iterator<Q> qiter = qs.iterator();
		int size = ps.size();
		for (int i = 0; i < size; i++) target.add( merger.apply(piter.next(), qiter.next()) );
	}
	
	public static <T> T collAccumulate(Collection<T> coll, Accumulator<T> accumulator, T initialValue) {
		Common.notNull(coll, accumulator);
		T result = initialValue;
		for (T elem : coll) result = (result != null ? accumulator.apply(result, elem) : elem);
		return result;
	}

	public static <T> String strReduction(Collection<T> items, final String separ, final String start,
			final String end, Function<T, String> transf) {
		final String sep = ( separ != null ? separ : DFLSTRSEPAR);
		Collection<String> strings = new ArrayList<>();
		CollectionsUtils.remap(items, strings, transf);
		StringBuilder sb = new StringBuilder();
		sb.append(start);
		sb.append( collAccumulate(strings, (res, next) -> res + sep + next, null) );
		sb.append(end);
		return sb.toString();
	}
	
	public static <T> String strReduction(Collection<T> items, final String separ, Function<T, String> transf) {
	return strReduction(items, separ, DFLSTRINIT, DFLSTREND, transf); }
	
	public static <T> String strReduction(Collection<T> items) { return strReduction(items, null, Objects::toString); }
	
	public static <P, Q, Cp extends Collection<P>, Cq extends Collection<Q>> void remap(Cp src, Cq dest, Function<P, Q> transf) {
		Common.notNull(src, dest, transf);
		src.forEach( p -> dest.add(transf.apply(p)) );
	}
	
	public static <A, B, C, Cc extends Collection<C>> C mapCollReduce(Map<A, B> map, Cc coll, BiFunction<A,B,C> transf,
			Function<Collection<C>, C> reducer) {
		Common.notNull(map, coll, transf, reducer);
		map.forEach( (a,b) -> coll.add(transf.apply(a, b)) );
		return reducer.apply(coll);
	}
	
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
	
	public static <T> List<T> arrToList(T[] arr){
		Common.notNull(arr);
		List<T> list = new ArrayList<>();
		for (T elem : arr) list.add(elem);
		return list;
	}
	
	public static <T> void arrToColl(Collection<T> coll, T[] arr) {
		Common.notNull(coll, arr);
		for (T elem : arr) coll.add(elem);
	}
	
	public static <T> void printCollection(Collection<T> coll, String separ, String start, String end,
		Function<T, String> transf, PrintStream stream) {
		String str = strReduction(coll, separ, start, end, transf);
		stream.println(str);
	}
	
	public static <T> void printCollection(Collection<T> coll, String separ, String start, String end,
		Function<T, String> transf) { printCollection(coll, separ, start, end, transf, System.out); }
	
	public static <T> void printArray(T[] arr, String separ, String start, String end,
		Function<T, String> transf, PrintStream stream) {
		List<T> l = arrToList(arr);
		printCollection(l, separ, start, end, transf, stream);
	}
	
	public static <T> void printArray(T[] arr, String separ, String start, String end,
		Function<T, String> transf) { printArray(arr, separ, start, end, transf, System.out); }
	
}