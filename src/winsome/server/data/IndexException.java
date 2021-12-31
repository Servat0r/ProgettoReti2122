package winsome.server.data;

public final class IndexException extends Exception {

	private static final long serialVersionUID = -1119727310188955533L;

	public IndexException() { super(); }

	public IndexException(String message) { super(message); }

	public IndexException(Throwable cause) { super(cause); }

	public IndexException(String message, Throwable cause) { super(message, cause); }

	public IndexException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}