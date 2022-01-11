package winsome.server.data;

import winsome.annotations.NotNull;
import winsome.util.Common;

import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

/**
 * Comments in Winsome.
 * @author Salvatore Correnti
 */
public final class Comment implements Comparable<Comment> {
	
	public static final Type TYPE = new TypeToken<Comment>() {}.getType();
	
	@NotNull
	private final String idAuthor, content;
	@NotNull
	private final long idPost, time;
	
	/**
	 * Creates a new comment.
	 * @param idAuthor Name of the author of the comment.
	 * @param idPost Id of the post of the comment.
	 * @param content Content of comment.
	 */
	public Comment(String idAuthor, long idPost, String content) {
		Common.notNull(idAuthor, content);
		Common.allAndArgs(idPost > 0, content.length() > 0);
		this.idAuthor = idAuthor;
		this.idPost = idPost;
		this.content = content;
		this.time = System.currentTimeMillis();
	}
	
	public int compareTo(Comment cmm) {
		long curr = time - cmm.time;
		if (curr < 0) return -1;
		else if (curr > 0) return 1;
		else {
			int a = this.idAuthor.compareTo(cmm.idAuthor);
			if (a != 0) return a;
			else {
				curr = idPost - cmm.idPost;
				if (curr < 0) return -1;
				else if (curr > 0) return 1;
				else return this.content.compareTo(cmm.content);
			}
		}
	}
	
	public String getIdAuthor() { return idAuthor; }
	public long getIdPost() { return idPost; }
	public String getContent() { return content; }
	public long getTime() { return time; }
	
	public String toString() { return Common.jsonString(this); }
}