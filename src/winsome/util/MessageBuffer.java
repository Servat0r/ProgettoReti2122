package winsome.util;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Wrapper della classe ByteBuffer che mantiene un controllo sullo stato del buffer,
 * permettendo di leggere e scrivere con SocketChannels e array di bytes.
 * @author Salvatore Correnti
 */
public final class MessageBuffer {

	/**
	 * Enumerazione che rappresenta lo stato del ByteBuffer e i dati significativi che contiene.
	 * INIT = buffer inizializzato, nessun dato significativo contenuto (position = 0, limit = capacity);
	 * READ = appena compiuta un'operazione di lettura dati dal buffer (tranne getAllData()), dati significativi in [0, limit)
	 * (limit >= position >= 0, normalizzabile con compact() a 0, limit <= capacity);
	 * WRITTEN = appena compiuta un'operazione di scrittura dati nel buffer, dati significativi in [0, position)
	 * (position >= 0, limit = capacity).
	 * @author Salvatore Correnti
	 */
	private static enum State {
		INIT, /* Buffer inizializzato, nessuna operazione ancora compiuta */
		READING, /* Compiuta un'operazione di lettura dati dal buffer: position = 0 (compact), limit = ultimo byte rilevante */
		WRITING, /* Compiuta un'operazione di scrittura dati nel buffer: position punta all'ultimo byte scritto, limit alla fine */
	}
		
	private ByteBuffer buffer;
	private State state;
	
	/**
	 * Allocates a MessageBuffer of the specified capacity.
	 * @param bufCap Capacity of the buffer (positive).
	 */
	public MessageBuffer(int bufCap) {
		Common.allAndArgs(bufCap >= 0); /* NON è possibile allocare buffer di capacità 0 */
		this.buffer = ByteBuffer.allocate(bufCap);
		this.state = State.INIT;
	}
	
	public MessageBuffer(byte[] data) {
		Common.notNull(data);
		this.buffer = ByteBuffer.wrap(data);
		this.state = State.INIT;
	}
	
	public final int position() { return this.buffer.position(); }
	public final int limit() { return this.buffer.limit(); }
	public final int capacity() { return this.buffer.capacity(); }
	
	/**
	 * Reads the next byte from the buffer and returns it as an integer, and if specified compacts
	 * the buffer (so eliminating even data before position).
	 * @param compact If true, compacts the buffer after retrieving the next byte.
	 * @return An integer in the range from 0 to 255 representing the next byte, or -1 if buffer is empty.
	 */
	public int get() {
		this.resetForReading();
		return (!this.isEmpty() ? (int)this.buffer.get() : 0xffffffff);
	}
		
	/**
	 * Appends byte to the end of the buffer, and if specified compacts the buffer before putting it.
	 * @param b The byte to append.
	 * @return true on success, false if buffer is empty.
	 */
	public boolean put(byte b) {
		this.resetForWriting();
		try { this.buffer.put(b); return true; }
		catch (BufferOverflowException boe) { return false; }
	}
	
	/**
	 * Copia i dati contenuti nel buffer in un array di bytes, SENZA modificare lo stato del buffer né il suo contenuto.
	 * @return Un array di bytes contenente i dati del buffer ([0, position) se lo stato è WRITTEN, [position, limit) se 
	 * lo stato è READ), null altrimenti.
	 */
	public byte[] getAllData() {
		if (this.state == State.INIT) return new byte[0];
		else {
			byte[] result;
			int position = this.buffer.position();
			int limit = this.buffer.limit();
			if (this.state == State.WRITING) {
				result = new byte[position];
				this.buffer.flip();
			} else if (this.state == State.READING) {
				result = new byte[limit - position];
			} else throw new IllegalStateException();
			this.buffer.get(result);
			this.buffer.position(position);
			this.buffer.limit(limit);
			return result;
		}
	}
	
	public void getAllData(Collection<Byte> result){
		Common.notNull(result);
		if (this.state == State.INIT) return;
		else {
			byte[] temp;
			int position = this.buffer.position();
			int limit = this.buffer.limit();
			if (this.state == State.WRITING) {
				temp = new byte[position];
				this.buffer.flip();
			} else if (this.state == State.READING) {
				temp = new byte[limit - position];
			} else throw new IllegalStateException();
			this.buffer.get(temp);
			this.buffer.position(position);
			this.buffer.limit(limit);
			for (byte b : temp) result.add(b);
		}
	}

	/**
	 * Se è appena stata compiuta una scrittura nel buffer, trasla i dati contenuti in [position, limit) in [0, limit-position),
	 * dopodiché, a differenza di {@link ByteBuffer#compact()}, setta limit = limit-position e position = 0.
	 * @return true se l'operazione è completata con successo, false se lo stato è diverso da State.READ o l'operazione
	 * non è completata con successo. Di conseguenza, se ritorna true lo stato del buffer è sempre State.READ, altrimenti se è
	 * diverso da State.READ non è stata compiuta alcuna operazione.
	 * @throws IllegalStateException If state is not recognized.
	 */
	public boolean compact() {
		if (this.state == State.INIT) return false;
		else if (this.state == State.WRITING) return false;
		else if (this.state == State.READING) {
			this.buffer.compact();
			this.buffer.flip();
			return true;
		} else throw new IllegalStateException();
	}
	
	/**
	 * Resets the buffer to the state INIT.
	 */
	public void clear() { this.buffer.clear(); this.state = State.INIT; }
	
	/**
	 * @return Il numero di bytes leggibili nel buffer se presenti.
	 */
	public int remaining() {
		if (this.state == State.INIT) return 0;
		else if (this.state == State.READING) return this.buffer.remaining();
		else if (this.state == State.WRITING) return this.buffer.position();
		else throw new IllegalStateException();
	}
	
	/**
	 * @return true if and only if this.remaining() > 0.
	 */
	public boolean isEmpty() { return this.remaining() == 0; }
	
	public int available() {
		if (this.state == State.INIT) return this.buffer.capacity();
		else if (this.state == State.READING) return this.capacity() - this.limit();
		else if (this.state == State.WRITING) return this.capacity() - this.position();
		else throw new IllegalStateException();
	}
	
	/**
	 * Checks if buffer is full, i.e. if it cannot be inserted any new byte without overwriting.
	 * @return true if buffer is full, false otherwise.
	 * @throws IllegalStateException if state is not recognized.
	 */
	public boolean isFull() { return this.available() == 0; }
	
	public void resetForWriting() {
		if (this.state == State.READING) {
			this.buffer.position(this.buffer.limit());
			this.buffer.limit(this.buffer.capacity());
		}
		this.state = State.WRITING;		
	}
	
	public void resetForReading() {
		if (this.state != State.READING) {
			this.buffer.flip();
			this.state = State.READING;
		}
	}
	
	public void flush(OutputStream dest) {
		Common.notNull(dest);
		while (!this.isEmpty()) this.write(dest, this.remaining(), true);
	}
	
	public void flush(WritableByteChannel chan) {
		Common.notNull(chan);
		while (!this.isEmpty()) this.write(chan, this.remaining(), true);
	}
			
	public int read(byte[] src, int offset, int maxRead) {
		Common.allAndArgs(src != null, offset >= 0, maxRead >= -1, offset < src.length);
		int aux = src.length - offset, maxCopy = ( maxRead == -1 ?  aux : Math.min(maxRead, aux) );
		int copied = 0;
		this.resetForWriting();
		try { while (copied < maxCopy) { this.buffer.put(src[copied + offset]); copied++; } }
		catch (BufferOverflowException boe) {}
		return copied;
	}
	
	public int read(Byte[] src, int offset, int maxRead) {
		Common.allAndArgs(src != null, offset >= 0, maxRead >= -1, offset < src.length);
		for (int i = 0; i < src.length; i++) if (src[i] == null) throw new IllegalArgumentException();
		int aux = src.length - offset, maxCopy = ( maxRead == -1 ?  aux : Math.min(maxRead, aux) );
		int copied = 0;
		this.resetForWriting();
		try { while (copied < maxCopy) { this.buffer.put(src[copied + offset]); copied++; } }
		catch (BufferOverflowException boe) {}
		return copied;	
	}
	
	public int read(Iterable<Byte> src, int offset, int maxRead) {
		Common.allAndArgs(src != null, offset >= 0, maxRead >= -1);
		int index = 0, copied = 0;
		this.resetForWriting();
		Iterator<Byte> iter = src.iterator();
		try {
			while (iter.hasNext() && (maxRead == -1 ? true : copied < maxRead)) {
				byte b = iter.next().byteValue();
				index++;
				if (index > offset) { this.buffer.put(b); copied++; }
			}
		} catch (BufferOverflowException boe) { }
		while (iter.hasNext()) iter.next();
		return copied;
	}
	
	public int read(InputStream src, int maxRead) throws IOException {
		Common.allAndArgs(src != null, maxRead >= -1);
		this.resetForWriting();
		maxRead = ( maxRead == -1 ? this.available() : Math.min(maxRead, this.available()) );
		int got = 0;
		try {
			for (; got < maxRead; got++) this.buffer.put((byte) src.read());
		} catch (BufferOverflowException boe) { }
		return got;
	}
	
	/**
	 * Reads data from ReadableByteChannel chan and sets state to WRITTEN.
	 * @param chan Input channel.
	 * @return Number of read bytes on success, -1 if EOS is reached.
	 * @throws IOException If an I/O error occurs.
	 * @throws IllegalArgumentException If (chan == null).
	 */
	public int read(ReadableByteChannel chan) throws IOException {
		Common.allAndArgs(chan != null);
		this.resetForWriting();
		int result = 0;
		result = chan.read(this.buffer);
		return result;
	}
	
	public int write(byte[] dest, int offset, int maxWrite, boolean compact) {
		Common.allAndArgs(dest != null, offset >= 0, offset <= dest.length, maxWrite >= -1, maxWrite <= dest.length);
		int avail = dest.length - offset;
		maxWrite = ( maxWrite == -1 ? avail : Math.min(maxWrite, avail) );
		if (maxWrite == 0) return 0;
		if (this.state == State.INIT) return -1;
		else if (this.state == State.WRITING) this.buffer.flip();
		this.state = State.READING;
		int copied = 0;
		try { while (copied < maxWrite) { dest[copied + offset] = this.buffer.get(); copied++; } }
		catch (BufferUnderflowException bue) { }
		if (compact) this.compact();
		return copied;
	}
	
	public int write(Byte[] dest, int offset, int maxWrite, boolean compact) {
		Common.allAndArgs(dest != null, offset >= 0, offset <= dest.length, maxWrite >= -1, maxWrite <= dest.length);
		int avail = dest.length - offset;
		maxWrite = ( maxWrite == -1 ? avail : Math.min(maxWrite, avail) );
		if (maxWrite == 0) return 0;
		if (this.state == State.INIT) return -1;
		else if (this.state == State.WRITING) this.buffer.flip();
		this.state = State.READING;
		int copied = 0;
		try { while (copied < maxWrite) { dest[copied + offset] = this.buffer.get(); copied++; } }
		catch (BufferUnderflowException bue) { }
		if (compact) this.compact();
		return copied;
	}
	
	public int write(Collection<Byte> dest, int maxWrite, boolean compact) { return 0; }//TODO
	
	public int write(OutputStream dest, int maxWrite, boolean compact) { return 0; }//TODO
		
	public int write(WritableByteChannel dest, int maxWrite, boolean compact) { return 0; }//TODO
	
	// TODO ------------------------------------------------------
	//TODO ELIMINARE!
	public int transfer(byte[] src, OutputStream dest, int srcOffset, int maxTransf) {
		Common.notNull(src, dest);
		Common.allAndArgs(srcOffset >= 0, maxTransf >= -1, srcOffset <= src.length);
		int rem = src.length - srcOffset;
		maxTransf = ( maxTransf == -1 ? rem : Math.min(maxTransf, rem) );
		int transfd = 0;
		while (transfd < maxTransf) {
			transfd += this.read(src, srcOffset, maxTransf);
			srcOffset += transfd;
			if (this.isFull()) this.flush(dest);
		}
		this.flush(dest);
		return transfd;
	}
	
	public int transfer(byte[] src, WritableByteChannel dest, int srcOffset, int maxTransf) {
		Common.notNull(src, dest);
		Common.allAndArgs(srcOffset >= 0, maxTransf >= -1, srcOffset <= src.length);
		int rem = src.length - srcOffset;
		maxTransf = ( maxTransf == -1 ? rem : Math.min(maxTransf, rem) );
		int transfd = 0;
		while (transfd < maxTransf) {
			transfd += this.read(src, srcOffset, maxTransf);
			srcOffset += transfd;
			if (this.isFull()) this.flush(dest);
		}
		this.flush(dest);
		return transfd;
	}
	
	//TODO ELIMINARE!
	public int transfer(InputStream src, byte[] dest, int destOffset, int maxTransf) throws IOException {
		Common.notNull(src, dest);
		Common.allAndArgs(destOffset >= 0, destOffset <= dest.length, maxTransf >= -1);
		int rem = dest.length - destOffset;
		maxTransf = ( maxTransf == -1 ? rem : Math.min(maxTransf, rem) );
		int transfd = 0, res = 0;
		while ( (maxTransf >= 0) && (res = src.read(dest, destOffset, maxTransf)) >= 0 )
			{ transfd += res; maxTransf -= res; destOffset += res; }
		return transfd;
	}
	
	public int transfer(ReadableByteChannel src, byte[] dest, int destOffset, int maxTransf) throws IOException {
		Common.notNull(src, dest);
		Common.allAndArgs(destOffset >= 0, destOffset <= dest.length, maxTransf >= -1);
		int rem = dest.length - destOffset;
		maxTransf = ( maxTransf == -1 ? rem : Math.min(maxTransf, rem) );
		int transfd = 0, res = 0;
		this.resetForWriting();
		while ( (transfd < maxTransf) && (res = src.read(buffer)) >= 0) {
			transfd += res;
			if (this.isFull()) while (!this.isEmpty()) this.write(dest, destOffset, dest.length, true);
		}
		while (!this.isEmpty()) this.write(dest, destOffset, dest.length, true);
		return transfd;
	}
		
	public int transfer(InputStream src, WritableByteChannel dest, int maxTransf) {//TODO
		Common.allAndArgs(src != null, dest != null, maxTransf >= 0);
		int transfd = 0;
		while (transfd < maxTransf) {
			
		}
		return transfd;
	}
	
	public int transfer(ReadableByteChannel src, OutputStream dest, int maxTransf) {//TODO
		return 0;
	}
	
	public int transfer(ReadableByteChannel src, WritableByteChannel dest) {//TODO
		return 0;
	}
	
	/**
	 * Writes data in [position, limit) on WritableByteChannel chan, sets state to READ and if specified
	 *  compacts remaining data.
	 * @param chan Output channel.
	 * @param compact If true after writing the buffer is compacted.
	 * @return Number of written bytes if state is different from INIT, -1 otherwise.
	 * @throws IOException If an I/O error occurs.
	 * @throws IllegalArgumentException If (chan == null).
	 */
	public int writeToChannel(WritableByteChannel chan, boolean compact) throws IOException {
		Common.notNull(chan);
		int result = 0;
		if (this.state == State.INIT) result = -1; /* Non c'� niente da scrivere! */
		else { 
			if (this.state == State.WRITING) this.buffer.flip(); //position = 0, limit = old_position
			this.state = State.READING; /* Indipendentemente dal risultato della write! */
			result = chan.write(this.buffer);
			if (compact) this.compact();
		}
		return result;
	}
	
	/**
	 * Writes data in [position, limit) on WritableByteChannel chan, sets state to READ and compacts remaining data.
	 * @param chan Output channel.
	 * @return Number of written bytes if state is different from INIT, -1 otherwise.
	 * @throws IOException If an I/O error occurs.
	 * @throws IllegalArgumentException If (chan == null).
	 */
	public int writeToChannel(WritableByteChannel chan) throws IOException { return this.writeToChannel(chan, true); }
	
	/**
	 * Reads at most length bytes from array starting from offset and sets state to WRITTEN.
	 * @param array Array of bytes from which to read data.
	 * @param offset First position in the array from which to read data.
	 * @param length Maximum number of readable bytes from array.
	 * @return Number of bytes copied in the buffer, -1 if it is not possible to copy data in the buffer 
	 *  (see implementation).
	 * @throws IllegalArgumentException If (array == null) or (offset < 0) or (length <= 0) or
	 *  (offset >= array.length).
	 */
	public int readFromArray(byte[] array, int offset, int length) {
		Common.allAndArgs(array != null, offset >= 0, length >= 0, offset < array.length);
		int maxCopy = Math.min(length, array.length - offset);
		int copied = 0;
		if (this.state == State.READING) {
			this.buffer.position(this.buffer.limit());
			this.buffer.limit(this.buffer.capacity());
		}
		try {
			while (copied < maxCopy) { this.buffer.put(array[copied + offset]); copied++; }
		} catch (BufferOverflowException boe) {}
		this.state = State.WRITING;
		return copied;
	}
	
	/**
	 * Writes data in the buffer ([0, position) if state is WRITTEN, [position, limit) if state is READ)
	 *  in array from offset for at most length bytes, and if specified compacts remaining data at the end.
	 * @param array Byte array in which to write data.
	 * @param offset First position in the array in which to write.
	 * @param length Max number of writable bytes.
	 * @param compact If true, at the end of writing compacts the buffer.
	 * @return The number of bytes written to array if state is different from INIT, -1 otherwise.
	 * @throws IllegalArgumentException Se avviene almeno una delle seguenti: 
	 *  array == null, length < 0, offset < 0, length > array.length, offset > array.length.
	 */
	public int writeToArray(byte[] array, int offset, int length, boolean compact) {
		Common.allAndArgs(array != null, length >= 0, offset >= 0, length <= array.length, offset <= array.length);
		int maxCopy = Math.min(length, array.length - offset);
		if (maxCopy == 0) return 0;
		if (this.state == State.INIT) return -1;
		else if (this.state == State.WRITING) this.buffer.flip();
		this.state = State.READING;
		int copied = 0;
		try {
			while (copied < maxCopy) { array[copied + offset] = this.buffer.get(); copied++; }
		} catch (BufferUnderflowException bue) {}
		if (compact) this.compact();
		return copied;
	}

	/**
	 * Writes data in the buffer ([0, position) if state is WRITTEN, [position, limit) if state is READ)
	 *  in array from offset for at most length bytes and compacts remaining data at the end.
	 * @param array Byte array in which to write data.
	 * @param offset First position in the array in which to write.
	 * @param length Max number of writable bytes.
	 * @return The number of bytes written to array if state is different from INIT, -1 otherwise.
	 * @throws IllegalArgumentException If it happens at least one of: 
	 *  array == null, length < 0, offset < 0, length > array.length, offset > array.length.
	 */
	public int writeToArray(byte[] array, int offset, int length) { return this.writeToArray(array, offset, length, true); } 
	
	/**
	 * Reads all data contained in array to the buffer and flushes the buffer to the channel chan
	 *  each time the buffer becomes full, and if specified flushes the buffer at the end (thus
	 *  writes ALL data in array to chan).
	 * @param array Array from which to read bytes.
	 * @param chan Output channel.
	 * @param flush If true, flushes buffer at the end.
	 * @throws IOException If an I/O occurs.
	 * @throws IllegalArgumentException If (array == null) or (chan == null).
	 */
	public void readAllFromArray(byte[] array, WritableByteChannel chan, boolean flush) throws IOException {
		Common.notNull(array, chan);
		int length = array.length;
		int index = 0;
		while (index < length) {
			index += this.readFromArray(array, index, length);
			if (this.isFull()) { while (!this.isEmpty()) this.writeToChannel(chan); }
		}
		if (flush) while (!this.isEmpty()) this.writeToChannel(chan);
	}
	
	/**
	 * Reads all data contained in array to the buffer and flushes the buffer to the channel
	 *  chan each time the buffer becomes full and at the end.
	 * @param array Array from which to read bytes.
	 * @param chan Output channel.
	 * @throws IOException If an I/O occurs.
	 * @throws IllegalArgumentException If (array == null) or (chan == null).
	 */
	public void readAllFromArray(byte[] array, WritableByteChannel chan) throws IOException {
		this.readAllFromArray(array, chan, true);
	}
	
	/**
	 * 
	 * @param length Max length of returned array.
	 * @param chan The channel from which to read bytes.
	 * @return A byte array of at most length bytes on success, such that if the EOS is reached,
	 *  all of them would be written to array, null on failure.
	 *  NOTE: A return value < length indicates that EOS has been reached.
	 * @throws IOException If an I/O error occurs.
	 * @throws IllegalArgumentException If (length <= 0) or (chan == null).
	 */
	public byte[] writeAllToArray(int length, ReadableByteChannel chan) throws IOException {
		Common.allAndArgs(length >= 0); Common.notNull(chan);
		byte[] result = new byte[length];
		int bRead = this.remaining(), index = 0;
		int cRead = 0;
		while (bRead < length) {
			cRead = this.read(chan);
			if (cRead == -1) {
				byte[] res = new byte[bRead];
				for (int i = 0; i < index; i++) res[i] = result[i];
				while (index < bRead) index += this.writeToArray(res, index, bRead);
				return res;
			}
			else bRead += cRead;
			if (this.isFull()) while (index < Math.min(bRead, length)) index += this.writeToArray(result, index, length);
		}
		while (index < length) index += this.writeToArray(result, index, length); 
		return result;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("MessageBuffer[state = ");
		sb.append(this.state.toString());
		sb.append("; position = " + this.buffer.position());
		sb.append("; limit = " + this.buffer.limit());
		sb.append("; capacity = " + this.buffer.capacity() + "]");
		return sb.toString();
	}
}