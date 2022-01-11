package winsome.server.data;

import java.util.*;
import java.util.concurrent.locks.*;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

import winsome.annotations.NotNull;
import winsome.server.ServerUtils;
import winsome.util.*;

/**
 * Winsome posts.
 * @author Salvatore Correnti
 */
public final class Post implements Indexable<Long>, Comparable<Post> {
	
	/** Id generator for posts. */
	private static IDGen gen = null;
		
	public static final Type TYPE = new TypeToken<Post>() {}.getType();
		
	/**
	 * Converts a string representing a rate into its boolean correspondent.
	 * @param vote The vote to convert to boolean.
	 * @return true if (vote == "+1"), false if (vote == "-1").
	 * @throws DataException If vote != +1/-1.
	 */
	public static boolean getVote(String vote) throws DataException {
		if (vote.equals(ServerUtils.LIKE)) return true;
		else if (vote.equals(ServerUtils.DISLIKE)) return false;
		else throw new DataException(DataException.INV_VOTE);
	}
	
	/* These fields are immutable and can be accessed without synchronization */
	private final long idPost;
	@NotNull
	private final String title, content, author;
	
	/* These fields should be accessed in read/write mode */
	private final NavigableMap<String, Boolean> votes;
	private transient ReentrantReadWriteLock voteLock;
	
	/* This field should be accessed in read/write mode */
	private final Map<String, NavigableSet<Comment>> comments;
	private transient ReentrantReadWriteLock commentLock;
	
	@NotNull
	private NavigableSet<String> rewinners;
	
	@NotNull
	//private NavigableSet<Action> actions;
	private double iteration;
	
	/** Sets the id generator to the given one (has effect only once e.g. after deserialization of the server) */
	public synchronized static void setGen(IDGen gen) {	if (Post.gen == null) Post.gen = gen; }
	
	public synchronized boolean isDeserialized() {
		return (voteLock != null && commentLock != null);
	}
	
	/**
	 * Restores transient fields after deserialization from JSON.
	 * @throws DeserializationException On failure.
	 */	
	public synchronized void deserialize() throws DeserializationException {
		if (voteLock == null) voteLock = new ReentrantReadWriteLock();
		if (commentLock == null) commentLock = new ReentrantReadWriteLock();
	}
	
	/**
	 * @throws DataException If Post.gen == null.
	 */
	public Post(String title, String content, User author) throws DataException {
		Common.notNull(title, content, author);
		if (Post.gen == null) throw new DataException(DataException.POST_NULLGEN);
		this.idPost = Post.gen.nextId();
		this.title = title;
		this.content = content;
		this.author = author.key();
		this.votes = new TreeMap<>();
		this.voteLock = new ReentrantReadWriteLock();
		this.comments = new HashMap<>();
		this.commentLock = new ReentrantReadWriteLock();
		this.rewinners = new TreeSet<>();
		this.iteration = 1.0;
		//this.actions = new TreeSet<>();
	}
	
	/* No sync need */
	public Long key() { return this.idPost; }

	/* This method does NOT need synchronization (only immutable fields) */
	/** @return A list containing post info for {@link User#getBlog()} and {@link User#getFeed()}. */
	@NotNull
	public List<String> getPostInfo() {
		return Common.toList(Long.toString(idPost), author, title);
	}
	
	/* No sync need */
	public String getAuthor() { return new String(author); }
	
	/**
	 * Formats an entry in the map {@link #comments} into a list of string of the form "  author: comment".
	 * @param entry An entry in {@link #comments}.
	 * @return A list of string as specified above.
	 */
	@NotNull
	private List<String> formatComments(Map.Entry<String, NavigableSet<Comment>> entry){
		List<String> result = new ArrayList<>();
		Iterator<Comment> iter = entry.getValue().iterator();
		String authStr = String.format("  %s: ", entry.getKey());
		while (iter.hasNext()) {
			StringBuilder sb = new StringBuilder();
			sb.append(authStr + iter.next().getContent());
			result.add(sb.toString());
		}
		return result;
	}
	
	/**
	 * Adds a rate to the current post.
	 * @param user Username of the user that is adding rate.
	 * @param like If true, a positive rate, otherwise a negative one.
	 * @return true if this post has not been rated before by user, false otherwise.
	 */
	public boolean addRate(String user, boolean like) {
		Common.notNull(user);
		try{
			voteLock.writeLock().lock();
			return (this.votes.putIfAbsent(user, like) == null);
		} finally { voteLock.writeLock().unlock(); }
	}
	
	/**
	 * Adds a comment with given content.
	 * @param author Author of the comment.
	 * @param content Content of the comment.
	 * @return The number of comments that this post has after having added this one.
	 *  NOTE: This method does NEVER return a negative value.
	 * @throws DataException On failure in adding comments (same author of the post,
	 *  error when adding comment).
	 *  @throws NullPointerException If author == null or content == null.
	 *  @throws IllegalArgumentException If author or content are empty.
	 */
	public int addComment(String author, String content) throws DataException {
		Common.notNull(author, content);
		Common.allAndArgs(author.length() > 0, content.length() > 0);
		if (author.equals(this.author)) throw new DataException(DataException.SAME_AUTHOR);
		NavigableSet<Comment> set;
		try {
			commentLock.writeLock().lock();
			if (this.comments.get(author) == null) {
				set = new TreeSet<>();
				this.comments.put( new String(author), set );
			} else set = this.comments.get(author);
			if (! set.add(new Comment(author, this.idPost, content)) ) throw new DataException(DataException.UNADD_COMMENT);
			else return set.size();
		} finally { commentLock.writeLock().unlock(); }
	}
	
	/**
	 * @return A list of strings of the form { title, content, likes, dislikes, (comments)}, where comments
	 * are sorted in ascending order by username of their authors.
	 */
	@NotNull
	public List<String> getPostData() {
		int[] votes = new int[2];
		
		try {
			voteLock.readLock().lock();
			for (String user : this.votes.keySet()) {
				votes[this.votes.get(user) ? 0 : 1]++;
			}
		} finally { voteLock.readLock().unlock(); }
		
		List<String> result = 
				Common.toList(title, content, Integer.toString(votes[0]), Integer.toString(votes[1]));
		
		try {
			commentLock.readLock().lock();
			for (Map.Entry<String, NavigableSet<Comment>> entry : comments.entrySet()) {
				result.addAll(this.formatComments(entry));
			}
		} finally { commentLock.readLock().unlock(); }
		
		return result;
	}
	
	/**
	 * Rewins the post for user, i.e. adds this post to the blog of user.
	 * @param user Username of the "rewinner".
	 * @return true if user is not the author of the post and has not already rewon this post.
	 * @throws DataException If user is also the author of the post.
	 */
	public boolean rewin(String user) throws DataException {
		if (user.equals(this.author)) throw new DataException(DataException.SAME_AUTHOR);
		String copy = new String(user);
		synchronized (rewinners) { return this.rewinners.add(copy); }
	}
	
	@NotNull
	public String toString() {
		try {
			if (voteLock != null && commentLock != null) {
				voteLock.readLock().lock();
				commentLock.readLock().lock();
			}
			return String.format("%s : %s", this.getClass().getSimpleName(), Serialization.GSON.toJson(this));
		} finally {
			if (voteLock != null && commentLock != null)
				{ voteLock.readLock().unlock(); commentLock.readLock().unlock(); }
		}
	}
	
	public double getIteration() { return this.iteration; }
	
	public void setIteration(double iter) { Common.allAndArgs(iter >= 0); this.iteration = iter; }
		
	public int compareTo(Post other) {
		if (this.idPost == other.idPost) return 0;
		else return (this.idPost > other.idPost ? 1 : -1);
	}
}