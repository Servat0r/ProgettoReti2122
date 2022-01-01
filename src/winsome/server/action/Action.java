package winsome.server.action;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Date;

import winsome.util.Common;

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
	private Integer ncomments; //Numero totale di commenti al post
	private Long endTime;
	
	private Action(ActionType type, String username, long idPost, Integer nComments) {
		Common.notNull(type, username);
		Common.andAllArgs(idPost >= 0);
		this.type = type;
		this.actor = username;
		this.idPost = idPost;
		this.ncomments = nComments;
		this.endTime = null;
	}
		
	public void setIdPost(long idPost) {
		Common.andAllArgs(idPost > 0);
		if (this.idPost < 0) this.idPost = idPost;
	}
	
	public static Action newCreate(String username) {
		return new Action(ActionType.CREATE, username, 0, null);
	}
	
	public static Action newDelete(String username, long idPost) {
		return new Action(ActionType.DELETE, username, idPost, null);
	}
	
	public static Action newLike(String username, long idPost) {
		return new Action(ActionType.LIKE, username, idPost, null);
	}
	
	public static Action newDislike(String username, long idPost) {
		return new Action(ActionType.DISLIKE, username, idPost, null);		
	}
	
	public static Action newComment(String username, long idPost, Integer nComments) {
		return new Action(ActionType.COMMENT, username, idPost, nComments);
	}
	
	public synchronized final void markEnded() {
		if (endTime == null) { endTime = Long.valueOf(System.currentTimeMillis()); }
	}
	
	public synchronized final boolean isEnded() { return (endTime != null); }
	
	public final ActionType getType() {return type;}
	public final String getActor() {return new String(actor);}
	public final long getIdPost() {return idPost;}
	public final String getAuthor() { return new String(author); }
	public final Long getEndTime() {return endTime;}
	public final Integer getNComments() {return ncomments;}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					Object obj = f.get(this);
					if (f.getName().contains("Time")) obj = new Date((long)obj);
					sb.append( (first ? ", " : "") + f.getName() + " = " + obj );
					if (!first) first = true;
				}
			}
		} catch (IllegalAccessException ex) { return null; }
		sb.append("]");
		return sb.toString();		
	}	
}