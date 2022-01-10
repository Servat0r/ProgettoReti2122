package winsome.common.rmi;

import java.rmi.*;
import java.util.*;

import winsome.util.Pair;

/**
 * Interface for server-side RMI including both user registration and followers notify service (de)registration.
 * @author Salvatore Correnti.
 */
public interface ServerRMI extends Remote {

	public static final String REGSERVNAME = "server.registration";
	
	public Pair<Boolean, String> register(String username, String password, List<String> tags)
		throws RemoteException;
		
	public boolean followersRegister(String username, ClientRMI client) throws RemoteException;
	public boolean followersUnregister(String username) throws RemoteException;
}