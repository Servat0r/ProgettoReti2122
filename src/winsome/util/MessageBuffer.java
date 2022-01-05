package winsome.util;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.List;

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
		READ, /* Compiuta un'operazione di lettura dati dal buffer: position = 0 (compact), limit = ultimo byte rilevante */
		WRITTEN, /* Compiuta un'operazione di scrittura dati nel buffer: position punta all'ultimo byte scritto, limit alla fine */
	}
		
	private ByteBuffer buffer;
	private State state;
	
	/**
	 * Allocates a MessageBuffer of the specified capacity.
	 * @param bufCap Capacity of the buffer (positive).
	 */
	public MessageBuffer(int bufCap) {
		Common.positive(bufCap); /* NON è possibile allocare buffer di capacità 0 */
		this.buffer = ByteBuffer.allocate(bufCap);
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
	public int get(boolean compact) {
		if (this.state == State.INIT) return -1;
		else if (this.state == State.WRITTEN) {
			this.buffer.flip();
			this.state = State.READ;
		}
		int result = (this.hasRemaining() ? (int)this.buffer.get() : -1);
		if (compact) this.compact();
		return result;
	}
	
	/**
	 * Reads the next byte from the buffer, returns it as an integer and compacts the buffer.
	 * @return An integer in the range from 0 to 255 representing the next byte, or -1 if buffer is empty.
	 */
	public int get() { return this.get(true); }
	
	/**
	 * Appends byte to the end of the buffer, and if specified compacts the buffer before putting it.
	 * @param b The byte to append.
	 * @param compact If true, compacts the buffer before putting the byte.
	 * @return true on success, false if buffer is empty.
	 */
	public boolean put(byte b, boolean compact) {
		if (this.state == State.READ) {
			if (compact) this.compact();
			this.buffer.position(this.buffer.limit());
			this.buffer.limit(this.buffer.capacity());
		}
		this.state = State.WRITTEN;
		try { this.buffer.put(b); return true; }
		catch (BufferOverflowException boe) { return false; }
	}

	/**
	 * Compacts the buffer and appends a byte to the end of the buffer.
	 * @param b The byte to append.
	 * @return true on success, false if buffer is empty.
	 */
	public boolean put(byte b) { return this.put(b, true); }

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
			if (this.state == State.WRITTEN) {
				result = new byte[position];
				this.buffer.flip();
			} else if (this.state == State.READ) {
				result = new byte[limit - position];
			} else throw new IllegalStateException();
			this.buffer.get(result);
			this.buffer.position(position);
			this.buffer.limit(limit);
			return result;
		}
	}
	
	public List<Byte> getAllDataAsList(){
		List<Byte> result = new ArrayList<>();
		if (this.state == State.INIT) return result;
		else {
			byte[] temp;
			int position = this.buffer.position();
			int limit = this.buffer.limit();
			if (this.state == State.WRITTEN) {
				temp = new byte[position];
				this.buffer.flip();
			} else if (this.state == State.READ) {
				temp = new byte[limit - position];
			} else throw new IllegalStateException();
			this.buffer.get(temp);
			this.buffer.position(position);
			this.buffer.limit(limit);
			for (byte b : temp) result.add(b);
			return result;
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
		else if (this.state == State.WRITTEN) return false;
		else if (this.state == State.READ) {
			this.buffer.compact();
			this.buffer.flip();
			return true;
		} else throw new IllegalStateException();
	}

	/**
	 * Resetta il buffer allo stato INIT.
	 */
	public void clear() {
		this.buffer.clear();
		this.state = State.INIT;
	}

	/**
	 * @return Il numero di bytes leggibili nel buffer se presenti.
	 */
	public int remaining() {
		if (this.state == State.INIT) return 0;
		else if (this.state == State.READ) return this.buffer.remaining();
		else if (this.state == State.WRITTEN) return this.buffer.position();
		else throw new IllegalStateException();
	}

	/**
	 * @return true if and only if this.remaining() > 0.
	 */
	public boolean hasRemaining() { return (this.remaining() > 0); }

	/**
	 * Prepares the buffer for a new reading operation, starting from 0 to the current limit.
	 */
	public void rewind() {
		if (this.state == State.READ) this.buffer.rewind();
		else if (this.state == State.WRITTEN) { this.buffer.flip(); this.state = State.READ; }
	}
	
	/**
	 * Checks if buffer is full, i.e. if it cannot be inserted any new byte without overwriting.
	 * @return true if buffer is full, false otherwise.
	 * @throws IllegalStateException if state is not recognized.
	 */
	public boolean isFull() {
		if (this.state == State.INIT) return false;
		else if (this.state == State.WRITTEN) {
			return (this.buffer.position() == this.buffer.capacity());
		} else if (this.state == State.READ) {
			return (this.buffer.limit() == this.buffer.capacity());
		} else throw new IllegalStateException();
	}

	/**
	 * Reads data from ReadableByteChannel chan and sets state to WRITTEN.
	 * @param chan Input channel.
	 * @return Number of read bytes on success, -1 if EOS is reached.
	 * @throws If {@link ReadableByteChannel#read(ByteBuffer)} throws an IOException for a connection reset.
	 * @throws IOException If another I/O error occurs.
	 * @throws IllegalArgumentException If (chan == null).
	 */
	public int readFromChannel(ReadableByteChannel chan) throws IOException {
		IOException ex = null;
		Common.notNull(chan);
		int result = 0;
		if (this.state == State.READ) {
			this.buffer.position(this.buffer.limit());
			this.buffer.limit(this.buffer.capacity());
		}
		this.state = State.WRITTEN;
		try { result = chan.read(this.buffer); }
		catch (IOException ioe) {
			if (Common.isConnReset(ioe)) ex = new ConnResetException();
			else ex = new IOException(ioe.getMessage(), ioe.getCause());
		}
		if (ex != null) throw ex;
		return result;
	}
	
	/**
	 * Writes data in [position, limit) on WritableByteChannel chan, sets state to READ and if specified
	 * compacts remaining data.
	 * @param chan Output channel.
	 * @param compact If true after writing the buffer is compacted.
	 * @return Number of written bytes if state is different from INIT, -1 otherwise.
	 * @throws ConnResetException If {@link java.nio.channels.WritableByteChannel#write(ByteBuffer)}
	 * throws an IOException for a connection reset by another peer (server, client etc.).
	 * @throws IOException If another I/O error occurs.
	 * @throws IllegalArgumentException If (chan == null).
	 */
	public int writeToChannel(WritableByteChannel chan, boolean compact) throws IOException {
		Common.notNull(chan);
		IOException ex = null;
		int result = 0;
		if (this.state == State.INIT) result = -1; /* Non c'� niente da scrivere! */
		else { 
			if (this.state == State.WRITTEN) this.buffer.flip(); //position = 0, limit = old_position
			this.state = State.READ; /* Indipendentemente dal risultato della write! */
			try { result = chan.write(this.buffer); }
			catch (IOException ioe) {
				if (Common.isConnReset(ioe)) ex = new ConnResetException();
				else ex = new IOException(ioe.getMessage(), ioe.getCause());
			}
			if (ex != null) throw ex;
			if (compact) this.compact();
		}
		return result;
	}
	
	/**
	 * Writes data in [position, limit) on WritableByteChannel chan, sets state to READ and compacts remaining data.
	 * @param chan Output channel.
	 * @return Number of written bytes if state is different from INIT, -1 otherwise.
	 * @throws ConnResetException If {@link java.nio.channels.WritableByteChannel#write(ByteBuffer)}
	 * throws an IOException for a connection reset by another peer (server, client etc.).
	 * @throws IOException If another I/O error occurs.
	 * @throws IllegalArgumentException If (chan == null).
	 */
	public int writeToChannel(WritableByteChannel chan) throws IOException { return this.writeToChannel(chan, true); }
	
	/**
	 * Reads at most length bytes from array starting from offset and sets state to WRITTEN.
	 * @param array Array of bytes from which to read data.
	 * @param offset First position in the array from which to read data.
	 * @param length Maximum number of readable bytes from array.
	 * @return Number of bytes copied in the buffer, -1 if it is not possible to copy data in the buffer 
	 * (see implementation).
	 * @throws IllegalArgumentException If (array == null) or (offset < 0) or (length <= 0) or (offset >= array.length).
	 */
	public int readFromArray(byte[] array, int offset, int length) {
		Common.andAllArgs(array != null, offset >= 0, length >= 0, offset < array.length);
		int maxCopy = Math.min(length, array.length - offset);
		int copied = 0;
		if (this.state == State.READ) {
			this.buffer.position(this.buffer.limit());
			this.buffer.limit(this.buffer.capacity());
		}
		try {
			while (copied < maxCopy) { this.buffer.put(array[copied + offset]); copied++; }
		} catch (BufferOverflowException boe) {}
		this.state = State.WRITTEN;
		return copied;
	}
	
	/**
	 * Writes data in the buffer ([0, position) if state is WRITTEN, [position, limit) if state is READ)
	 * in array from offset for at most length bytes, and if specified compacts remaining data at the end.
	 * @param array Byte array in which to write data.
	 * @param offset First position in the array in which to write.
	 * @param length Max number of writable bytes.
	 * @param compact If true, at the end of writing compacts the buffer.
	 * @return The number of bytes written to array if state is different from INIT, -1 otherwise.
	 * @throws IllegalArgumentException Se avviene almeno una delle seguenti: 
	 * array == null, length < 0, offset < 0, length > array.length, offset > array.length.
	 */
	public int writeToArray(byte[] array, int offset, int length, boolean compact) {
		Common.andAllArgs(array != null, length >= 0, offset >= 0, length <= array.length, offset <= array.length);
		int maxCopy = Math.min(length, array.length - offset);
		if (maxCopy == 0) return 0;
		if (this.state == State.INIT) return -1;
		else if (this.state == State.WRITTEN) this.buffer.flip();
		this.state = State.READ;
		int copied = 0;
		try {
			while (copied < maxCopy) { array[copied + offset] = this.buffer.get(); copied++; }
		} catch (BufferUnderflowException bue) {}
		if (compact) this.compact();
		return copied;
	}

	/**
	 * Writes data in the buffer ([0, position) if state is WRITTEN, [position, limit) if state is READ)
	 * in array from offset for at most length bytes and compacts remaining data at the end.
	 * @param array Byte array in which to write data.
	 * @param offset First position in the array in which to write.
	 * @param length Max number of writable bytes.
	 * @return The number of bytes written to array if state is different from INIT, -1 otherwise.
	 * @throws IllegalArgumentException If it happens at least one of: 
	 * array == null, length < 0, offset < 0, length > array.length, offset > array.length.
	 */
	public int writeToArray(byte[] array, int offset, int length) { return this.writeToArray(array, offset, length, true); } 
	
	/**
	 * Reads all data contained in array to the buffer and flushes the buffer to the channel chan each time
	 * the buffer becomes full, and if specified flushes the buffer at the end (thus writes ALL data in array
	 * to chan).
	 * @param array Array from which to read bytes.
	 * @param chan Output channel.
	 * @param flush If true, flushes buffer at the end.
	 * @throws ConnResetException If {@link #writeToChannel(WritableByteChannel, boolean)} throws an IOException
	 * for a connection reset by peer.
	 * @throws IOException If another I/O occurs.
	 * @throws IllegalArgumentException If (array == null) or (chan == null).
	 */
	public void readAllFromArray(byte[] array, WritableByteChannel chan, boolean flush) throws IOException {
		Common.notNull(array, chan);
		int length = array.length;
		int index = 0;
		while (index < length) {
			index += this.readFromArray(array, index, length);
			if (this.isFull()) { while (this.hasRemaining()) this.writeToChannel(chan); }
		}
		if (flush) while (this.hasRemaining()) this.writeToChannel(chan);
	}
	
	/**
	 * Reads all data contained in array to the buffer and flushes the buffer to the channel chan each time
	 * the buffer becomes full and at the end.
	 * @param array Array from which to read bytes.
	 * @param chan Output channel.
	 * @throws ConnResetException If {@link #writeToChannel(WritableByteChannel, boolean)} throws an IOException
	 * for a connection reset by peer.
	 * @throws IOException If another I/O occurs.
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
	 * all of them would be written to array, null on failure.
	 * NOTE: A return value < length indicates that EOS has been reached.
	 * @throws ConnResetException If {@link #readFromChannel(ReadableByteChannel)} throws an IOException
	 * for a connection closed by other peer.
	 * @throws IOException If another I/O error occurs.
	 * @throws IllegalArgumentException If (length <= 0) or (chan == null).
	 */
	public byte[] writeAllToArray(int length, ReadableByteChannel chan) throws IOException {
		Common.positive(length); Common.notNull(chan);
		byte[] result = new byte[length];
		int bRead = this.remaining(), index = 0;
		int cRead = 0;
		while (bRead < length) {
			cRead = this.readFromChannel(chan);
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