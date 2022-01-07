package winsome.server.data;

import java.io.*;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

import winsome.util.Common;

public final class Logger implements Closeable {

	private final PrintStream stream;
	private boolean closed;
	private final ReentrantLock lock;
	private final String logstr, errstr;
	
	public Logger(String logstr, String errstr, String filename) throws FileNotFoundException {
		Common.notNull(filename);
		this.stream = new PrintStream(filename);
		this.closed = false;
		this.lock = new ReentrantLock();
		this.logstr = logstr;
		this.errstr = errstr;
	}
	
	public Logger(String logstr, String errstr, PrintStream stream) { 
		Common.notNull(stream);
		this.stream = stream;
		this.closed = false;
		this.lock = new ReentrantLock();
		this.logstr = logstr;
		this.errstr = errstr;
	}
	
	public void log(String format, Object ...objs) {
		String timestamp = new Date().toString().substring(0, 19);
		StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
		String fname = "Thread[" + Thread.currentThread().getName() + "]: " + elem.getClassName() + "." + elem.getMethodName();
		String msg = String.format(format, objs);
		try {
			lock.lock();
			stream.println(String.format(logstr, timestamp, fname, msg));
		} finally { lock.unlock(); }
	}
	
	public void logStackTrace(Exception ex) {
		String timestamp = new Date().toString().substring(0, 19);
		Thread t = Thread.currentThread();
		StackTraceElement elem = t.getStackTrace()[2];
		String fname = "Thread[" + t.getName() + "]: " + elem.getClassName() + "." + elem.getMethodName();
		try {
			lock.lock();
			stream.println(String.format(errstr, timestamp, fname, "Exception caught: {"));
			ex.printStackTrace(stream);
			stream.println("}");
		} finally { lock.unlock(); }
	}
		
	public synchronized void close() throws IOException {
		if (!closed && !stream.equals(System.out) && !stream.equals(System.err))
			{ stream.close(); closed = true; }
	}
	
	public synchronized boolean isClosed() { return closed; }
}