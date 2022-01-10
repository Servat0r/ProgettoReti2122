package winsome.common.config;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import winsome.util.Common;

/**
 * Utilities for processing a configuration map returned e.g. by {@link ConfigParser#parseFile(String)}.
 * @author Salvatore Correnti
 * @see ConfigParser
 */
public final class ConfigUtils {

	private ConfigUtils() {}
	
	public static final Function<String, String> newStr = (str) -> new String(str);
	
	public static final Function<String, Integer> newInt = (str) -> {
		try { return Integer.parseInt(str); }
		catch (Exception ex) {throw new IllegalArgumentException(Common.excStr(str + " is not a correct integer!"));}
	};
	
	public static final Function<String, Long> newLong = (str) -> {
		try { return Long.parseLong(str); }
		catch (Exception ex) {throw new IllegalArgumentException(Common.excStr(str + " is not a correct long!"));}
	};
	
	public static final Function<String, Double> newDouble = (str) -> {
		try { return Double.parseDouble(str); }
		catch (Exception ex) {throw new IllegalArgumentException(Common.excStr(str + " is not a correct double!"));}
	};
	
	public static final Function<String, PrintStream> newPrintStream = (str) -> {
		try { return new PrintStream(str); }
		catch (Exception ex)
			{throw new IllegalArgumentException(Common.excStr(str + " is not a correct file for output stream!")); } 
	};

	public static final Function<String, TimeUnit> newTimeUnit = (str) -> {
		TimeUnit unit = TimeUnit.valueOf(str); if (unit == null) throw new IllegalArgumentException(); else return unit;
	};
	
	public static final Function<String, InputStream> newInputStream = (str) -> {
		try { return new FileInputStream(str); }
		catch (Exception ex)
			{throw new IllegalArgumentException(Common.excStr(str + " is not a correct file for output stream!")); } 
	};
	
	/**
	 * If configMap contains the given key, applies the given function to the corresponding value and returns it,
	 *  otherwise returns a default value. Used in {@link winsome.client.WinsomeClient} and {@link winsome.client.WinsomeServer}
	 *  for parsing configuration map.
	 * @param <T> Type of the return value.
	 * @param configMap Configuration map.
	 * @param keyName The key.
	 * @param fun Transformation function.
	 * @param defVal Default value if key is absent.
	 * @return Transformed value if key is present, default value otherwise.
	 */
	public static final <T> T setValueOrDefault(Map<String, String> configMap, String keyName, Function<String, T> fun, T defVal) {
		String cval = configMap.get(keyName); return (cval != null ? fun.apply(cval) : defVal);
	}
}