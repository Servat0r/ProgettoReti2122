package winsome.util;

public final class Pair<K,V> {

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
}
