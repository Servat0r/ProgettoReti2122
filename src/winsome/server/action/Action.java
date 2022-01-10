package winsome.server.action;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * A class representing an action that influences reward calculations. An action is always
 *  referred to a single post and a single "actor" (i.e. the user) who commits it.
 * @author Salvatore Correnti
 * @see ActionRegistry
 */
public final class Action {

	/* 
	 * type -> tipo di azione
	 * actor -> autore dell'azione
	 * idPost -> id del post
	 * author -> autore del post
	 * ncomments -> #commenti di actor al post
	 * endTime -> tempo di conclusione dell'azione
	 */
	/** Type of action */
	private ActionType type;
	/** Who commits the action */
	@NotNull
	private String actor;
	private long idPost;
	/** Author of the post */
	private String author;
	/** Number of comments when the action is committed */
	private int ncomments;
	/** Milliseconds time (since the epoch) when the action is marked as ended */
	private long endTime;
	
	/**
	 * @throws NullPointerException If type == null or actor == null.
	 * @throws IllegalArgumentException If idPost < 0 or ncomments < 0.
	 */
	private Action(ActionType type, String actor, String author, long idPost, int ncomments) {
		Common.notNull(type, actor);
		Common.allAndArgs(idPost >= 0, ncomments >= 0);
		this.type = type;
		this.actor = actor;
		this.author = author;
		this.idPost = idPost;
		this.ncomments = ncomments;
		this.endTime = -1;
	}
	
	/** @see {@link #Action(ActionType, String, String, long, int)} */
	private Action(ActionType type, String actor, long idPost, int nComments)
	{ this(type, actor, null, idPost, nComments); }
		
	/** @see {@link #Action(ActionType, String, String, long, int)} */
	private Action(ActionType type, String actor, long idPost) { this(type, actor, null, idPost, 0); }
	
	public synchronized void setIdPost(long idPost) {
		Common.allAndArgs(idPost > 0);
		if (this.idPost <= 0) this.idPost = idPost;
	}
	
	public synchronized void setNComments(int nComments) {
		Common.allAndArgs(nComments >= 0);
		this.ncomments = nComments;
	}
	
	public synchronized void setAuthor(String author) {
		Common.notNull(author);
		this.author = author;
	}
	
	public static Action newCreatePost(String username) {
		return new Action(ActionType.CREATE, username, username, 0, 0);
	}
	
	public static Action newDeletePost(String username, long idPost) {
		return new Action(ActionType.DELETE, username, username, idPost, 0);
	}
	
	public static Action newRatePost(boolean like, String actor, String author, long idPost) {
		ActionType type = (like ? ActionType.LIKE : ActionType.DISLIKE);
		return new Action(type, actor, author, idPost, 0);
	}
		
	public static Action newAddComment(String actor, String author, long idPost) {
		return new Action(ActionType.COMMENT, actor, author, idPost, 0);
	}
	
	/**
	 * Marks the action as ended, i.e. sets {@link #endTime} to the current System time indicating that
	 *  the action has ended. Successive invocations of this method have no effect.
	 */
	public synchronized final void markEnded() {
		if (endTime < 0) { endTime = Long.valueOf(System.currentTimeMillis()); }
	}
	
	public synchronized final boolean isEnded() { return (endTime >= 0); }
	
	public final ActionType getType() {return type;}
	public final String getActor() {return new String(actor);}
	public final long getIdPost() {return idPost;}
	public final String getAuthor() { return new String(author); }
	public final Long getEndTime() {return endTime;}
	public final Integer getNComments() {return ncomments;}
	
	public String toString() { return Common.jsonString(this); }
}