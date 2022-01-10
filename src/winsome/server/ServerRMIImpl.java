package winsome.server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

import winsome.annotations.NotNull;
import winsome.common.rmi.*;
import winsome.util.*;

/**
 * Implementation of {@link ServerRMI}.
 * @author Salvatore Correnti
 */
final class ServerRMIImpl extends UnicastRemoteObject implements ServerRMI {
	
	private static final long serialVersionUID = 2111662808782298914L;
	
	/** Map of users -> client rmi handler for that user (i.e., valid only for logged in users). */
	@NotNull
	private final ConcurrentMap<String, ClientRMI> clients;
	@NotNull
	private final WinsomeServer server;
	
	public ServerRMIImpl(WinsomeServer server) throws RemoteException {
		super();
		Common.notNull(server);
		this.server = server;
		this.clients = new ConcurrentHashMap<>();
	}
	
	/**
	 * Register the given user.
	 * @param username Username.
	 * @param password Password.
	 * @param tags List of tags.
	 * @return A pair (true, message) on success, (false, message) on failure. The message is the analogous
	 *  of the initial text message sent by the server for the other request.
	 * @throws RemoteException On RMI errors.
	 * @throws NullPointerException If any of the parameters is null or tags contains a null value.
	 * @throws IllegalArgumentException On wrong number of tags.
	 */
	public synchronized Pair<Boolean, String> register(String username, String password, List<String> tags)
		throws RemoteException {
		Common.notNull(username, password);
		Common.collectionNotNull(tags);
		Common.allAndArgs(tags.size() >= 1, tags.size() <= 5);
		return this.server.register(username, password, tags);
	}
	
	/**
	 * Register the given user to the followers notifying service.
	 * @param username Username.
	 * @param client Client RMI object.
	 * @return true if user is not already registered, false otherwise.
	 */
	public boolean followersRegister(String username, ClientRMI client) throws RemoteException {
		Common.notNull(username, client);
		synchronized (this.clients) { return (this.clients.putIfAbsent(username, client) == null); }
	}
	
	/**
	 * Unregister the given user from the followers notifying service.
	 * @parma username Username.
	 * @return true if user is registered, false otherwise.
	 */
	public boolean followersUnregister(String username) throws RemoteException {
		Common.notNull(username);
		synchronized (this.clients) { return (this.clients.remove(username) != null); }
	}
		
	boolean addFollower(String follower, String followed, List<String> tags) throws RemoteException { //Tag di chi segue!
		Common.notNull(follower, followed, tags);
		ClientRMI client;
		synchronized (this.clients) {
			client = this.clients.get(followed);
			return (client != null ? client.addFollower(follower, tags) : true);
		}
	}
	
	boolean removeFollower(String follower, String followed) throws RemoteException {
		Common.notNull(follower, followed);
		ClientRMI client;
		synchronized (this.clients) {
			client = this.clients.get(followed);
			return (client != null ? client.removeFollower(follower) : true);
		}
	}
}