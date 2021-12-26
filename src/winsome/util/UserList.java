package winsome.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import winsome.client.command.CommandParser;

public final class UserList {
	
	private UserList() {}

	private static final String SEPAR = " #";
	public static final String ENTRY_REGEX = CommandParser.ALPHANUM + SEPAR + CommandParser.NUM + "[( " + CommandParser.LOWERNUM + ")]+";
	
	public static List<String> serializeMap(ConcurrentMap<String, List<String>> map){
		Common.notNull(map);
		Set<String> keys = map.keySet();
		for (String key : keys) Common.notNull( key, map.get(key) );
		List<String> result = new ArrayList<>();
		StringBuilder sb;
		for (String key : keys) {
			List<String> tags = map.get(key);
			sb = new StringBuilder();
			sb.append(key + SEPAR + tags.size() + SEPAR);
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
			if (!Pattern.matches(ENTRY_REGEX, s))
				throw new IllegalArgumentException(Common.excStr("Item <" + s + "> does not match entry regex!"));
			cval = new ArrayList<String>();
			m = Pattern.compile(CommandParser.ALPHANUM + SEPAR).matcher(s);
			m.find();
			ckey = new String( s.substring(0, m.end() - SEPAR.length()) );
			s = s.substring(m.end());
			m = Pattern.compile(CommandParser.NUM).matcher(s);
			m.find();
			clen = Integer.parseInt( s.substring(0, m.end()) );
			s = s.substring(m.end());
			for (int i = 0; i < clen; i++) {
				m = Pattern.compile(" " + CommandParser.LOWERNUM).matcher(s);
				m.find();
				cval.add( new String( s.substring(1, m.end()) ) );
				s = s.substring(m.end());
			}
			Common.debugln("ckey = '" + ckey + "', clen = '" + clen + "', remkey = '" + s + "', cval = '" + cval.toString() + "'");			
			result.put(ckey, cval);
		}
		return result;
	}
	
	public static void main(String[] args) {
		ConcurrentMap<String, List<String>> tryMap = Common.newConcurrentHashMapFromLists(
			Arrays.asList("Str1", "String2"),
			Arrays.asList( Arrays.asList("tag1", "tag2"), Arrays.asList("tag2", "tag3", "tag54"))
		);
		List<String> serMap = UserList.serializeMap(tryMap);
		for (String s : serMap) Common.printLn(s);
		ConcurrentMap<String, List<String>> tryMap2 = UserList.deserializeMap(serMap);
		for (String s : tryMap2.keySet()) Common.printf("'%s' : '%s'%n", s, tryMap2.get(s).toString());
	}
}
