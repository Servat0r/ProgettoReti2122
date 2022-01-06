package winsome.server.action;

import winsome.util.*;

public final class Action {

	/* 
	 * type -> tipo di azione
	 * actor -> autore dell'azione
	 * idPost -> id del post
	 * author -> autore del post
	 * ncomments -> #commenti di actor al post
	 * endTime -> tempo di conclusione dell'azione
	 */
	private ActionType type;
	private String actor;
	private long idPost;
	private String author;
	private int ncomments; //Numero totale di commenti al post
	private long endTime;
	
	private Action(ActionType type, String actor, String author, long idPost, int nComments) {
		Common.notNull(type, actor);
		Common.andAllArgs(idPost >= 0, nComments >= 0);
		this.type = type;
		this.actor = actor;
		this.author = author;
		this.idPost = idPost;
		this.ncomments = nComments;
		this.endTime = -1;
	}
	
	private Action(ActionType type, String actor, long idPost, int nComments)
	{ this(type, actor, null, idPost, nComments); }
		
	private Action(ActionType type, String actor, long idPost) { this(type, actor, null, idPost, 0); }
	
	public synchronized void setIdPost(long idPost) {
		Common.andAllArgs(idPost > 0);
		if (this.idPost <= 0) this.idPost = idPost;
	}
	
	public synchronized void setNComments(int nComments) {
		Common.andAllArgs(nComments >= 0);
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
	
	public String toString() {
		String jsond = Serialization.GSON.toJson(this, Action.class);
		String cname = this.getClass().getSimpleName();
		return String.format("%s: %s", cname, jsond);
	}
}