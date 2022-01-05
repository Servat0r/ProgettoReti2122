package winsome.server;

import winsome.annotations.NotNull;

final class Result {
	
	public static final String fmt = "Thread[%s] # %s";
	
	//success, connreset, ioerror, illarg
	public static final int
		SUCCESS = 0,
		GENERROR = 1,
		MSGERROR = 2,
		CONNRESET = 3,
		IOERROR = 4,
		INTERRUPT = 5,
		DATA = 6,
		ILLARG = 7,
		UNDEFINED = -1;
	
	@NotNull
	private final String thread, fname;
	private final int result;
	private Object attachment;
	
	public static Result newSuccess(Object obj) { return new Result(SUCCESS, obj); }
	public static Result newSuccess() { return new Result(SUCCESS, null); }
	
	public static Result newGenError(Object obj) { return new Result(GENERROR, obj); }
	public static Result newGenError() { return new Result(GENERROR, null); }
		
	public static Result newMsgError(Object obj) { return new Result(MSGERROR, obj); }
	public static Result newMsgError() { return new Result(MSGERROR, null); }
	
	public static Result newConnReset(Object obj) { return new Result(CONNRESET, obj); }
	public static Result newConnReset() { return new Result(CONNRESET, null); }
	
	public static Result newIOError(Object obj) { return new Result(IOERROR, obj); }
	public static Result newIOError() { return new Result(IOERROR, null); }
	
	public static Result newInterrupt(Object obj) { return new Result(INTERRUPT, obj); }
	public static Result newInterrupt() { return new Result(INTERRUPT, null); }
	
	public static Result newData(Object obj) { return new Result(DATA, obj); }
	public static Result newData() { return new Result(DATA, null); }
		
	public static Result newIllArg(Object obj) { return new Result(ILLARG, obj); }
	public static Result newIllArg() { return new Result(ILLARG, null); }
		
	public static Result newUndef(Object obj) { return new Result(UNDEFINED, obj); }
	public static Result newUndef() { return new Result(UNDEFINED, null); }
	
	private Result(int result, Object attachment) {
		this.thread = Thread.currentThread().getName();
		this.fname = Thread.currentThread().getStackTrace()[2].getMethodName();
		this.result = result;
		this.attachment = attachment;
	}
	
	public final String getThread() { return thread; }	
	
	public final String getMethod() { return fname; }
	
	public final int getResult() { return result; }
	
	public synchronized final Object getAttachment() { return attachment; }
	
	public synchronized final void attach(Object obj) { this.attachment = obj; }
}