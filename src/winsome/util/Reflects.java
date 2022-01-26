package winsome.util;

import java.lang.reflect.*;
import java.util.*;

public final class Reflects {
	
	private Reflects() {}
	
	public static boolean isSubclass(Class<?> clazz, Class<?> superClazz) {
		Common.notNull(clazz, superClazz);
		try { clazz.asSubclass(superClazz); return true; }
		catch (ClassCastException ccex) { return false; }
	}
	
	public static Pair<Long, Object> monitorTime(boolean nanos, Method m, Object obj, Object... objs) throws ReflectiveOperationException {
		long time = (nanos ? System.nanoTime() : System.currentTimeMillis());
		Object res = m.invoke(obj, objs);
		time = (nanos ? System.nanoTime() : System.currentTimeMillis()) - time;
		return new Pair<>(time, res);
	}
	
	public static void printMonitorTime(boolean nanos, Method m, Object obj, Object... objs) throws ReflectiveOperationException {
		Pair<Long, Object> pair = monitorTime(nanos, m, obj, objs);
		Common.printfln(
			"Method: %s.%s(%s) executed in %d %s",
			m.getDeclaringClass().getSimpleName(),
			m.getName(),
			CollectionsUtils.strReduction(CollectionsUtils.arrToList(objs), ", ", "", "", Objects::toString),
			pair.getKey(),
			(nanos ? "nanoseconds" : "milliseconds")
		);
	}
	
	public static void printParams(String className, String methodName) throws ClassNotFoundException {
		List<Method> methods = Arrays.asList(Class.forName(className).getDeclaredMethods());
		List<String> res = new ArrayList<>();
		for (Method m : methods) {
			if (m.getName().equals(methodName)) {
				Arrays.asList(m.getParameters()).forEach( param -> res.add(param.getName() + ": " + param.getType()) );
			}
		}
		String str = CollectionsUtils.strReduction(res, "\n", "{\n", "\n}", s -> s);
		System.out.println(str);
	}
	
	public static void printParams(Class<?> cl, String methodName) throws ClassNotFoundException
	{ printParams(cl.getCanonicalName(), methodName); }
	
	public static void genericDesc(Object obj, List<String> objParams, Map<String, Type> fieldParams){
		Common.notNull(obj, objParams, fieldParams);
		Class<?> cl = obj.getClass();
		
		objParams.add(cl.toGenericString());
		for (TypeVariable<?> tv : cl.getTypeParameters()) objParams.add(tv.getTypeName());
		
		for (Field field : cl.getDeclaredFields()) fieldParams.put(field.getName(), field.getGenericType());
	}
	
	public static String printFields(Object obj) {
		Common.notNull(obj);
		List<Field> fields = Arrays.asList(obj.getClass().getDeclaredFields());
		fields.forEach(f -> f.setAccessible(true));
		List<String> datas = new ArrayList<>();
		
		CollectionsUtils.remap(fields, datas, f -> {
			try {
				String format = "%s : %s : '%s'", internal;
				if (f.get(obj) == null) internal = "null";
				else if ( f.get(obj).getClass().isArray() ) {
					Object[] o = (Object[])f.get(obj);
					List<Object> l = new ArrayList<>();
					for (Object item : o) l.add(item);
					internal = CollectionsUtils.strReduction(l, ", ", "[", "]", Objects::toString);
				} else internal = Objects.toString(f.get(obj));
				return String.format(format, f.getName(), f.getType().getSimpleName(), internal);
			} catch (IllegalArgumentException | IllegalAccessException e) { e.printStackTrace(); return null; }
		});
		
		fields.forEach(f -> f.setAccessible(false));
		
		return CollectionsUtils.strReduction(datas, " ", "{", "}", Objects::toString);
	}
	
	public static String printFieldsNames(Object obj) {
		Common.notNull(obj);
		List<Field> fields = Arrays.asList(obj.getClass().getDeclaredFields());
		List<String> datas = new ArrayList<>();
		CollectionsUtils.remap( fields, datas, f -> f.getName() + " : " + f.getType().getSimpleName() );		
		return CollectionsUtils.strReduction(datas, "; ", "{", "}", Objects::toString);
	}
	
}