package winsome.common.msg;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import winsome.util.*;

public final class MessageWriter {
	
	private static final byte
		INIT	=	0x00,
		SENDING	=	0x01,
		SENT 	=	0x02;
	
	private static final byte
		NONE = 0x00,
		STREAM = 0x01,
		CHANNEL = 0x02;
	
	private byte state;
	private Message msg;
	private int dataPtr;
	private boolean hsent, lensent;
	private OutputStream stream;
	private WritableByteChannel chan;
	
	private void sendToStream(byte[] data) throws IOException { stream.write(data); }
	private void sendToChannel(byte[] data) throws IOException {
		ByteBuffer buf = ByteBuffer.wrap(data);
		while (buf.hasRemaining()) chan.write(buf);
	}
	private boolean sendHeader() throws MessageException, IOException {
		if (state != SENDING) throw new MessageException();
		if (!hsent) {
			byte[] header = msg.getHeader();
			byte out = this.output();
			if (out == STREAM) this.sendToStream(header);
			else if (out == CHANNEL) this.sendToChannel(header);
			else throw new IllegalStateException();
			hsent = true;
			return true;
		} else return false;
	}
	private boolean sendLengths() throws MessageException, IOException {
		if (state != SENDING) throw new MessageException();
		if (hsent && !lensent) {
			byte[] lengths = msg.getLengths();
			byte out = this.output();
			if (out == STREAM) this.sendToStream(lengths);
			else if (out == CHANNEL) this.sendToChannel(lengths);
			else throw new IllegalStateException();
			lensent = true;
			return true;
		} else return false;
	}
	private boolean sendString() throws MessageException, IOException {
		if (state != SENDING) throw new MessageException();
		if ( hsent && lensent && dataPtr < msg.getArgN() ) {
			byte[] data = msg.getArguments().get(dataPtr);
			byte out = this.output();
			if (out == STREAM) this.sendToStream(data);
			else if (out == CHANNEL) this.sendToChannel(data);
			else throw new IllegalStateException();
			dataPtr++;
			return true;
		} else return false;		
	}
	public synchronized boolean isInit() { return state == INIT; }
	public synchronized boolean isSending() { return state == SENDING; }
	public synchronized boolean isSent() { return state == SENT; }
	public synchronized byte output() {
		if (stream == null && chan == null) return NONE;
		else if (chan == null) return STREAM;
		else return CHANNEL;
	}
	
	public synchronized boolean start() {
		if (state == INIT && msg != null) {
			state = SENDING;
			dataPtr = 0;
			hsent = false;
			lensent = false;
			return true;
		} else return false;
	}
	
	public synchronized boolean hasNext() throws MessageException {
		if (state == SENT) return false;
		else if (state == SENDING) return !hsent || !lensent || (dataPtr < msg.getArgN());
		else throw new MessageException();
	}
	
	public synchronized boolean sendNext() throws IOException, MessageException {
		if (state != SENDING) throw new MessageException();
		if (!hsent) { hsent = this.sendHeader(); return hsent; }
		else if (!lensent) { lensent = this.sendLengths(); return lensent; }
		else if (dataPtr >= msg.getArgN()) throw new MessageException();
		else return this.sendString();
	}
	
	public synchronized boolean setMessage(Message msg) {
		Common.notNull(msg);
		if (this.state != INIT) return false;
		else { this.msg = msg; return true; }
	}
	
	public synchronized boolean setStream(OutputStream stream) {
		Common.notNull(stream);
		if (this.state != INIT) return false;
		else { this.stream = stream; this.chan = null; return true; }
	}
	
	public synchronized boolean setChannel(WritableByteChannel chan) {
		Common.notNull(chan);
		if (this.state != INIT) return false;
		else { this.stream = null; this.chan = chan; return true; }
	}
	
	public synchronized boolean reset() throws IOException { return reset(false); }
	
	public synchronized boolean reset(boolean close) throws IOException {
		if (this.state != SENT) return false;
		else {
			this.state = INIT;
			this.msg = null;
			this.dataPtr = 0;
			this.hsent = false;
			this.lensent = false;
			if (close && stream != null) stream.close();
			if (close && chan != null) chan.close();
			stream = null; chan = null;
			return true;
		}
	}
	
	public synchronized boolean reset(boolean close, Message msg) throws IOException {
		Common.notNull(msg);
		if (!this.reset(close)) return false;
		else { this.msg = msg; return true; }
	}
	
	public synchronized boolean reset(Message msg) throws IOException { return this.reset(false, msg); }
	
	public MessageWriter(Message msg) {
		Common.notNull(msg);
		this.state = INIT;
		this.msg = msg;
		this.dataPtr = 0;
		this.hsent = false;
		this.lensent = false;
		this.stream = null;
		this.chan = null;
	}
	
}