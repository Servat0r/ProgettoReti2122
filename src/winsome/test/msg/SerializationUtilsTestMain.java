package winsome.test.msg;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import winsome.util.*;

public final class SerializationUtilsTestMain {

	public static void main(String[] args) {
		ConcurrentMap<String, List<String>> tryMap = Common.newConcurrentHashMapFromLists(
			Arrays.asList("Str1", "String2"),
			Arrays.asList( Arrays.asList("tag1", "tag2"), Arrays.asList("tag2", "tag3", "tag54"))
		);
		List<String> serMap = SerializationUtils.serializeMap(tryMap);
		for (String s : serMap) Common.printLn(s);
		ConcurrentMap<String, List<String>> tryMap2 = SerializationUtils.deserializeMap(serMap);
		for (String s : tryMap2.keySet()) Common.printf("'%s' : '%s'%n", s, tryMap2.get(s).toString());
	}
	
}