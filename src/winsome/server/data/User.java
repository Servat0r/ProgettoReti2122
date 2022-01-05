package winsome.server.data;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import com.google.gson.reflect.TypeToken;

import winsome.annotations.NotNull;
import winsome.util.*;

public final class User implements Indexable<String>, Comparable<User> {
	
	private static final int NUM_RCHARS = 8, RCHAR_MIN = Math.min('A', 'a'), RCHAR_MAX = Math.max('Z', 'z');
	
	public static final Type TYPE = new TypeToken<User>() {}.getType();
	
	private transient boolean deserialized = false;
	private final String username;
	private transient Wallet wallet;
	private final String pwAppend; //String to be concatenated with password for generating hashStr
	private final String hashStr;
	private final List<String> tags;
	
	private final Index<String, User> following;
	private final Index<String, User> followers;
	private final Index<Long, Post> blog;
		
	private transient Table<Long, Post> posts;
	
	private static final User min(User u1, User u2) { return (u1.compareTo(u2) <= 0 ? u1 : u2); }
	
	private static final User max(User u1, User u2) { return (u1.compareTo(u2) <= 0 ? u2 : u1); }
	
	private Post feedSearch(long idPost) {
		List<User> iter;
		synchronized (this) { iter = this.following.getAll(); }
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
	
	public static User newUser(String username, String password, Table<String, User> users, Table<Long, Post> posts,
			Table<String, Wallet> wallets, List<String> tags) throws IllegalStateException {
		if (users.contains(username)) return null;
		User user = new User(username, password, posts, wallets, tags);
		return user;
	}
	
	private User(String username, String password, Table<Long, Post> posts, Table<String, Wallet> wallets, List<String> tags) {
		Common.notNull(username, password, posts, wallets, tags);
		Common.andAllArgs(username.length() > 0, password.length() > 0, tags.size() >= 1, tags.size() <= 5);
		
		this.deserialized = true;
		this.username = username;
		
		Random r = new Random(System.currentTimeMillis());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < NUM_RCHARS; i++) sb.append((char)(r.nextInt(RCHAR_MAX - RCHAR_MIN) + RCHAR_MIN));
		this.pwAppend = sb.toString();
		try { this.hashStr = Hash.bytesToHex(Hash.sha256(password + pwAppend)); }
		catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(); }
		
		this.wallet = new Wallet(username);
		if (!wallets.putIfAbsent(wallet)) throw new IllegalStateException();
		
		this.tags = tags;
		this.followers = new Index<>();
		this.following = new Index<>();
		this.blog = new Index<>();
		this.posts = posts;
	}
		
	public synchronized void deserialize(Table<String, User> users, Table<Long, Post> posts, Table<String, Wallet> wallets)
		throws DeserializationException {
		Common.notNull(users, posts, wallets);
		Debug.println(username);
		if (this.isDeserialized()) { Debug.println("Napalm 51"); return; }
		if (!users.isDeserialized() || !posts.isDeserialized() || !wallets.isDeserialized())
			throw new DeserializationException();
		following.deserialize(users); followers.deserialize(users);
		if (this.posts == null) this.posts = posts; else this.posts.deserialize();
		Debug.println(username);
		blog.deserialize(this.posts);
		if (this.wallet == null) this.wallet = wallets.get(username);
		this.wallet.deserialize();
		deserialized = true;
	}
	
	public synchronized boolean isDeserialized() { return deserialized; }
	
	public boolean checkPassword(String password) {
		Common.notNull(password);
		String h;
		try { h = Hash.bytesToHex(Hash.sha256(password + pwAppend)); }
		catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(); }
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
	
	@NotNull
	public ConcurrentMap<String, List<String>> getFollowers() { //list followers
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		List<User> users;
		synchronized (this) { users = followers.getAll(); } //For following static methods
		for (User u : users) result.put(u.key(), u.tags());
		return result;
	}
	
	@NotNull
	public ConcurrentMap<String, List<String>> getFollowing() { //list following
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		List<User> users;
		synchronized (this) { users = following.getAll(); } //For following static methods
		for (User u : users) result.put(u.key(), u.tags());
		return result;
	}
		
	@NotNull
	public List<String> getBlog() { //blog
		Debug.println(this.blog);
		List<Post> posts = this.blog.getAll();
		Debug.println(posts);
		List<String> result = new ArrayList<>();
		for (Post p : posts) result.add(p.getPostInfo());
		return result;
	}
	
	@NotNull
	public List<String> getFeed() { //show feed
		List<User> users;
		synchronized (this) {users = this.following.getAll();}
		List<String> result = new ArrayList<>();
		List<Post> posts;
		for (User u : users) {
			posts = u.blog.getAll();
			for (Post p : posts) result.add(p.getPostInfo());
		}
		return result;
	}
	
	@NotNull
	public List<String> getPost(long idPost) throws DataException { //show post <idPost>
		Common.andAllArgs(idPost > 0);
		Post p = this.blog.get(idPost);
		if (p == null) p = this.posts.get(idPost);
		if (p != null) { return p.getPostData(); }
		else { throw new DataException(DataException.POST_NEXISTS); }
	}
	
	public int addComment(long idPost, String content) throws DataException { //comment <idPost> <comment>
		Common.andAllArgs(idPost > 0, content != null);
		Post p;
		if ( (p = this.feedSearch(idPost)) != null ) return p.addComment(key(), content);
		else throw new DataException(DataException.NOT_IN_FEED);
	}
	
	public long createPost(String title, String content) { //post <title> <content>
		Debug.println("Title = %s, content = %s", title, content);
		Post p = new Post(title, content, this);
		if (!this.posts.putIfAbsent(p)) return -1;
		if (!this.blog.add(p)) {
			if (this.posts.remove(p.key()) == null) throw new IllegalStateException();
			return -1;
		}
		return p.key();
	}
	
	public void deletePost(long idPost) throws DataException { //delete <idPost>
		Common.andAllArgs(idPost > 0);
		Post p;
		if ( ((p = this.blog.get(idPost)) != null) && p.getAuthor().equals(key()) ) {
			if (this.posts.remove(idPost) == null) throw new DataException(DataException.TABLE_REMOVE);
			if ( !this.blog.remove(idPost) ) throw new IllegalStateException();
		} else throw new DataException(DataException.NOT_AUTHOR);
	}
	
	public boolean ratePost(long idPost, boolean like) throws DataException { //rate <idPost> <vote>
		Common.andAllArgs(idPost > 0);
		Post p;
		if ( (p = this.feedSearch(idPost)) == null) throw new DataException(DataException.NOT_IN_FEED);
		return p.addRate(key(), like);
	}
	
	public boolean rewinPost(long idPost) throws DataException { //rewin <idPost>
		Common.andAllArgs(idPost > 0);
		Post p = null;
		if ((p = this.feedSearch(idPost)) == null) throw new DataException(DataException.NOT_IN_FEED);
		return this.blog.add(p);
	}
	
	//TODO Il valore in bitcoin viene calcolato dal server (aggiunto in cima alla lista qui ritornata)
	@NotNull
	public List<String> getWallet() {
		List<String> result = Arrays.asList( Double.toString(this.wallet().value()) );
		result.addAll(this.wallet().history());
		return result;
	}
	
	public int hashCode() { return Objects.hash(key()); }

	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		User other = (User) obj;
		return Objects.equals(key(), other.key());
	}
	
	public String toString() { return String.format("%s : %s", this.getClass().getSimpleName(), Serialization.GSON.toJson(this)); }
	
	public int compareTo(User other) {
		Common.notNull(other);
		return this.key().compareTo(other.key());
	}
}