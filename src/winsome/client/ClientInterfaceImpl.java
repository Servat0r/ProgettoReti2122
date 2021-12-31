package winsome.client;

import java.rmi.*;
import java.rmi.server.*;
import java.util.*;

import winsome.common.rmi.ClientInterface;
import winsome.util.Common;

final class ClientInterfaceImpl extends UnicastRemoteObject implements ClientInterface {
	
	private static final long serialVersionUID = -6610109195985408710L;
	
	private WinsomeClient client;
	
	ClientInterfaceImpl(WinsomeClient client) throws RemoteException {
		super();
		Common.notNull(client);
		this.client = client;
	}
	
	public boolean addFollower(String username, List<String> tags) throws RemoteException {
		Common.notNull(username, tags);
		return client.addFollower(username, tags);
	}

	public boolean removeFollower(String username) throws RemoteException {
		Common.notNull(username);
		return client.removeFollower(username);
	}
}