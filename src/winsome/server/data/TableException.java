package winsome.server.data;

public final class TableException extends Exception {

	private static final long serialVersionUID = -4251074604690913841L;

	public static final String
		TABLE_ADD = "Error when adding element to table",
		TABLE_REMOVE = "Error when removing element from table",
		TABLE_UPDATE = "Error when updating element";
	
	public TableException() { super(); }

	public TableException(String message) { super(message); }

	public TableException(Throwable cause) { super(cause); }

	public TableException(String message, Throwable cause) { super(message, cause); }

	public TableException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}