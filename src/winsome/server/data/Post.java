package winsome.server.data;

import java.util.*;
import java.util.concurrent.locks.*;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

import winsome.annotations.NotNull;
import winsome.util.*;

public final class Post implements Indexable<Long>, Comparable<Post> {
		
	private static IDGen gen = null;
		
	public static final Type TYPE = new TypeToken<Post>() {}.getType();
	
	public static final String
		LIKE = "+1",
		DISLIKE = "-1";
	
	public static boolean getVote(String vote) throws DataException {
		if (vote.equals(LIKE)) return true;
		else if (vote.equals(DISLIKE)) return false;
		else throw new DataException(DataException.INV_VOTE);
	}
	
	//TODO These fields are immutable and can be accessed without synchronization
	private final long idPost;
	private final String title, content, author;

	//TODO This field is mutable but is accessed only by get and set methods and thus they can be sync'd privately
	private double value;
	private transient ReentrantLock valLock;
	
	//TODO These fields should be accessed in read/write mode
	private final NavigableMap<String, Boolean> votes;
	private transient ReentrantReadWriteLock voteLock;
	
	//TODO This field should be accessed in read/write mode (with specific operations)
	private final Map<String, SortedSet<Comment>> comments;
	private transient ReentrantReadWriteLock commentLock;
	
	//TODO
	private NavigableSet<String> rewinners;
	
	public synchronized static void setGen(IDGen gen) {	if (Post.gen == null) Post.gen = gen; }
	
	public synchronized boolean isDeserialized() {
		return (valLock != null && voteLock != null && commentLock != null);
	}
	
	public synchronized void deserialize() throws DeserializationException {
		if (valLock == null) valLock = new ReentrantLock();
		if (voteLock == null) voteLock = new ReentrantReadWriteLock();
		if (commentLock == null) commentLock = new ReentrantReadWriteLock();
	}
	
	public Post(String title, String content, User author) {
		Common.notNull(title, content, author);//, Post.gen);
		this.idPost = Post.gen.nextId();
		this.title = Common.dequote(title);
		this.content = Common.dequote(content);
		this.value = 0.0;
		this.valLock = new ReentrantLock();
		this.author = author.key();
		this.votes = new TreeMap<>();
		this.voteLock = new ReentrantReadWriteLock();
		this.comments = new HashMap<>();
		this.commentLock = new ReentrantReadWriteLock();
		this.rewinners = new TreeSet<>();
	}
	
	//TODO No sync need
	public Long key() { return this.idPost; }

	//TODO This method does NOT need synchronization (only immutable fields)
	@NotNull
	public String getPostInfo() {
		StringBuilder sb = new StringBuilder();
		String separ = Serialization.SEPAR;
		sb.append(this.idPost + separ + this.author + separ + Common.quote(this.title));
		return sb.toString();
	}
	
	//TODO No sync need
	public String getAuthor() { return new String(author); }

	public double getValue() {
		try { valLock.lock(); return value; } finally { valLock.unlock(); }
	}

	public void updateValue(double amount) {
		Common.andAllArgs(amount >= 0.0);
		try { valLock.lock(); this.value += amount; } finally { valLock.unlock(); }
	}
	
	@NotNull
	private List<String> formatComments(Map.Entry<String, SortedSet<Comment>> entry){
		List<String> result = new ArrayList<>();
		Iterator<Comment> iter = entry.getValue().iterator();
		String authStr = "\t" + entry.getKey() + ": ";
		while (iter.hasNext()) {
			StringBuilder sb = new StringBuilder();
			sb.append(authStr + iter.next().getContent());
			result.add(sb.toString());
		}
		return result;
	}
	
	public boolean addRate(String user, boolean like) {
		Common.notNull(user);
		try{
			voteLock.writeLock().lock();
			return (this.votes.putIfAbsent(user, like) == null);
		} finally { voteLock.writeLock().unlock(); }
	}
	
	/**
	 * 
	 * @param author
	 * @param content
	 * @return 
	 * @throws DataException 
	 */
	public int addComment(String author, String content) throws DataException {
		Common.notNull(author, content);
		Common.andAllArgs(author.length() > 0, content.length() > 0);
		if (author.equals(this.author)) throw new DataException(DataException.SAME_AUTHOR);
		SortedSet<Comment> set;
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
			for (Map.Entry<String, SortedSet<Comment>> entry : comments.entrySet()) {
				result.addAll(this.formatComments(entry));
			}
		} finally { commentLock.readLock().unlock(); }
		
		return result;
	}
	
	
	public boolean rewin(String user) throws DataException {
		if (user.equals(this.author)) throw new DataException(DataException.SAME_AUTHOR);
		String copy = new String(user);
		return this.rewinners.add(copy);
	}
	
	@NotNull
	public String toString() {
		try {
			if (valLock != null && voteLock != null && commentLock != null) {
				valLock.lock();
				voteLock.readLock().lock();
				commentLock.readLock().lock();
			}
			return String.format("%s : %s", this.getClass().getSimpleName(), Serialization.GSON.toJson(this));
		} finally {
			if (valLock != null && voteLock != null && commentLock != null)
				{ valLock.unlock(); voteLock.readLock().unlock(); commentLock.readLock().unlock(); }
		}
	}

	public int compareTo(Post other) {
		if (this.idPost == other.idPost) return 0;
		else return (this.idPost > other.idPost ? 1 : -1);
	}
}