package winsome.util;

import java.io.PrintStream;

public final class Debug {

	public static final String DBGSEPAR = ": ";
	private static PrintStream dbgStream = System.out;
	private static final String DEBUGSTR = "DEBUG" + DBGSEPAR;
	public static final String DEBUGPROP = "debug"; /* System property for enabling debugging. */
	public static final String DEBUGFILE = "debug.txt"; /* Default debug file. */

	private Debug() {}

	/**
	 * Enables debug printings.
	 */
	public static void setDebug() { System.setProperty(DEBUGPROP, "true"); }

	/**
	 * Disables debug printings.
	 */
	public static void resetDebug() { System.setProperty(DEBUGPROP, "false"); }

	/**
	 * Clears debug property.
	 */
	public static void clearDebug() { System.clearProperty(DEBUGPROP); }

	/**
	 * Sets debug file to the specified filename.
	 * @param filename (Relative) path of the file.
	 */
	public static synchronized void setDbgStream(String filename) {
		Common.notNull(filename);
		try { dbgStream = new PrintStream(filename); }
		catch (Exception ex) { dbgStream = System.out; }
	}

	/**
	 * Sets debug file to default value.
	 */
	public static synchronized void setDbgStream() { setDbgStream(DEBUGFILE); }

	/**
	 * Sets debug printing to default stream (System.out).
	 */
	public static synchronized void resetDgbStream() { dbgStream = System.out; }


	/**
	 * Debug printing indicating the invoking function and the current source code line.
	 * @param obj Object to debug (prints 'null' if null, otherwise invokes obj.toString()).
	 */
	public static void println(int stacktracelevel, String fmt, Object... objs) {
		Common.notNeg(stacktracelevel);
		Thread ct = Thread.currentThread();
		StackTraceElement stracelem = ct.getStackTrace()[stacktracelevel];
		int fline = stracelem.getLineNumber();
		String ult = (fline >= 0 ? " at line " + fline : "");
		String fname = String.format("Thread[%s]: %s.%s%s: ", ct.getName(), stracelem.getClassName(),
			stracelem.getMethodName(), ult);
		dbgStream.printf(DEBUGSTR + fname + fmt + "%n", objs);
	}
	
	public static void println(String fmt, Object...objs) { println(3, fmt, objs); }
	
	public static void println(Object obj) { println( 3, "%s", (obj == null ? "null" : obj.toString()) ); }
	
	public static void println(int num) { println(3, "%d", num); }
	public static void println(long num) { println(3, "%d", num); }
	public static void println(byte num) { println(3, "%s", Byte.valueOf(num)); }
	public static void println(double num) { println(3, "%s", Double.valueOf(num)); }
	public static void println(char c) { println(3, "%c", c); }
	
	public static void println() {
		Thread t = Thread.currentThread();
		StackTraceElement elem = t.getStackTrace()[2];
		String fname = "Thread[" + t.getName() + "]: " + elem.getClassName() + "." + elem.getMethodName();
		int fline = elem.getLineNumber();
		dbgStream.println(DEBUGSTR + fname + " at line " + fline);
	}

	public static void printf(String fname, String format, Object... objs) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true")) dbgStream.printf(DEBUGSTR + fname + DBGSEPAR + format, objs);
	}

	public static void printf(String format, Object... objs) {
		String fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		int fline = Thread.currentThread().getStackTrace()[2].getLineNumber();
		printf(fname + " at line " + fline, format, objs);
	}
	
	public static void printVars(Object...objects) {
		StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
		String fname = elem.getClassName() + "." + elem.getMethodName();
		int fline = elem.getLineNumber();
		StringBuilder sb = new StringBuilder(DEBUGSTR + fname + " at line " + fline + DBGSEPAR);
		for (int i = 0; i < objects.length; i++) {
			Object obj = objects[i];
			sb.append("var #" + (i+1) + " = " + (obj != null ? obj.toString() : "null"));
		}
		dbgStream.println(sb.toString());
	}
	
	public synchronized static void debugExc(Exception ex) {
		Debug.println("Exception caught: ");
		ex.printStackTrace(dbgStream);
	}
	
	public static void exit(int code) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true")){
			dbgStream.println(code);
			if (dbgStream != System.out) { dbgStream.close(); dbgStream = System.out; }
		}
		System.exit(code);
	}
}