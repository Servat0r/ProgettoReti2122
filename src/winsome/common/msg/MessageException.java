package winsome.common.msg;

public final class MessageException extends Exception {

	private static final long serialVersionUID = -5334173006139808696L;

	public MessageException() { super(); }

	public MessageException(String message) { super(message); }

	public MessageException(Throwable cause) { super(cause); }

	public MessageException(String message, Throwable cause) { super(message, cause); }

	public MessageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}