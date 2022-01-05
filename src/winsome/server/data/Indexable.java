package winsome.server.data;

import winsome.annotations.NotNull;

public interface Indexable<T extends Comparable<T>> {

	@NotNull
	public T key();
	
}