package winsome.util;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.lang.reflect.Type;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.*;

import winsome.annotations.NotNull;

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
	 * {@link TypeToken} used for {@link #serializeMap(ConcurrentMap)} method.
	 */
	private static final Type ENTRYTYPE = new TypeToken< Pair<String, List<String>> >() {}.getType();
	
	@NotNull
	/**
	 * Serialize a ConcurrentMap into a list of strings for each map entry using {@link #GSON} to serialize.
	 * @param map The ConcurrentMap to serialize.
	 * @return A List of String such that each item is the {@link Gson#toJson(Object, Type)} of an entry
	 * 	in the map.
	 */
	public static List<String> serializeMap(ConcurrentMap<String, List<String>> map){
		Common.notNull(map);
		Gson gson = new Gson();
		List<String> result = new ArrayList<>();
		Pair<String, List<String>> pair;
		for (ConcurrentMap.Entry<String, List<String>> entry : map.entrySet()) {
			pair = new Pair<>(entry.getKey(), entry.getValue());
			String str = gson.toJson(pair, ENTRYTYPE);
			result.add(str);
		}
		return result;
	}
	
	@NotNull
	/**
	 * Deserializes a list of strings in the format of the output of {@link #serializeMap(ConcurrentMap)}
	 *  into a ConcurrentMap using {@link #GSON} to deserialize.
	 * @param serMap The List of string to deserialize.
	 * @return A ConcurrentMap such that each entry is got from {@link Gson#fromJson(String, Type)} of an
	 *  item in the list.
	 */
	public static ConcurrentMap<String, List<String>> deserializeMap(List<String> serMap){
		Common.allAndArgs(serMap != null, serMap.size() > 0);
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		Gson gson = new Gson();
		Pair<String, List<String>> pair;
		for (String str : serMap) {
			pair = gson.fromJson(str, ENTRYTYPE);
			result.put(pair.getKey(), pair.getValue());
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
}