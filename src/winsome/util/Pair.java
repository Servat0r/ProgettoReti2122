package winsome.util;

import java.io.Serializable;
import java.util.Objects;

public final class Pair<K,V> implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private K key;
	private V value;
	
	public Pair(K key, V value) {
		this.key = key;
		this.value = value;
	}

	public final K getKey() {
		return key;
	}

	public final void setKey(K key) {
		this.key = key;
	}

	public final V getValue() {
		return value;
	}

	public final void setValue(V value) {
		this.value = value;
	}

	public int hashCode() { return Objects.hash(key); }

}