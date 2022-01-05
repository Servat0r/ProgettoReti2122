package winsome.common.rmi;

import java.util.*;
import java.rmi.*;

public interface ClientInterface extends Remote {
	
	public void addFollower(String username, List<String> tags) throws RemoteException;
	
	public void removeFollower(String username) throws RemoteException;
	
}