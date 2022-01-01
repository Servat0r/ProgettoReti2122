package winsome.server.data;

import winsome.util.Common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

public final class Comment implements Comparable<Comment> {

	public static final Type TYPE = new TypeToken<Comment>() {}.getType();
	
	private final String idAuthor;
	private final long idPost;
	private final String content;
	private final long time;
	
	public Comment(String idAuthor, long idPost, String content) {
		Common.notNull(idAuthor, content);
		Common.andAllArgs(idPost > 0, content.length() > 0);
		this.idAuthor = idAuthor;
		this.idPost = idPost;
		this.content = content;
		this.time = System.currentTimeMillis();
	}
	
	public int compareTo(Comment cmm) {
		int a = this.idAuthor.compareTo(cmm.idAuthor);
		int b = Long.valueOf(this.idPost).compareTo(cmm.idPost);
		int c = Long.valueOf(this.time).compareTo(cmm.time);
		int d = this.content.compareTo(cmm.content);
		if (a != 0) return a;
		else if (b != 0) return b;
		else if (c != 0) return c;
		else if (d != 0) return d;
		else return 0;
	}
	
	public String getIdAuthor() { return idAuthor; }
	public long getIdPost() { return idPost; }
	public String getContent() { return content; }
	public long getTime() { return time; }

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append( this.getClass().getSimpleName() + " [");
		try {
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					sb.append( (first ? ", " : "") + f.getName() + " = " + f.get(this) );
					if (!first) first = true;
				}
			}
			sb.append("]");
			return sb.toString();
		} catch (IllegalAccessException ex) { return null; }
	}
}