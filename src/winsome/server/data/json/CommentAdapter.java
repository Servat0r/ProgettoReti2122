package winsome.server.data.json;

import java.io.IOException;

import com.google.gson.*;
import com.google.gson.stream.*;

import winsome.server.data.*;
import winsome.util.*;

public class CommentAdapter extends TypeAdapter<Comment> {
	
	public Comment read(JsonReader reader) throws IOException {
		Common.notNull(reader);
		String[] str = reader.nextString().split(" : ");
		return null;
	}
	
	public void write(JsonWriter writer, Comment comment) throws IOException {
		Common.notNull(writer, comment);
		String indent = Serialization.getWriterIndent(writer);
		writer.setIndent("");
		String cmm = String.format("%s : %s", comment.getTime(), comment.getContent());
		writer.value(cmm);
		writer.setIndent(indent);
	}
	
}