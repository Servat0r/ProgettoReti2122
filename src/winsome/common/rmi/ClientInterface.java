package winsome.common.rmi;

import java.util.*;
import java.rmi.*;

public interface ClientInterface {

	public String getUser() throws RemoteException;
	
	public boolean addFollower(String username, List<String> tags) throws RemoteException;
	
	public boolean removeFollower(String username, List<String> tags) throws RemoteException;
	
}