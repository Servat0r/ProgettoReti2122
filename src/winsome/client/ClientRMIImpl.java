package winsome.client;

import java.rmi.*;
import java.rmi.server.*;
import java.util.*;

import winsome.common.rmi.ClientRMI;
import winsome.util.Common;

/**
 * Implementation of {@link ClientRMI}.
 * @author Salvatore Correnti
 */
final class ClientRMIImpl extends UnicastRemoteObject implements ClientRMI {
	
	private static final long serialVersionUID = -6610109195985408710L;
	
	private WinsomeClient client;
	
	ClientRMIImpl(WinsomeClient client) throws RemoteException {
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