package winsome.client;

import java.rmi.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.*;

import winsome.common.rmi.ClientInterface;
import winsome.util.Common;

final class ClientInterfaceImpl extends UnicastRemoteObject implements ClientInterface {
	
	private static final long serialVersionUID = -6610109195985408710L;
	
	private WinsomeClient client;
	
	ClientInterfaceImpl(WinsomeClient client) throws RemoteException {
		Common.notNull(client);
		this.client = client;
	}
	
	public String getUser() throws RemoteException { return client.getUser(); }

	public boolean addFollower(String username, List<String> tags) throws RemoteException {
		Common.notNull(username, tags);
		return client.addFollower(username, tags);
	}

	public boolean removeFollower(String username, List<String> tags) throws RemoteException {
		Common.notNull(username, tags);
		return client.removeFollower(username, tags);
	}

	public boolean loadFollowers(ConcurrentMap<String, List<String>> followers) throws RemoteException {
		Common.notNull(followers);
		return client.setFollowers(followers);
	}
}