package winsome.common.rmi;

import java.util.*;

public interface ServerInterface {

	public static final String REGSERVNAME = "server.registration";
	
	public boolean register(String username, String password, List<String> tags);
		
	public boolean followersRegister(ClientInterface client);
	public boolean followersUnregister(ClientInterface client);
}