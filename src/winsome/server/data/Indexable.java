package winsome.server.data;

import winsome.annotations.NotNull;

/**
 * An interface for an object that can be identified uniquely by a not null "key" (i.e., like a primary
 *  key in a database table).
 * @author Salvatore Correnti
 *
 * @param <T> Type of the key.
 */
public interface Indexable<T extends Comparable<T>> {
	
	@NotNull
	public T key();
	
}