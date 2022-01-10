package winsome.server.data;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import com.google.gson.reflect.TypeToken;

import winsome.annotations.NotNull;
import winsome.util.*;

/**
 * A Winsome user.
 * @author Salvatore Correnti
 */
public final class User implements Indexable<String>, Comparable<User> {
	
	private static final int NUM_RCHARS = 8, RCHAR_MIN = Math.min('A', 'a'), RCHAR_MAX = Math.max('Z', 'z');
	
	/**
	 * @param u1 First user.
	 * @param u2 Second user.
	 * @return The user with the minimum username.
	 */
	private static final User min(User u1, User u2) { return (u1.compareTo(u2) <= 0 ? u1 : u2); }
	
	/**
	 * @param u1 First user.
	 * @param u2 Second user.
	 * @return The user with the maximum username.
	 */
	private static final User max(User u1, User u2) { return (u1.compareTo(u2) <= 0 ? u2 : u1); }
	
	public static final Type TYPE = new TypeToken<User>() {}.getType();
	
	private transient boolean deserialized = false;
	@NotNull
	private final String username;
	private transient Wallet wallet;
	/** String to be concatenated with password for generating {@link #hashStr}. */
	@NotNull
	private final String pwAppend;
	/** Result of the SHA-256 hash function of the password concatenated with {@link #pwAppend}. */
	@NotNull
	private final String hashStr;
	@NotNull
	private final List<String> tags;
	
	private final Index<String, User> following;
	private final Index<String, User> followers;
	private final Index<Long, Post> blog;
	
	/** Reference to the post table of the server. */
	private transient Table<Long, Post> posts;
	
	/**
	 * Search for a post in feed that has the specified id.
	 * @param idPost Id of the post to search.
	 * @return The post with the given id on success, null if that post does not exist.
	 */
	private Post feedSearch(long idPost) {
		NavigableSet<User> set;
		synchronized (this) { set = this.following.getAll(); }
		Post p = null;
		for (User user : set) {
			if ((p = user.blog.get(idPost)) != null) break;
		}
		return p;
	}
	
	/**
	 * Marks the second user as followed by the first one, i.e. adds the second user to {@link #following} of the
	 * first one and the first one to {@link #followers} of the second one.
	 * @param follower The follower.
	 * @param followed the followed.
	 * @return 0 on success, 1 if follower is already following followed, -1 on failure or
	 *  if (follower == followed).
	 * @throws NullPointerException If any of {follower, followed} is null.
	 */
	public static final int addFollower(User follower, User followed) {
		Common.notNull(follower, followed);
		if (follower.equals(followed)) return -1;
		Index<String, User> i = follower.following();
		if ( i.contains(followed.key()) ) return 1;
		else {
			User umin = min(follower, followed), umax = max(follower, followed);
			synchronized (umin) {
				synchronized (umax) {
					String u1 = follower.key(), u2 = followed.key();
					if (! follower.following.add(new String(u2)) ) return -1;
					if (! followed.followers.add(new String(u1)) ) return -1;
				}
			}
		}
		return 0;
	}
	
	/**
	 * Removes the first user from the followers of the second one, i.e. removes the second user to
	 *  {@link #following} of the first one and the first one to {@link #followers} of the second one.
	 * @param follower The follower.
	 * @param followed the followed.
	 * @return 0 on success, 1 if follower is not following followed, -1 on failure or
	 *  if (follower == followed).
	 * @throws NullPointerException If any of {follower, followed} is null.
	 */
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
		User user = new User(username, password, users, posts, wallets, tags);
		return user;
	}
	
	/**
	 * @param username Username.
	 * @param password Password.
	 * @param users Table of users (usually the one of the server).
	 * @param posts Table of posts (usually the one of the server).
	 * @param wallets Table of wallets (usually the one of the server).
	 * @param tags Tags of the user (given when registering).
	 */
	private User(String username, String password, Table<String, User> users, Table<Long, Post> posts,
		Table<String, Wallet> wallets, List<String> tags) {
		Common.notNull(username, password, posts, wallets, tags);
		Common.allAndArgs(username.length() > 0, password.length() > 0, tags.size() >= 1, tags.size() <= 5);
		
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
		this.followers = new Index<>(users);
		this.following = new Index<>(users);
		this.blog = new Index<>(posts);
		this.posts = posts;
	}
	
	/**
	 * Restores transient fields after deserialization from JSON.
	 * @param users Table of users for deserialization (usually the one of the server).
	 * @param posts Table of posts for deserialization (usually the one of the server).
	 * @param wallets Table of wallets for deserialization (usually the one of the server).
	 * @throws DeserializationException On failure.
	 */
	public synchronized void deserialize(Table<String, User> users, Table<Long, Post> posts, Table<String, Wallet> wallets)
		throws DeserializationException {
		Common.notNull(users, posts, wallets);
		if (this.isDeserialized()) return;
		if (!users.isDeserialized() || !posts.isDeserialized() || !wallets.isDeserialized())
			throw new DeserializationException();
		following.deserialize(users); followers.deserialize(users);
		if (this.posts == null) this.posts = posts; else this.posts.deserialize();
		blog.deserialize(this.posts);
		if (this.wallet == null) this.wallet = wallets.get(username);
		this.wallet.deserialize();
		deserialized = true;
	}
	
	public synchronized boolean isDeserialized() { return deserialized; }
	
	/**
	 * Checks if the given password is correct by computing the SHA-256 on the concatenation
	 *  of password and {@link #pwAppend}.
	 * @param password Password to check.
	 * @return true on success, false on failure.
	 * @throws NullPointerException If password is null.
	 * @throws IllegalArgumentException If password is an empty string.
	 */
	public boolean checkPassword(String password) {
		Common.notNull(password); Common.allAndArgs(!password.isEmpty());
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
	
	/**
	 * Retrieves the set of the followers of the current user with their tags
	 *  as a ConcurrentMap.
	 * @return A ConcurrentMap such that for each entry e = (k, v), k is the
	 *  name of the user and v a list of its tags.
	 */
	@NotNull
	public ConcurrentMap<String, List<String>> getFollowers() { //list followers
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		NavigableSet<User> users;
		synchronized (this) { users = followers.getAll(); } //For following static methods
		for (User u : users) result.put(u.key(), u.tags());
		return result;
	}
	
	/**
	 * Retrieves the set of the users that the current user is following with their
	 *  tags as a ConcurrentMap.
	 * @return A ConcurrentMap such that for each entry e = (k, v), k is the
	 *  name of the user and v a list of its tags.
	 */
	@NotNull
	public ConcurrentMap<String, List<String>> getFollowing() { //list following
		ConcurrentMap<String, List<String>> result = new ConcurrentHashMap<>();
		NavigableSet<User> users;
		synchronized (this) { users = following.getAll(); } //For following static methods
		for (User u : users) result.put(u.key(), u.tags());
		return result;
	}
		
	/**
	 * Retrieves the blog of the current user as a list of formatted strings as got
	 *  by {@link Post#getPostInfo()}.
	 *  NOTE: The strings in the list follow the order of the posts by id.
	 * @return A list of formatted strings as described above.
	 */
	@NotNull
	public List<String> getBlog() { //blog
		NavigableSet<Post> posts = new TreeSet<>();
		posts.addAll(this.blog.getAll());
		List<String> result = new ArrayList<>();
		Iterator<Post> iter = posts.iterator();
		/* TODO NOTE: Posts will appear ordered by id to the client. */
		while (iter.hasNext()) result.addAll(iter.next().getPostInfo());
		return result;
	}
	
	/**
	 * Retrieves the feed of the current user as a list of formatted strings as got
	 *  by {@link Post#getPostInfo()}.
	 *  NOTE: The strings in the list follow the order of the posts by id.
	 * @return A list of formatted strings as described above.
	 */
	@NotNull
	public List<String> getFeed() { //show feed
		NavigableSet<User> users;
		synchronized (this) {users = this.following.getAll();}
		NavigableSet<Post> posts = new TreeSet<>();
		List<String> result = new ArrayList<>();
		for (User u : users) posts.addAll(u.blog.getAll());
		Iterator<Post> iter = posts.iterator();
		/* TODO NOTE: Posts will appear ordered by id to the client. */
		while (iter.hasNext()) result.addAll(iter.next().getPostInfo());
		return result;
	}
	
	/**
	 * Retrieves the info of the post specified by idPost as a list of strings
	 * { title, content, likes, dislikes, (comments) }.
	 * @param idPost Id of the post.
	 * @return A list of strings as specified above.
	 * @throws DataException If it does not exist a post with that id.
	 */
	@NotNull
	public List<String> getPost(long idPost) throws DataException { //show post <idPost>
		Common.allAndArgs(idPost > 0);
		Post p = this.blog.get(idPost);
		if (p == null) p = this.posts.get(idPost);
		if (p != null) { return p.getPostData(); }
		else { throw new DataException(DataException.POST_NEXISTS); }
	}
	
	/**
	 * Adds a comment to the post with idPost and the specified content.
	 * @param idPost Id of the post.
	 * @param content Content of the post.
	 * @return The number of comments to the post as specified in {@link Post#addComment(String, String)}.
	 * @throws DataException If post is not in user's feed.
	 */
	public int addComment(long idPost, String content) throws DataException { //comment <idPost> <comment>
		Common.allAndArgs(idPost > 0, content != null);
		Post p;
		if ( (p = this.feedSearch(idPost)) != null ) return p.addComment(key(), content);
		else throw new DataException(DataException.NOT_IN_FEED);
	}
	
	/**
	 * Creates a post with given title and content.
	 * @param title Title.
	 * @param content Content.
	 * @return The id of the created post on success, -1 on error.
	 * @throws DataException If Post.gen is null.
	 * @throws IllegalStateException If post does not exist in table.
	 */
	public long createPost(String title, String content) throws DataException { //post <title> <content>
		Post p = new Post(title, content, this);
		if (!this.posts.putIfAbsent(p)) return -1;
		if (!this.blog.add(p.key())) {
			if (this.posts.remove(p.key()) == null) throw new IllegalStateException("Could not remove post");
			return -1;
		}
		return p.key();
	}
	
	/**
	 * Deletes the post with the given id.
	 * @param idPost Id of the post.
	 * @throws DataException On failure.
	 */
	public void deletePost(long idPost) throws DataException { //delete <idPost>
		Common.allAndArgs(idPost > 0);
		Post p;
		if ( ((p = this.blog.get(idPost)) != null) && p.getAuthor().equals(key()) ) {
			if (this.posts.remove(idPost) == null) throw new DataException(DataException.TABLE_REMOVE);
			if ( !this.blog.remove(idPost) ) throw new IllegalStateException();
		} else throw new DataException(DataException.NOT_AUTHOR);
	}
	
	/**
	 * Adds a rate to the post with the given id.
	 * @param idPost Id of the post.
	 * @param like If true, adds a positive rate, otherwise a negative one.
	 * @return The same as returned by {@link Post#addRate(String, boolean)}.
	 * @throws DataException If post is not in feed.
	 * @throws IllegalArgumentException If idPost <= 0.
	 */
	public boolean ratePost(long idPost, boolean like) throws DataException { //rate <idPost> <vote>
		Common.allAndArgs(idPost > 0);
		Post p;
		if ( (p = this.feedSearch(idPost)) == null) throw new DataException(DataException.NOT_IN_FEED);
		return p.addRate(key(), like);
	}
	
	/**
	 * Rewins the post with the given id.
	 * @param idPost Id of the post.
	 * @return true on success, false on error.
	 * @throws DataException If post is not in feed or on failure when rewinning.
	 * @throws IllegalArgumentException If idPost <= 0.
	 */
	public boolean rewinPost(long idPost) throws DataException { //rewin <idPost>
		Common.allAndArgs(idPost > 0);
		Post p = null;
		if ((p = this.feedSearch(idPost)) == null) throw new DataException(DataException.NOT_IN_FEED);
		boolean b1 = this.blog.add(p.key()), b2 = p.rewin(this.key());
		if (b1 && b2) return true;
		else if (!b1 && !b2) return false;
		else throw new DataException(DataException.UNREWIN_POST);
	}
	
	/**
	 * @return A list of string made up by {wallet wincoin value} concatenated with the history
	 *  as formatted by {@link Wallet#history()}.
	 */
	@NotNull
	public List<String> getWallet() {
		List<String> result = Common.toList( Double.toString(this.wallet().value()) );
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