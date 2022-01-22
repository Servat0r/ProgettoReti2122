package winsome.server.data;

import winsome.annotations.NotNull;
import winsome.util.*;

import com.google.gson.*;
import com.google.gson.stream.*;

/**
 * Comments in Winsome.
 * @author Salvatore Correnti
 */
public final class Comment implements Comparable<Comment> {
		
	@NotNull
	private final String idAuthor, content;
	private final long idPost, time;
	
	public static final Gson gson() { return Serialization.GSON; }
	
	public static final Exception jsonSerializer(JsonWriter writer, Comment comm) {
		Common.notNull(writer, comm);
		String indent = Serialization.getWriterIndent(writer);
		try {
			writer.beginObject();
			Serialization.writeFields(gson(), writer, true, comm, "time", "content");
			writer.setIndent("");
			writer.endObject();
			writer.setIndent(indent);
			return null;
		} catch(Exception ex) { return ex; }
	}
	
	public static final Comment deserialize(JsonReader reader, long idPost, String idAuthor) {
		Common.notNull(reader, idAuthor); Common.allAndArgs(idPost > 0);
		Comment comm = new Comment(idAuthor, idPost, "stub");
		try {
			reader.beginObject();
			Serialization.readFields(gson(), reader, comm, "time", "content");
			reader.endObject();
			return comm;
		} catch (Exception ex) { ex.printStackTrace(); return null; }
	}
	
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
	
	@NotNull
	public String toString() {
		String cname = this.getClass().getSimpleName();
		return String.format( "%s: %s", cname, Common.jsonString(this) );
	}
}