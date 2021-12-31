package winsome.common.rmi;

import java.util.*;
import java.rmi.*;

public interface ClientInterface {
	
	public boolean addFollower(String username, List<String> tags) throws RemoteException;
	
	public boolean removeFollower(String username) throws RemoteException;
	
}