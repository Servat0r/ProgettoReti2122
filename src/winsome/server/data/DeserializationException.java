package winsome.server.data;

/**
 * Checked Exception thrown when restoring values of transient fields of Post/Table/User/Wallet/WinsomeServer
 * objects fails.
 * @author Salvatore Correnti
 *
 */
public final class DeserializationException extends Exception {
	
	private static final long serialVersionUID = 1550834140708222183L;
	
	public static final String
		JSONREAD = "Error when reading JSON file";
	
	public DeserializationException() { super(); }
	
	public DeserializationException(String message) { super(message); }
	
	public DeserializationException(Throwable cause) { super(cause); }
	
	public DeserializationException(String message, Throwable cause) { super(message, cause); }
}