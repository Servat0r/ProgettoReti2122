package winsome.common.rmi;

import java.rmi.*;
import java.util.*;

public interface ServerInterface extends Remote {

	public static final String REGSERVNAME = "server.registration";
	
	public boolean register(String username, String password, List<String> tags) throws RemoteException;
		
	public boolean followersRegister(String username, ClientInterface client) throws RemoteException;
	public boolean followersUnregister(String username) throws RemoteException;
	public boolean isRegistered(String username) throws RemoteException;
}