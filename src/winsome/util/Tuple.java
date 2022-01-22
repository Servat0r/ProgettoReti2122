package winsome.util;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

import com.google.gson.*;
import com.google.gson.stream.*;

public final class Tuple {
	
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	
	private static final String
		SIZE = "size",
		TYPES = "types",
		VALUES = "values",
		INDENT = " ",
		SETTINGS = "settings";
	
	private final List<Object> data = new ArrayList<>();
	private final List<Class<?>> types = new ArrayList<>();
	private Lock[] locks;
	private int size;
	private boolean jsonable = false;
	
	private ConfigMap<String> settings = new ConfigMap<>();
	
	public final void defaultSettings() { settings
		.set("toString.newLine", "\n")
		.set("toString.indent", "  ")
		.set("toString.separ", "; ")
		.set("toString.start", "{")
		.set("toString.end", "}");
	}
	
	public final ConfigMap<String> settings(){ return settings; }
	
	private Tuple() { jsonable = true; }
	
	private Tuple(int size) {
		Common.allAndArgs(size >= 0);
		this.size = size;
		this.locks = new ReentrantLock[size];
	}
	
	public static Tuple newEmptyTuple(Class<?>...classes) {
		Tuple t = new Tuple(classes.length);
		for (int i = 0; i < t.size; i++) {
			t.data.add(null);
			t.types.add(classes[i]);
			t.locks[i] = new ReentrantLock();
		}
		return t;
	}
	
	public static Tuple newEmptyTuple(Collection<Class<?>> classes) {
		Tuple t = new Tuple(classes.size());
		Iterator<Class<?>> iter = classes.iterator();
		int i = 0;
		while (iter.hasNext()) {
			t.data.add(null);
			t.types.add(iter.next());
			t.locks[i++] = new ReentrantLock();
		}
		return t;
	}	
	
	public static Tuple newInitTuple(Object...items) {
		Tuple t = new Tuple(items.length);
		for (int i = 0; i < t.size; i++) {
			t.data.add(items[i]);
			t.types.add(items[i].getClass());
			t.locks[i] = new ReentrantLock();
		}
		return t;
	}
	
	public static Tuple newInitTuple(Collection<Object> items) {
		Tuple t = new Tuple(items.size());
		Iterator<Object> iter = items.iterator();
		int i = 0; Object currObj;
		while (iter.hasNext()) {
			currObj = iter.next();
			t.data.add(currObj);
			t.types.add(currObj.getClass());
			t.locks[i++] = new ReentrantLock();
		}
		return t;
	}
	
	//------------------------------------------------------------------------
	
	public Object get(int index) {
		Common.allAndArgs(index >= 0, index < size);
		try {
			locks[index].lock();
			return data.get(index);
		} finally { locks[index].unlock(); }
	}
	
	
	public Class<?> getType(int index){
		Common.allAndArgs(index >= 0, index < size);
		try {
			locks[index].lock();
			return types.get(index);
		} finally { locks[index].unlock(); }
	}
	
	public List<Class<?>> getTypes(){
		try {
			for (int i = 0; i < size; i++) locks[i].lock();
			return new ArrayList<Class<?>>(types);
		} finally { for (int i = size-1; i >= 0; i--) locks[i].unlock(); }
	}
	
	
	public String getRepr(int index, Function<Class<?>, String> transf) {
		Common.notNull(transf);
		Common.allAndArgs(index >= 0, index < size);
		try {
			locks[index].lock();
			return transf.apply(getType(index));
		} finally { locks[index].unlock(); }
	}
	
	public String getRepr(int index) { return this.getRepr(index, Class<?>::getName); }
	
	
	public List<String> getReprs(Function<Class<?>, String> transf){
		try {
			for (int i = 0; i < size; i++) locks[i].lock();
			List<String> result = new ArrayList<>();
			this.getTypes().forEach( c -> result.add(transf.apply(c)) );
			return result;
		} finally { for (int i = size-1; i >= 0; i--) locks[i].unlock(); }
	}
	
	public List<String> getReprs(){ return this.getReprs(Class::getCanonicalName); }
	
	
	public void set(int index, Object elem) throws ClassCastException {
		Common.notNull(elem);
		Common.allAndArgs(index >= 0, index < size);
		try {
			locks[index].lock();
			elem.getClass().asSubclass( types.get(index) );
			data.set(index, elem);
		} finally { locks[index].unlock(); }
	}
	
	public int size() { return size; }
	
	public List<String> getValsReprs(Function<Object, String> stringfun){
		try {
			for (int i = 0; i < size; i++) locks[i].lock();
			List<String> names = new ArrayList<>();
			this.data.forEach( obj -> names.add( stringfun.apply(obj)) );
			return names;
		} finally { for (int i = size-1; i >= 0; i--) locks[i].unlock(); }
	}
	
	public List<String> getValsReprs(){ return getValsReprs( obj -> (obj != null ? obj.toString() : "null") ); }
	
	public Gson gson() { return GSON; }

	public void toJson(JsonWriter writer) throws IOException {
		Common.notNull(writer);
		
		writer.setIndent(INDENT);
		
		writer.beginObject();
		
		writer.name(SETTINGS).jsonValue( gson().toJson(settings, settings.getClass()) );
		
		writer.name(SIZE).value(size);
		
		writer.name(TYPES);
		writer.beginArray();
		List<String> l = getReprs(Class::getCanonicalName);
		for (String s : l) writer.value(s);
		writer.endArray();
		
		writer.name(VALUES);
		writer.beginArray();
		data.forEach(obj -> {
			try { writer.value( GSON.toJson(obj, obj.getClass()) ); }
			catch (IOException e) { e.printStackTrace(); }
		});
		writer.endArray();
		
		writer.endObject();
		
		writer.close();
	}
	
	public boolean fromJson(JsonReader reader) throws IOException {
		Common.notNull(reader);
		if (!jsonable) return false;
		String name;
		List<String> values = new ArrayList<>();
		List<Class<?>> types = new ArrayList<>();
		
		reader.beginObject();
		
		while (reader.hasNext()) {
			name = reader.nextName();
			switch(name) {
				case SETTINGS : {
					settings = gson().fromJson(reader, settings.getClass());
					break;
				}
				case SIZE : {
					size = reader.nextInt();
					locks = new Lock[size];
					for (int i = 0; i < size; i++) locks[i] = new ReentrantLock();
 					break;
				}
				
				case TYPES : {
					
					reader.beginArray();
					for (int i = 0; i < size; i++) values.add(reader.nextString());
					reader.endArray();
					
					CollectionsUtils.remap(values, types, t1 -> {
						try { return Class.forName(t1); }
						catch (ClassNotFoundException e) { e.printStackTrace(); return null; }
					});
					
					this.types.clear();
					this.types.addAll(types);
					break;
				}
				
				case VALUES : {
					
					data.clear();
					
					reader.beginArray();
					for (int i = 0; i < size; i++) data.add( GSON.fromJson(reader.nextString(), types.get(i)) );
					reader.endArray();
					
					break;
				}
				
				default : { reader.skipValue(); }
			}
		}
		reader.endObject();
		
		values.clear();
		types.clear();
		reader.close();
		return true;
	}
	
	public String toString() {
		String start = "{", end = "}", separ = "; ", newLine = "", indent = "";
		Function<String, String> idStr = str -> str;
		
		ConfigMap<String> settings = this.settings.getSubset("toString");
		
		newLine = settings.getValueOrDefault("newLine", idStr, newLine);
		indent = settings.getValueOrDefault("indent", idStr, indent);
		
		String items = 
			CollectionsUtils.strReduction(this.getValsReprs(), separ, start, end, str -> str)
			.replace(start, settings.getValueOrDefault("start", idStr, start) + newLine + indent)
			.replace(separ, settings.getValueOrDefault("separ", idStr, separ) + newLine + indent)
			.replace(end, newLine + settings.getValueOrDefault("end", idStr, end));
		
		return String.format( "Tuple %s %s", this.getReprs(Class::getSimpleName), items);
	}
			
	public boolean isJsonable() { return jsonable; }
}