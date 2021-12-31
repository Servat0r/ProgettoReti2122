package winsome.server.data;

public interface Indexable<T extends Comparable<T>> {

	public T key();
	
}