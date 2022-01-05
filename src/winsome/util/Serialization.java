package winsome.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.io.*;

import com.google.gson.*;
import com.google.gson.stream.*;

import winsome.client.command.CommandParser;

public final class Serialization {
	
	private Serialization() {}

	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
		
	public static final String
		SEPAR = " #",
		ALPHANUM = CommandParser.ALPHANUM,
		NUM = CommandParser.NUM,
		LOWERNUM = CommandParser.LOWERNUM,
		QUOTED = CommandParser.QUOTED,
		//username #<tagnum> tag1 tag2 ...
		CMAP_ENTRY = ALPHANUM + SEPAR + NUM + "[( " + LOWERNUM + ")]+",
		//id #author #title
		POSTLIST_ENTRY = (NUM + SEPAR) + (ALPHANUM + SEPAR) + QUOTED;
	
	public static final JsonWriter fileWriter(String filename) throws IOException {
		Common.notNull(filename);
		try { return GSON.newJsonWriter( new OutputStreamWriter(new FileOutputStream(filename)) ); }
		catch (FileNotFoundException ex) { return null; }
	}
	
	public static final JsonReader fileReader(String filename) {
		Common.notNull(filename);
		try { return GSON.newJsonReader( new InputStreamReader(new FileInputStream(filename)) ); }
		catch (FileNotFoundException ex) { return null; }
	}
	
	public static List<String> serializeMap(ConcurrentMap<String, List<String>> map){
		Common.notNull(map);
		Set<String> keys = map.keySet();
		for (String key : keys) Common.notNull( key, map.get(key) );
		List<String> result = new ArrayList<>();
		StringBuilder sb;
		for (String key : keys) {
			List<String> tags = map.get(key);
			sb = new StringBuilder();
			sb.append(key + SEPAR + tags.size());
			for (String tag : tags) sb.append(" "  + tag);
			result.add(sb.toString());
		}
		return result;
	}
	
	public static ConcurrentMap<String, List<String>> deserializeMap(List<String> serMap){
		Common.notNull(serMap);
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		String ckey; int clen; List<String> cval; Matcher m;
		for (String s : serMap) {
			Common.notNull(s);
			if (!Pattern.matches(CMAP_ENTRY, s)) {
				System.err.println(Common.excStr("Item '%s' does not match entry regex!", s));
				return null;
			}
			cval = new ArrayList<String>();
			m = Pattern.compile(ALPHANUM + SEPAR).matcher(s);
			m.find();
			ckey = new String( s.substring(0, m.end() - SEPAR.length()) );
			s = s.substring(m.end());
			m = Pattern.compile(NUM).matcher(s);
			m.find();
			clen = Integer.parseInt( s.substring(0, m.end()) );
			s = s.substring(m.end());
			for (int i = 0; i < clen; i++) {
				m = Pattern.compile(" " + LOWERNUM).matcher(s);
				m.find();
				cval.add( new String( s.substring(1, m.end()) ) );
				s = s.substring(m.end());
			}
			result.put(ckey, cval);
		}
		return result;
	}
	
	public static List<List<String>> deserializePostList(List<String> posts){
		Common.notNull(posts);
		List<List<String>> result = new ArrayList<>();
		List<String> cval;
		Matcher m;
		for (String s : posts) {
			Common.notNull(s);
			if (!Pattern.matches(POSTLIST_ENTRY, s)) {
				System.err.println( Common.excStr("Item <" + s + "> does not match postlist regex!") );
				return null;
			}
			cval = new ArrayList<String>();
			m = Pattern.compile(NUM + SEPAR).matcher(s);
			m.find();
			cval.add( new String(s.substring(0, m.end() - SEPAR.length())) );
			s = s.substring(m.end());
			m = Pattern.compile(ALPHANUM + SEPAR).matcher(s);
			m.find();
			cval.add( new String(s.substring(0, m.end() - SEPAR.length())) );
			s = s.substring(m.end());
			cval.add(new String(s));
			result.add(cval);
		}
		return result;
	}
	
}