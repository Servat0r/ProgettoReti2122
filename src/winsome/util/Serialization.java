package winsome.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.io.*;
import java.lang.reflect.*;

import com.google.gson.*;
import com.google.gson.stream.*;

import winsome.annotations.NotNull;
import winsome.server.data.DeserializationException;

/**
 * Common methods for JSON/Message exchange serialization/deserialization handling.
 * @author Salvatore Correnti.
 */
public final class Serialization {
	
	private Serialization() {}
	
	/**
	 * Standard {@link Gson} object used by server and {@link Common#jsonString(Object)} method.
	 */
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	
	/**
	 * Returns a {@link JsonWriter} object derived by {@link #GSON}.
	 * @param filename Name of the output file.
	 * @return A {@link JsonWriter} for the specified file with the same settings as the {@link #GSON} object.
	 * @throws IOException If thrown by {@link Gson#newJsonWriter(Writer)}.
	 */
	public static final JsonWriter fileWriter(String filename) throws IOException {
		Common.notNull(filename);
		try { return GSON.newJsonWriter( new OutputStreamWriter(new FileOutputStream(filename)) ); }
		catch (FileNotFoundException ex) { return null; }
	}
	
	/**
	 * Returns a {@link JsonReader} object derived by {@link #GSON}.
	 * @param filename Name of the output file.
	 * @return A {@link JsonReader} for the specified file with the same settings as the {@link #GSON} object.
	 * @throws IOException If thrown by {@link Gson#newJsonReader(Reader)}.
	 */
	public static final JsonReader fileReader(String filename) {
		Common.notNull(filename);
		try { return GSON.newJsonReader( new InputStreamReader(new FileInputStream(filename)) ); }
		catch (FileNotFoundException ex) { return null; }
	}
	
	/**
	 * Serialize a ConcurrentMap into a list of strings for each map entry using {@link #GSON} to serialize.
	 * @param map The ConcurrentMap to serialize.
	 * @return A List of String such that each item is the {@link Gson#toJson(Object, Type)} of an entry
	 * 	in the map.
	 * @throws Exception 
	 */
	@NotNull
	public static List<String> serializeMap(ConcurrentMap<String, List<String>> map) throws Exception{
		Common.notNull(map);
		Gson gson = new Gson();
		List<String> result = new ArrayList<>();
		for (ConcurrentMap.Entry<String, List<String>> entry : map.entrySet()) {
			StringWriter sw = new StringWriter();
			JsonWriter writer = gson.newJsonWriter(sw);
			writer.beginObject().name(entry.getKey()).beginArray();
			for (String s : entry.getValue()) writer.value(s);
			writer.endArray().endObject().flush();
			result.add(sw.toString());
		}
		return result;
	}
	
	/**
	 * Deserializes a list of strings in the format of the output of {@link #serializeMap(ConcurrentMap)}
	 *  into a ConcurrentMap using {@link #GSON} to deserialize.
	 * @param serMap The List of string to deserialize.
	 * @return A ConcurrentMap such that each entry is got from {@link Gson#fromJson(String, Type)} of an
	 *  item in the list.
	 * @throws IOException 
	 */
	@NotNull
	public static ConcurrentMap<String, List<String>> deserializeMap(List<String> serMap) throws IOException{
		Common.allAndArgs(serMap != null, serMap.size() > 0);
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		Gson gson = new Gson();
		for (String str : serMap) {
			StringReader rd = new StringReader(str);
			JsonReader reader = gson.newJsonReader(rd);
			List<String> args = new ArrayList<>();
			reader.beginObject();
			String name = reader.nextName();
			reader.beginArray();
			while (reader.hasNext()) args.add(reader.nextString());
			reader.endArray();
			reader.endObject();
			result.put(name, args);
		}
		return result;
	}
	
	@NotNull
	/**
	 * Deserializes a list of string into a list of string lists such that each item in the output contains
	 * num elements. Used with (num = 3) by {@link winsome.client.WinsomeClient#viewBlog()} and
	 *  {@link winsome.client.WinsomeClient#showFeed()}.
	 * @param posts List of strings.
	 * @param num Length of the output "sublists".
	 * @return A List of Strings as described above.
	 * @throws IllegalArgumentException If posts == null, num &le; 0 or num does not divide posts.size().
	 */
	public static List<List<String>> deserializePostList(List<String> posts, int num){
		Common.allAndArgs(posts != null, num > 0, posts.size() % num == 0);
		List<List<String>> result = new ArrayList<>();
		List<String> cval;
		int size = posts.size() / num;
		for (int i = 0; i < size; i++) {
			cval = new ArrayList<>();
			for (int j = 0; j < num; j++) cval.add( posts.get(num * i + j) );
			result.add(cval);
		}
		return result;
	}
	
	/* JSON */
	
	public static <V> void writeNameObject(JsonWriter writer, boolean inline, String name, V obj,
			BiFunction<JsonWriter, V, Exception> encoder) throws Exception {
		Common.notNull(writer, name, obj, encoder);
		String indent = Serialization.getWriterIndent(writer);
		writer.name(name);
		if (inline) writer.setIndent("");
		Exception ex = encoder.apply(writer, obj);
		if (ex != null) throw ex;
		if (inline) writer.setIndent(indent);
	}
	
	public static <V> void writeNameObject(JsonWriter writer, boolean inline, String name, V obj, Type type) throws Exception {
		BiFunction<JsonWriter, V, Exception> dflEncoder = (wr, v) -> {
			try { Serialization.GSON.toJson(v, type, wr); return null; }
			catch (Exception ex) { return ex; }
		};
		Serialization.writeNameObject(writer, inline, name, obj, dflEncoder);
	}
	
	public static <T> T readNameObject(JsonReader reader, String name, Function<JsonReader, T> decoder) throws Exception {
		Common.notNull(reader, name, decoder);
		if (!reader.nextName().equals(name)) throw new DeserializationException(DeserializationException.JSONREAD);
		return decoder.apply(reader);
	}
	
	public static <T> T readNameObject(JsonReader reader, String name, Type type) throws Exception {
		Common.notNull(reader, name, type);
		if (!reader.nextName().equals(name)) throw new DeserializationException(DeserializationException.JSONREAD);
		return Serialization.GSON.fromJson(reader, type);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> void writeField(JsonWriter writer, boolean inline, T obj, String name,
			BiFunction<JsonWriter, T, Exception> encoder) throws Exception {
		
		Common.notNull(writer, obj, name, encoder);
		String indent = Serialization.getWriterIndent(writer);
		
		Field field = obj.getClass().getDeclaredField(name);
		boolean isPublic = Modifier.isPublic(field.getModifiers());
		if (!isPublic) field.setAccessible(true);
		
		T fval = (T)field.get(obj);
		
		writer.name(name);
		if (inline) writer.setIndent("");
		Exception ex = encoder.apply(writer, fval);
		if (ex != null) throw ex;
		if (inline) writer.setIndent(indent);
		
		if (!isPublic) field.setAccessible(false);
	}
	
	public static void writeField(JsonWriter writer, boolean inline, Object obj, String name, Type type) throws Exception {
		
		Common.notNull(writer, obj, name, type);
		String indent = Serialization.getWriterIndent(writer);
		
		Field field = obj.getClass().getDeclaredField(name);
		boolean isPublic = Modifier.isPublic(field.getModifiers());
		if (!isPublic) field.setAccessible(true);
		
		Object fval = field.get(obj);
		
		writer.name(name);
		if (inline) writer.setIndent("");
		Serialization.GSON.toJson(fval, type, writer);
		if (inline) writer.setIndent(indent);
		if (!isPublic) field.setAccessible(false);
	}	
	
	public static <T> void readField(JsonReader reader, T obj, String name, Function<JsonReader, T> decoder) throws Exception {
		
		Common.notNull(reader, obj, name, decoder);
		Field field = obj.getClass().getDeclaredField(name);
		boolean isPublic = Modifier.isPublic(field.getModifiers());
		if (!isPublic) field.setAccessible(true);
		
		if (!reader.nextName().equals(name)) throw new DeserializationException(DeserializationException.JSONREAD);				
		T val = decoder.apply(reader);
		field.set(obj, val);
		if (!isPublic) field.setAccessible(false);
	}
	
	public static void readField(JsonReader reader, Object obj, String name, Type type)
			throws ReflectiveOperationException, SecurityException, IOException, DeserializationException {
		
		Common.notNull(reader, obj, name, type);
		Field field = obj.getClass().getDeclaredField(name);
		boolean isPublic = Modifier.isPublic(field.getModifiers());
		if (!isPublic) field.setAccessible(true);
		
		if (!reader.nextName().equals(name)) throw new DeserializationException(DeserializationException.JSONREAD);				
		Object val = Serialization.GSON.fromJson(reader, type);
		field.set(obj, val);
		
		if (!isPublic) field.setAccessible(false);
	}

	public static void writeFields(Gson gson, JsonWriter writer, boolean inline, Object obj, String...strs)
			throws IOException, SecurityException, ReflectiveOperationException {
		Common.notNull(gson, writer, obj, strs);
		Field field;
		String indent = Serialization.getWriterIndent(writer);
		for (String name : strs) {
			field = obj.getClass().getDeclaredField(name);
			boolean isPublic = Modifier.isPublic(field.getModifiers());
			if (!isPublic) field.setAccessible(true);
			
			Object fval = field.get(obj);
			
			writer.name(name);
			if (inline) writer.setIndent("");
			gson.toJson(fval, fval.getClass(), writer);
			if (inline) writer.setIndent(indent);
			
			if (!isPublic) field.setAccessible(false);
		}
	}

	public static void readFields(Gson gson, JsonReader reader, Object obj, String...strs) throws Exception {
		Common.notNull(gson, reader);
		Field field;
		for (String name : strs) {
			try {
				field = obj.getClass().getDeclaredField(name);
				boolean isPublic = Modifier.isPublic(field.getModifiers());
				if (!isPublic) field.setAccessible(true);
				
				if (!reader.nextName().equals(name)) throw new DeserializationException(DeserializationException.JSONREAD);				
				Object val = gson.fromJson(reader, field.getType());
				field.set(obj, val);
				
				if (!isPublic) field.setAccessible(false);
			} catch (NoSuchFieldException nsfe) {}
		}		
	}

	public static <T> void writeColl(JsonWriter writer, boolean inline, Collection<T> coll,
			String name, BiFunction<JsonWriter, T, Exception> encoder) throws Exception {
		Common.notNull(writer, coll, name, encoder);
		String indent = Serialization.getWriterIndent(writer);
		writer.name(name).beginArray();
		if (inline) writer.setIndent("");
		for (T item : coll) {
			Exception ex = encoder.apply(writer, item);
			if (ex != null) throw ex;
		}
		writer.endArray();
		if (inline) writer.setIndent(indent);
	}
	
	public static <T> void writeColl(JsonWriter writer, boolean inline, Collection<T> coll, String name, Type type) throws Exception {
		BiFunction<JsonWriter, T, Exception>
			dflEncoder = (wr, item) -> {
			try { GSON.toJson(item, type, wr); return null; }
			catch (Exception ex) { return ex; }
		};
		Serialization.writeColl(writer, inline, coll, name, dflEncoder);
	}
	
	public static <T> void readColl(JsonReader reader, Collection<T> coll, String name, Function<JsonReader, T> decoder)
			throws Exception {
		Common.notNull(reader, coll, name, decoder);
		if (!reader.nextName().equals(name)) throw new DeserializationException(DeserializationException.JSONREAD);		
		reader.beginArray();
		while (reader.hasNext()) coll.add(decoder.apply(reader));
		reader.endArray();
	}
	
	public static <T> void readColl(JsonReader reader, Collection<T> coll, String name, Type type) throws Exception {
		Common.notNull(reader, coll, name, type);
		Function<JsonReader, T> dflDecoder = (rd) -> Serialization.GSON.fromJson(rd, type);
		Serialization.readColl(reader, coll, name, dflDecoder);
	}
	
	public static <K, V> void writeMap(JsonWriter writer, boolean inline, Map<K, V> map, String name,
		Function<K, String> keySerializer,
		BiFunction<JsonWriter, V, Exception> valueSerializer)
		throws Exception {
		
		Common.notNull(writer, map, name, keySerializer, valueSerializer);
		String indent = Serialization.getWriterIndent(writer);
		
		writer.name(name).beginObject();
		for (Map.Entry<K, V> entry : map.entrySet()) {
			String key = keySerializer.apply(entry.getKey());
			writer.name(key);
			if (inline) writer.setIndent("");
			Exception ex = valueSerializer.apply(writer, entry.getValue());
			if (ex != null) throw ex;
			if (inline) writer.setIndent(indent);
		}
		writer.endObject();
	}
	
	public static <K, V> void readMap(JsonReader reader, Map<K, V> map, String name,
		Function<String, K> keyDeserializer,
		Function<JsonReader, V> valueDeserializer
		) throws Exception {
		
		Common.notNull(reader, map, name, keyDeserializer, valueDeserializer);
		
		if (!reader.nextName().equals(name)) throw new DeserializationException(DeserializationException.JSONREAD);
		reader.beginObject();
		
		while (reader.hasNext()) {
			K key = keyDeserializer.apply(reader.nextName());
			V value = valueDeserializer.apply(reader);
			map.put(key, value);			
		}
		reader.endObject();		
	}
	
	public static String getWriterIndent(JsonWriter writer) {
		Common.notNull(writer);
		try {
			Field f = writer.getClass().getDeclaredField("indent");
			f.setAccessible(true);
			String res = (String)f.get(writer);
			f.setAccessible(false);
			return res;
		} catch (ReflectiveOperationException roex) { roex.printStackTrace(); return null; }
	}
	
	public static String getWriterSeparator(JsonWriter writer) {
		Common.notNull(writer);
		try {
			Field f = writer.getClass().getDeclaredField("separator");
			f.setAccessible(true);
			String res = (String)f.get(writer);
			f.setAccessible(false);
			return res;
		} catch (ReflectiveOperationException roex) { roex.printStackTrace(); return null; }
	}
	
	/* JSON */
}