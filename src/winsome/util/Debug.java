package winsome.util;

import java.io.PrintStream;

/**
 * Simple debugging tools. All these methods works if the property "debug" is set, which
 * 	can be done with {@link #setDebug()} method or by commandline with -Ddebug=true.
 * @author Salvatore Correnti
 *
 */
public final class Debug {

	/**
	 * PrintStream of the debug printings.
	 */
	private static PrintStream dbgStream = System.out;
	public static final String DBGSEPAR = ": ";
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
	
	
	public static void println(int stacktracelevel, String fmt, Object... objs) {
		Common.allAndArgs(stacktracelevel >= 0);
		Thread ct = Thread.currentThread();
		StackTraceElement stracelem = ct.getStackTrace()[stacktracelevel];
		int fline = stracelem.getLineNumber();
		String ult = (fline >= 0 ? " at line " + fline : "");
		String fname = String.format("Thread[%s]: %s.%s%s: ", ct.getName(), stracelem.getClassName(),
			stracelem.getMethodName(), ult);
		dbgStream.printf(DEBUGSTR + fname + fmt + "%n", objs);
	}
	
	public static void println(String fmt, Object...objs) { println(3, fmt, objs); }
	
	public static void println(Object obj) { println( 3, "(%s)", (obj == null ? "(null)" : obj.toString()) ); }
	
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
	
	/**
	 * Utility for debug printing of an Exception.
	 * @param ex The Exception to print on {@link #dbgStream}.
	 */
	public synchronized static void debugExc(Exception ex) {
		Debug.println("Exception caught: ");
		ex.printStackTrace(dbgStream);
	}
	
	/**
	 * If {@link #dbgStream} is different from System.out and debug property is set, closes {@link #dbgStream}
	 * and exits, otherwise exits normally.
	 * @param code Exit code.
	 */
	public static void exit(int code) {
		String debug = System.getProperty(DEBUGPROP);
		if (debug != null && debug.equals("true")){
			dbgStream.println(code);
			if (dbgStream != System.out) { dbgStream.close(); dbgStream = System.out; }
		}
		System.exit(code);
	}
}