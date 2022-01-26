package winsome.common.msg;

import java.io.*;
import java.nio.channels.*;

public final class MessageReader {
	
	private static final byte
		INIT	= 0x00,
		READING	= 0x01,
		READ	= 0x02;
	
	private static final byte
		NONE = 0x00,
		STREAM = 0x01,
		CHANNEL = 0x02;
	
	private byte state;
	private InputStream stream;
	private ReadableByteChannel chan;
	private boolean hrecv, lenrecv;
	private int dataPtr;
	
	public synchronized boolean isInit() { return state == INIT; }
	public synchronized boolean isSending() { return state == READING; }
	public synchronized boolean isSent() { return state == READ; }
	public synchronized byte output() {
		if (stream == null && chan == null) return NONE;
		else if (chan == null) return STREAM;
		else return CHANNEL;
	}
	
	public synchronized boolean start() {
		if (state == INIT) {
			state = READING;
			dataPtr = 0;
			hrecv = false;
			lenrecv = false;
			return true;
		} else return false;
	}
	
}