package winsome.server.data;

/**
 * Checked Exception for cases in which an operation on data (users, posts, comments, wallets) fails.
 * @author Salvatore Correnti.
 *
 */
public final class DataException extends Exception {

	private static final long serialVersionUID = -4251074604690913841L;

	public static final String
		TABLE_ADD = "Error when adding element to table",
		TABLE_REMOVE = "Error when removing element from table",
		TABLE_UPDATE = "Error when updating element";
	
	public static final String
		NOT_IN_FEED = "Post is not in current user feed",
		NOT_AUTHOR = "Current User is not the author of the post",
		SAME_AUTHOR = "Current User is the author of the post",
		POST_NEXISTS = "No existing post with given id",
		POST_NULLGEN = "Id generator of the posts is NULL";
	
	public static final String
		UNRETRIEVE_HISTORY = "Unable to retrieve wallet history",
		UNRETRIEVE_COMMENTS = "Unable to retrieve post comments",
		UNADD_COMMENT = "Unable to add comment",
		UNREWIN_POST = "Unable to rewin post";
	
	public static final String
		INV_VOTE = "Invalid vote";
	
	public DataException() { super(); }

	public DataException(String message) { super(message); }

	public DataException(Throwable cause) { super(cause); }

	public DataException(String message, Throwable cause) { super(message, cause); }
}