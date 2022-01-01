package winsome.server.data;

import java.util.*;
import java.util.concurrent.locks.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

import winsome.util.*;

public final class Post implements Indexable<Long> {
		
	private static IDGen gen = null;
		
	public static final Type TYPE = new TypeToken<Post>() {}.getType();
	
	//TODO These fields are immutable and can be accessed without synchronization
	private final long idPost;
	private final String title;
	private final String content;
	private final String author;

	//TODO This field is mutable but is accessed only by get and set methods and thus they can be sync'd privately
	private double value;
	private transient ReentrantLock valLock;
	
	//TODO These fields should be accessed in read/write mode
	private final NavigableSet<String> likes;
	private final NavigableSet<String> dislikes;
	private transient ReentrantReadWriteLock voteLock;
	
	//TODO This field should be accessed in read/write mode (with specific operations)
	private final Map<String, SortedSet<Comment>> comments;
	private transient ReentrantReadWriteLock commentLock;
	
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
		Common.notNull(title, content, author, Post.gen);
		this.idPost = Post.gen.nextId();
		this.title = Common.dequote(title);
		this.content = Common.dequote(content);
		this.value = 0.0;
		this.valLock = new ReentrantLock();
		this.author = author.key();
		this.likes = new TreeSet<>();
		this.dislikes = new TreeSet<>();
		this.voteLock = new ReentrantReadWriteLock();
		this.comments = new HashMap<>();
		this.commentLock = new ReentrantReadWriteLock();
	}
	
	//TODO No sync need
	public Long key() { return this.idPost; }

	//TODO This method does NOT need synchronization (only immutable fields)
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
			NavigableSet<String> voteSet = (like ? likes : dislikes);
			return voteSet.add(user);
		} finally { voteLock.writeLock().unlock(); }
	}
	
	public Integer addComment(String author, String content) {
		Common.notNull(author, content);
		Common.andAllArgs(author.length() > 0, content.length() > 0);
		if (author.equals(this.author)) return null; //For security
		SortedSet<Comment> set;
		try {
			commentLock.writeLock().lock();
			if (this.comments.get(author) == null) {
				set = new TreeSet<>();
				this.comments.put( new String(author), set );
			} else set = this.comments.get(author);
			if (! set.add(new Comment(author, this.idPost, content)) ) return null;
			else return Integer.valueOf(set.size());
		} finally { commentLock.writeLock().unlock(); }
	}
	
	public List<String> getPostData(){
		int[] votes = new int[2];
		
		try {
			voteLock.readLock().lock();
			votes[0] = likes.size();
			votes[1] = dislikes.size();
		} finally { voteLock.readLock().unlock(); }
		
		List<String> result = 
				Arrays.asList(title, content, Integer.toString(votes[0]), Integer.toString(votes[1]));
		
		try {
			commentLock.readLock().lock();
			for (Map.Entry<String, SortedSet<Comment>> entry : comments.entrySet())
				{ result.addAll(this.formatComments(entry)); }
		} finally { commentLock.readLock().unlock(); }
		
		return result;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
			valLock.lock();
			voteLock.readLock().lock();
			commentLock.readLock().lock();
			Field[] fields = this.getClass().getDeclaredFields();
			boolean first = false;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if ( (f.getModifiers() & Modifier.STATIC) == 0 ) {
					sb.append( (first ? ", " : "") + f.getName() + " = " + f.get(this) );
					if (!first) first = true;
				}
			}
			sb.append("]");
			return sb.toString();		
		} catch (IllegalAccessException ex) { return null; }
		finally { valLock.unlock(); voteLock.readLock().unlock(); commentLock.readLock().unlock(); }
	}
}