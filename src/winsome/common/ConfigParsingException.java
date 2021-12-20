package winsome.common;

public final class ConfigParsingException extends Exception {

	private static final long serialVersionUID = -7885513207117893768L;

	public ConfigParsingException() { super(); }

	public ConfigParsingException(String message) { super(message); }

	public ConfigParsingException(Throwable cause) { super(cause); }

	public ConfigParsingException(String message, Throwable cause) { super(message, cause); }

	public ConfigParsingException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) { super(message, cause, enableSuppression, writableStackTrace); }

}