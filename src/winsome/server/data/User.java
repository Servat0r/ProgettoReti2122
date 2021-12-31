package winsome.server.data;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

import winsome.util.*;

public final class User implements Indexable<String> {

	private static final int NUM_RCHARS = 8;
	private static final int RCHAR_MIN = Math.min('A', 'a');
	private static final int RCHAR_MAX = Math.max('Z', 'z');
	
	public static final Type TYPE = new TypeToken<User>() {}.getType();
	
	//TODO Immutable fields (no sync)
	private final String username;
	private Wallet wallet;
	private final String pwAppend; //String concat'd with password for generating hashStr
	private final String hashStr;
	private final List<String> tags;
	
	private final Index<String, User> following;
	private final Index<String, User> followers;
	private final Index<Long, Post> blog;
		
	private transient Table<Long, Post> posts;
	
	private static final User min(User u1, User u2) {
		return (u1.username.compareTo(u2.username) <= 0 ? u1 : u2);
	}

	private static final User max(User u1, User u2) {
		return (u1.username.compareTo(u2.username) <= 0 ? u2 : u1);
	}

	private Post feedSearch(long idPost) {
		List<User> iter = this.following.getAll();
		Post p = null;
		for (User user : iter) {
			if ((p = user.blog.get(idPost)) != null) break;
		}
		return p;
	}

	public static final int addFollower(User follower, User followed) {
		Common.notNull(follower, followed);
		if (follower.equals(followed)) return -1;
		Index<String, User> i = follower.following();
		if ( i.contains(followed.key()) ) return 1;
		else {
			User umin = min(follower, followed), umax = max(follower, followed);
			synchronized (umin) {
				synchronized (umax) {
					if (! follower.following.add(followed) ) return -1;
					if (! followed.followers.add(follower) ) return -1;
				}
			}
		}
		return 0;
	}

	public static final int removeFollower(User follower, User followed) {
		Common.notNull(follower, followed);
		if (follower.equals(followed)) return -1;
		Index<String, User> i = follower.following;
		if ( !i.contains(followed.key()) ) return 1;
		else {
			User umin = min(follower, followed), umax = max(follower, followed);
			synchronized (umin) {
				synchronized (umax) {
					if (! follower.following.remove(followed.key()) ) return -1;
					if (! followed.followers.remove(follower.key()) ) return -1;
				}
			}
		}
		return 0;
	}

	public User(String username, String password, Table<Long, Post> posts, String ...tags) throws NoSuchAlgorithmException {
		Common.notNull(username, password, posts, tags);
		Common.checkAll(username.length() > 0, password.length() > 0, tags.length >= 1, tags.length <= 5);
		this.username = username;
		this.wallet = new Wallet();
		this.tags = Arrays.asList(tags);
		this.followers = new Index<>();
		this.following = new Index<>();
		this.blog = new Index<>();
		this.posts = posts;
		Random r = new Random(System.currentTimeMillis());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < NUM_RCHARS; i++) sb.append((char)(r.nextInt(RCHAR_MAX - RCHAR_MIN) + RCHAR_MIN));
		this.pwAppend = sb.toString();
		this.hashStr = Hash.bytesToHex(Hash.sha256(password + pwAppend));
	}
		
	public synchronized boolean deserialize(Table<String, User> users, Table<Long, Post> posts) {
		Common.notNull(users, posts);
		if (!following.deserialize(users) || !followers.deserialize(users)) return false;
		if (posts == null) { this.posts = posts; if (!blog.deserialize(posts)) return false; }
		return true;
	}
	
	public boolean checkPassword(String password) throws NoSuchAlgorithmException {
		Common.notNull(password);
		String h = Hash.bytesToHex(Hash.sha256(password + pwAppend));
		return this.hashStr.equals(h);
	}
	
	public String key() { return new String(username); }
	public Wallet wallet() { return wallet; }
	public String pwAppend() { return new String(pwAppend); }
	public String hashStr() { return new String(hashStr); }
	public List<String> tags(){ return Collections.unmodifiableList(tags); }
	public Index<String, User> following(){ return following; }
	public Index<String, User> followers(){ return followers; }
	public Index<Long, Post> blog(){ return blog; }

	public ConcurrentMap<String, List<String>> getFollowing() { //list following
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		List<User> users = following.getAll();
		for (User u : users) result.put(u.key(), u.tags());
		return result;
	}
		
	//TODO list users sarà implementato nel server attraverso la tagsMap
	
	public List<String> getBlog() { //blog
		List<Post> posts = this.blog.getAll();
		List<String> result = new ArrayList<>();
		for (Post p : posts) result.add(p.getPostInfo());
		return result;
	}
	
	public List<String> getFeed() { //show feed
		List<User> users = this.following.getAll();
		List<String> result = new ArrayList<>();
		List<Post> posts;
		for (User u : users) {
			posts = u.blog.getAll();
			for (Post p : posts) result.add(p.getPostInfo());
		}
		return result;
	}
	
	public List<String> getPost(long idPost) { //show post <idPost>
		Common.checkAll(idPost > 0);
		Post p = this.blog.get(idPost);
		if (p == null) p = this.posts.get(idPost);
		return (p != null ? p.getPostData() : null);
	}
	
	public boolean addComment(long idPost, String content) throws IllegalAccessException { //comment <idPost> <comment>
		Common.checkAll(idPost > 0, content != null);
		Post p;
		if ( (p = this.feedSearch(idPost)) != null ) return p.addComment(key(), content);
		else throw new IllegalAccessException();
	}
	
	public long createPost(String title, String content) { //post <title> <content>
		Post p = new Post(title, content, this);
		if (this.posts.putIfAbsent(p) != null) return -1;
		if (!this.blog.add(p)) {
			if (this.posts.remove(p.key()) == null) throw new IllegalStateException();
			return -1;
		}
		return p.key();
	}
	
	public void deletePost(long idPost) throws TableException, IllegalAccessException { //delete <idPost>
		Common.checkAll(idPost > 0);
		Post p;
		if ( ((p = this.blog.get(idPost)) != null) && p.getAuthor().equals(key()) ) {
			if (this.posts.remove(idPost) == null) throw new TableException();
			if ( !this.blog.remove(idPost) ) throw new IllegalStateException();
		} else throw new IllegalAccessException();
	}
	
	public boolean ratePost(long idPost, boolean like) throws IllegalAccessException { //rate <idPost> <vote>
		Common.checkAll(idPost > 0);
		Post p;
		if ( (p = this.feedSearch(idPost)) == null) throw new IllegalAccessException();
		return p.addRate(key(), like);
	}
	
	public boolean rewinPost(long idPost) throws IllegalAccessException { //rewin <idPost>
		Common.checkAll(idPost > 0);
		Post p = null;
		if ((p = this.feedSearch(idPost)) == null) throw new IllegalAccessException();
		return this.blog.add(p);
	}
	
	//TODO Il valore in bitcoin viene calcolato dal server (aggiunto in cima alla lista qui ritornata)
	public List<String> getWallet() {
		List<String> result = Arrays.asList( Double.toString(this.wallet().value()) );
		if ( result.addAll(this.wallet().history()) ) return result;
		else return null;
	}
	
	public int hashCode() { return Objects.hash(key()); }

	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		User other = (User) obj;
		return Objects.equals(key(), other.key());
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName() + " [");
		try {
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
	}
}