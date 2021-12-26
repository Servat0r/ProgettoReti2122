package winsome.common.rmi;

import java.util.*;
import java.util.concurrent.*;
import java.rmi.*;

public interface ClientInterface {

	public String getUser() throws RemoteException;
	
	public boolean addFollower(String username, List<String> tags) throws RemoteException;
	
	public boolean removeFollower(String username, List<String> tags) throws RemoteException;
	
	public boolean loadFollowers(ConcurrentMap< String, List<String> > followers) throws RemoteException;
}