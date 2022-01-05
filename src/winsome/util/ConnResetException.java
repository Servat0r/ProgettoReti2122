package winsome.util;

import java.io.IOException;

public final class ConnResetException extends IOException {
		
	private static final long serialVersionUID = 5027376090454938207L;
	private static final String DFLMSG = "Connection reset by peer";
	
	public ConnResetException() { super(DFLMSG); }
	
	public ConnResetException(String message) { super(message); }
	
	public ConnResetException(Throwable cause) { super(DFLMSG, cause); }
	
	public ConnResetException(String message, Throwable cause) { super(message, cause); }	
}