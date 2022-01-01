package winsome.server.data;

public final class DeserializationException extends Exception {
	
	private static final long serialVersionUID = 1550834140708222183L;

	public DeserializationException() { super(); }

	public DeserializationException(String message) { super(message); }

	public DeserializationException(Throwable cause) { super(cause); }

	public DeserializationException(String message, Throwable cause) { super(message, cause); }

	public DeserializationException(String message, Throwable cause, boolean enableSuppression,
		boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}