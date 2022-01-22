package winsome.server.data;

import winsome.annotations.NotNull;
import winsome.util.Common;

public final class Rate implements Comparable<Rate> {
	
	@NotNull
	private final String author;
	private final long time;
	private final boolean like;
	
	private Rate(String author, boolean like) {
		Common.notNull(author);
		this.time = System.currentTimeMillis();
		this.author = author;
		this.like = like;
	}
	
	public static Rate newLike(String author) {return new Rate(author, true); }
	
	public static Rate newDislike(String author) { return new Rate(author, false); }

	public final String getAuthor() { return author; }

	public final long getTime() { return time; }
	
	public final boolean isLike() { return like; }
	
	public String toString() { return Common.jsonString(this); }
	
	public int compareTo(Rate other) {
		Common.notNull(other);
		int res = Long.compare(this.time, other.time);
		if (res != 0) return res;
		else {
			res = this.author.compareTo(other.author);
			if (res != 0) return res;
			else return Boolean.compare(this.like, other.like);
		}		
	}
	
}