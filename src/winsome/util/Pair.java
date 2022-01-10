package winsome.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * A pair of objects.
 * @author Salvatore Correnti
 *
 * @param <K> Type of the first object.
 * @param <V> Type of the second object.
 */
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
	
	public int hashCode() { return Objects.hash(key, value); }
	
	@SuppressWarnings("unchecked")
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Pair<K,V> other = (Pair<K,V>) obj;
		return Objects.equals(key, other.key) && Objects.equals(value, other.value);
	}	
}