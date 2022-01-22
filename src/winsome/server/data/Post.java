package winsome.server.data;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import com.google.gson.*;
import com.google.gson.stream.*;

import winsome.annotations.NotNull;
import winsome.server.ServerUtils;
import winsome.util.*;

/**
 * Winsome posts.
 * @author Salvatore Correnti
 */
public final class Post implements Indexable<Long>, Comparable<Post> {
	
	/** Id generator for posts. */
	private static AtomicLong gen = null;
	
	public static final Gson gson() { return Serialization.GSON; }
	
	public static final Exception jsonSerializer(JsonWriter writer, Post post) {
		Common.notNull(writer, post);
		try {
			writer.beginObject();
			Serialization.writeFields(gson(), writer, false, post, "idPost", "title", "content", "author");
			
			Serialization.writeNameObject(writer, false, "votes.processed", post.votes.getKey(), Integer.class);
			Serialization.writeColl(writer, false, post.votes.getValue(), "votes", Rate.class);
			
			Collection< Pair<Integer, List<Comment>> > procdComms = post.comments.values();
			
			List<Integer> procd = new ArrayList<>();
			CollectionsUtils.remap(procdComms, procd, Pair::getKey);
			Serialization.writeColl(writer, true, procd, "comments.processed", Integer.class);
			
			
			writer.name("comments").beginObject();
			for (Map.Entry< String, Pair<Integer, List<Comment>> > entry : post.comments.entrySet()) {
				writer.name(entry.getKey()).beginArray();
				for (Comment comm : entry.getValue().getValue()) {
					Exception ex = Comment.jsonSerializer(writer, comm);
					if (ex != null) return ex;
				}
				writer.endArray();
			}
			writer.endObject();
			
			Serialization.writeColl(writer, true, post.rewinners, "rewinners", String.class);
			Serialization.writeField(writer, false, post, "iteration", AtomicInteger.class);
			
			writer.endObject();
			return null;
		} catch (Exception ex) { return ex; }
	}
	
	public static final Post jsonDeserializer(JsonReader reader) {
		Common.notNull(reader);
		try {
			reader.beginObject();
			
			long idPost = Serialization.readNameObject(reader, "idPost", Long.class);
			String
				title = Serialization.readNameObject(reader, "title", String.class),
				content = Serialization.readNameObject(reader, "content", String.class),
				author = Serialization.readNameObject(reader, "author", String.class);
			
			Post post = new Post(idPost, title, content, author);
			
			post.votes.setKey( Serialization.readNameObject(reader, "votes.processed", Integer.class) );
			Serialization.readColl(reader, post.votes.getValue(), "votes", Rate.class);
			
			List<Integer> procd = new ArrayList<>();
			Serialization.readColl(reader, procd, "comments.processed", Integer.class);
			
			if (!reader.nextName().equals("comments")) throw new DeserializationException(DeserializationException.JSONREAD);
			reader.beginObject();
			for (int i = 0; i < procd.size(); i++) {
				String commAuthor = reader.nextName();
				List<Comment> comms = new ArrayList<>();
				reader.beginArray();
				while (reader.hasNext()) comms.add(Comment.deserialize(reader, idPost, commAuthor));
				post.comments.put( commAuthor, new Pair<>(procd.get(i), comms) );
				reader.endArray();
			}
			reader.endObject();
			
			Serialization.readColl(reader, post.rewinners, "rewinners", String.class);
			Serialization.readField(reader, post, "iteration", AtomicInteger.class);
			
			reader.endObject();
			return post;
		} catch (Exception ex) { ex.printStackTrace(); return null; }
	}
	
	/**
	 * Converts a string representing a rate into its boolean correspondent.
	 * @param rate The vote to convert to boolean.
	 * @return true if (vote == "+1"), false if (vote == "-1").
	 * @throws DataException If vote != +1/-1.
	 */
	public static boolean getRate(String rate) throws DataException {
		if (rate.equals(ServerUtils.LIKE)) return true;
		else if (rate.equals(ServerUtils.DISLIKE)) return false;
		else throw new DataException(DataException.INV_VOTE);
	}
	
	/* These fields are immutable and can be accessed without synchronization */
	private final long idPost;
	@NotNull
	private final String title, content, author;
	
	/* These fields should be accessed in read/write mode */
	private Pair<Integer, List<Rate>> votes;
	private transient ReentrantReadWriteLock voteLock;
	
	/* This field should be accessed in read/write mode */
	private NavigableMap<String, Pair<Integer,List<Comment>> > comments;
	private transient ReentrantReadWriteLock commentLock;
	
	@NotNull
	private NavigableSet<String> rewinners;
	
	@NotNull
	private AtomicInteger iteration;
	
	/** Sets the id generator to the given one (has effect only once e.g. after deserialization of the server) */
	public synchronized static void setGen(AtomicLong gen) { if (Post.gen == null) Post.gen = gen; }
		
	private Post(long idPost, String title, String content, String author) throws DataException {
		this.idPost = idPost;
		this.title = title;
		this.content = content;
		this.author = author;
		this.votes = new Pair<>(0, new ArrayList<>());
		this.voteLock = new ReentrantReadWriteLock();
		this.comments = new TreeMap<>();
		this.commentLock = new ReentrantReadWriteLock();
		this.rewinners = new TreeSet<>();
		this.iteration = new AtomicInteger(1);
	}
	
	/**
	 * @throws DataException If Post.gen == null.
	 */
	public Post(String title, String content, User author) throws DataException {
		Common.notNull(title, content, author);
		if (Post.gen == null) throw new DataException(DataException.POST_NULLGEN);
		this.idPost = Post.gen.getAndIncrement();
		this.title = title;
		this.content = content;
		this.author = author.key();
		this.votes = new Pair<>(0, new ArrayList<>());
		this.voteLock = new ReentrantReadWriteLock();
		this.comments = new TreeMap<>();
		this.commentLock = new ReentrantReadWriteLock();
		this.rewinners = new TreeSet<>();
		this.iteration = new AtomicInteger(1);
	}
	
	/* No sync need */
	public Long key() { return this.idPost; }

	/* This method does NOT need synchronization (only immutable fields) */
	/** @return A list containing post info for {@link User#getBlog()} and {@link User#getFeed()}. */
	@NotNull
	public List<String> getPostInfo() {
		return CollectionsUtils.toList(Long.toString(idPost), author, title);
	}
	
	/* No sync need */
	public String getAuthor() { return new String(author); }
	
	/**
	 * Formats an entry in the map {@link #comments} into a list of string of the form "  author: comment".
	 * @param entry An entry in {@link #comments}.
	 * @return A list of string as specified above.
	 */
	@NotNull
	private List<String> formatComments(String author, List<Comment> comments){
		List<String> result = new ArrayList<>();
		Iterator<Comment> iter = comments.iterator();
		String authStr = String.format("  %s: ", author);
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
			for (Rate v : votes.getValue()) if (v.getAuthor().equals(user)) return false;
			Rate v = (like ? Rate.newLike(user) : Rate.newDislike(user));
			return this.votes.getValue().add(v);
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
	public void addComment(String author, String content) throws DataException {
		Common.notNull(author, content);
		Common.allAndArgs(author.length() > 0, content.length() > 0);
		if (author.equals(this.author)) throw new DataException(DataException.SAME_AUTHOR);
		Pair<Integer, List<Comment>> pair;
		try {
			commentLock.writeLock().lock();
			if (this.comments.get(author) == null) {
				pair = new Pair<>(0, new ArrayList<>());
				this.comments.put( new String(author), pair );
			} else pair = this.comments.get(author);
			Comment cmm = new Comment(author, this.idPost, content);
			if (!pair.getValue().add(cmm) ) throw new DataException(DataException.UNADD_COMMENT);
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
			for (Rate v : this.votes.getValue()) {
				votes[v.isLike() ? 0 : 1]++;
			}
		} finally { voteLock.readLock().unlock(); }
		
		List<String> result = 
				CollectionsUtils.toList(title, content, Integer.toString(votes[0]), Integer.toString(votes[1]));
		
		try {
			commentLock.readLock().lock();
			for (Map.Entry< String, Pair<Integer, List<Comment>> > entry : comments.entrySet()) {
				result.addAll( this.formatComments( entry.getKey(), entry.getValue().getValue() ) );
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
	
	public double getRewardData(List<Rate> recVotes, Map< String, Pair<Integer, List<Comment>> > recComms, long timeout){
		Common.notNull(recVotes, recComms);
		Common.allAndArgs(timeout >= 0L);
		Pair<Integer, List<Comment>> pair;
		List<Comment> aux;
		double res = (double)iteration.getAndIncrement();
		try {
			voteLock.readLock().lock();
			commentLock.readLock().lock();
			int voteIndex, commIndex;
			
			voteIndex = votes.getKey();
			List<Rate> vs = votes.getValue();
			for (int i = voteIndex; i < vs.size(); i++) {
				Rate v = vs.get(i);
				if (v.getTime() > timeout) break;
				else { voteIndex++; recVotes.add(v); }
			}
			votes.setKey(voteIndex);
			
			for (String author : comments.keySet()) {
				pair = comments.get(author);
				commIndex = pair.getKey();
				aux = pair.getValue();
				if (commIndex >= aux.size()) continue; //TODO Exclude no new people commenting
				pair = new Pair<>(aux.size(), new ArrayList<>());
				for (int i = commIndex; i < aux.size(); i++) {
					Comment c = aux.get(i);
					if (c.getTime() > timeout) break;
					else { commIndex++; pair.getValue().add(c); }
				}
				comments.get(author).setKey(commIndex);
				recComms.put(author, pair);
			}
			
			return res;
		} finally {
			commentLock.readLock().unlock();
			voteLock.readLock().unlock();
		}
	}
	
	@NotNull
	public String toString() {
		String cname = this.getClass().getSimpleName();
		return String.format( "%s: %s", cname, 
			Common.toString(this, gson(), Post::jsonSerializer, voteLock.readLock(), commentLock.readLock()) );
	}

	public int compareTo(Post other) {
		if (this.idPost == other.idPost) return 0;
		else return (this.idPost > other.idPost ? 1 : -1);
	}
}