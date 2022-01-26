package winsome.common.msg;

/**
 * Checked Exception thrown when attempting to create / read / write an inconsistent message.
 * @author Salvatore Correnti
 * @see Message
 */
public final class MessageException extends Exception {

	private static final long serialVersionUID = -5334173006139808696L;		
		
	public MessageException() { super(); }

	public MessageException(String message) { super(message); }

	public MessageException(Throwable cause) { super(cause); }

	public MessageException(String message, Throwable cause) { super(message, cause); }
}