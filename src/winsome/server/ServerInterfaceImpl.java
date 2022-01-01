package winsome.server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

import winsome.common.rmi.*;
import winsome.util.Common;

final class ServerInterfaceImpl extends UnicastRemoteObject implements ServerInterface {
	
	private static final long serialVersionUID = 2111662808782298914L;
	
	private final ConcurrentMap<String, ClientInterface> clients;
	private final WinsomeServer server;
	
	public ServerInterfaceImpl(WinsomeServer server) throws RemoteException {
		super();
		Common.notNull(server);
		this.server = server;
		this.clients = new ConcurrentHashMap<>();
	}
	
	public synchronized boolean register(String username, String password, List<String> tags) throws RemoteException {
		Common.notNull(username, password, tags); Common.andAllArgs(tags.size() >= 1, tags.size() <= 5);
		return this.server.registerUser(username, password, tags);
	}
	
	public boolean followersRegister(String username, ClientInterface client) throws RemoteException {
		Common.notNull(username, client);
		return (this.clients.putIfAbsent(username, client) == null);
	}
	
	public boolean followersUnregister(String username) throws RemoteException {
		Common.notNull(username);
		return (this.clients.remove(username) != null);
	}
	
	public boolean isRegistered(String username) throws RemoteException {
		Common.notNull(username);
		return this.clients.containsKey(username);
	}
	
	boolean addFollower(String follower, String followed, List<String> tags) throws RemoteException {
		Common.notNull(follower, followed, tags);
		ClientInterface client = this.clients.get(followed);
		return (client != null ? client.addFollower(follower, tags) : false);
	}
	
	boolean removeFollower(String follower, String followed) throws RemoteException {
		Common.notNull(follower, followed);
		ClientInterface client = this.clients.get(followed);
		return (client != null ? client.removeFollower(follower) : false);
	}
}