package winsome.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.io.*;

public final class ConfigMap<T> implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private final ConcurrentMap<String, T> map = new ConcurrentHashMap<>();
	
	public ConfigMap() {}
	
	public ConcurrentMap<String, T> map(){ return map; }
	
	public boolean isSet(String property) {
		Common.notNull(property);
		return map.containsKey(property);
	}
	
	public ConfigMap<T> set(String prop, T value) {
		Common.notNull(prop, value);
		map.put(prop, value);
		return this;
	}
	
	public ConfigMap<T> setIfUnset(String prop, T value) {
		Common.notNull(prop, value);
		map.putIfAbsent(prop, value);
		return this;
	}
	
	public ConfigMap<T> unset(String prop) {
		Common.notNull(prop);
		map.remove(prop);
		return this;
	}
	
	public T get(String prop) {
		Common.notNull(prop);
		return map.get(prop);
	}
	
	public ConfigMap<T> getSubset(String...props){
		String searchStr = CollectionsUtils.strReduction(Arrays.asList(props), ".", "", "", str -> str);
		ConfigMap<T> res = new ConfigMap<>();
		map.forEach( (str, val) -> { if (str.startsWith(searchStr)) res.set(str.substring(searchStr.length()), val); } );
		res.map().forEach((str, val) -> {
			if (str.startsWith(".")) { res.unset(str); res.set(str.substring(1), val); }
		});
		return res;
	}
	
	public <Cstr extends Collection<String>> void keys(Cstr coll) {
		CollectionsUtils.remap(map.keySet(), coll, t -> t);
	}
	
	public Set<String> keys(){ return map.keySet(); }
	
	public <Ct extends Collection<T>> void values(Ct coll) {
		CollectionsUtils.remap(map.values(), coll, t -> t);
	}
	
	public Collection<T> values(){ return map.values(); }
	
	public <V> V getValueOrDefault(String key, Function<T, V> fun, V defValue) {
		Common.notNull(key, fun);
		synchronized (map) {
			T setting = map.get(key);
			return (setting != null ? fun.apply(setting) : defValue);
		}
	}
}