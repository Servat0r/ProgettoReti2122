package winsome.util;

import java.io.*;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Basic logging system based on PrintStreams for logging messages / Exception stack traces with
 *  a formatted output.
 * @author Salvatore Correnti
 */
public final class Logger implements Closeable {
	
	private final PrintStream stream;
	private boolean closed;
	private final ReentrantLock lock;
	private final String logstr, errstr;
	
	/**
	 * Constructs a Logger with the specified ordinary and stack trace format strings such that
	 *  it writes to the specified filename.
	 * @param logstr Format string for ordinary messages, such that it supports {@link String#format(String, Object...)}
	 *  with 3 variadic arguments. 
	 * @param errstr Format string for error messages, such that is supports {@link String#format(String, Object...)}
	 *  with 3 variadic arguments.
	 * @param filename (Relative) path of the output file.
	 * @throws FileNotFoundException If thrown by PrintStream constructor.
	 * @throws NullPointerException If any of the params is null. 
	 */
	public Logger(String logstr, String errstr, String filename) throws FileNotFoundException {
		Common.notNull(logstr, errstr, filename);
		this.stream = new PrintStream(filename);
		this.closed = false;
		this.lock = new ReentrantLock();
		this.logstr = logstr;
		this.errstr = errstr;
	}
	
	/**
	 * Constructs a Logger with the specified ordinary and stack trace format strings such that
	 *  it writes to the specified filename.
	 * @param logstr Format string for ordinary messages, such that it supports {@link String#format(String, Object...)}
	 *  with 3 variadic arguments. 
	 * @param errstr Format string for error messages, such that is supports {@link String#format(String, Object...)}
	 *  with 3 variadic arguments.
	 * @param stream Stream for logging.
	 * @throws NullPointerException If any of the params is null. 
	 */
	public Logger(String logstr, String errstr, PrintStream stream) { 
		Common.notNull(logstr, errstr, stream);
		this.stream = stream;
		this.closed = false;
		this.lock = new ReentrantLock();
		this.logstr = logstr;
		this.errstr = errstr;
	}
	
	/**
	 * Logs an ordinary message based on logstr such that it contains as substrings:
	 *  - timestamp as {@link Date#toString()};
	 *  - Thread[threadName]: className.methodName;
	 *  - {@link String#format(String, Object...)}
	 * @param format Format string for final message.
	 * @param objs Objects to format for the final message.
	 * @throws NullPointerException If format == null.
	 */
	public void log(String format, Object ...objs) {
		Common.notNull(format);
		String timestamp = new Date().toString().substring(0, 19);
		StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
		String fname = "Thread[" + Thread.currentThread().getName() + "]: " + elem.getClassName() + "." + elem.getMethodName();
		String msg = String.format(format, objs);
		try {
			lock.lock();
			if (!closed) stream.println(String.format(logstr, timestamp, fname, msg));
		} finally { lock.unlock(); }
	}
	
	/**
	 * Logs an error message with the StackTrace of the Exception parameter such that it contains as substrings:
	 * 	- timestamp as {@link Date#toString()};
	 *  - Thread[threadName]: className.methodName;
	 *  - {@link Exception#printStackTrace(PrintStream)}.
	 * @param ex The Exception whose stack trace is to be logged.
	 */
	public void logStackTrace(Exception ex) {
		String timestamp = new Date().toString().substring(0, 19);
		Thread t = Thread.currentThread();
		StackTraceElement elem = t.getStackTrace()[2];
		String fname = "Thread[" + t.getName() + "]: " + elem.getClassName() + "." + elem.getMethodName();
		try {
			lock.lock();
			if (!closed) {
				stream.println(String.format(errstr, timestamp, fname, "Exception caught: {"));
				ex.printStackTrace(stream);
				stream.println("}");
			}
		} finally { lock.unlock(); }
	}
		
	/**
	 * Closes this logger by releasing the underlying PrintStream (unless it is {@link System#out}
	 * or {@link System#err}).
	 */
	public void close() throws IOException {
		try {
			lock.lock();
			if (!closed) closed = true; else return;
			if (!stream.equals(System.out) && !stream.equals(System.err)) stream.close();
		} finally { lock.unlock(); }
	}
	
	public boolean isClosed() { try { lock.lock(); return closed; } finally { lock.unlock(); } }
}