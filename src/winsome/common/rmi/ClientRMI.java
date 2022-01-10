package winsome.common.rmi;

import java.util.*;
import java.rmi.*;
/**
 * Interface for client-side RMI followers notify service (de)registration.
 * @author Salvatore Correnti
 */
public interface ClientRMI extends Remote {
	
	public boolean addFollower(String username, List<String> tags) throws RemoteException;
	
	public boolean removeFollower(String username) throws RemoteException;
	
}