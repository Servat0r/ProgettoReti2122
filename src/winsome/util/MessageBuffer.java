package winsome.util;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

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
	
	public MessageBuffer(int bufCap) {
		Common.positive(bufCap); /* NON è possibile allocare buffer di capacità 0 */
		this.buffer = ByteBuffer.allocate(bufCap);
		this.state = State.INIT;
	}
	
	public final int position() { return this.buffer.position(); }
	public final int limit() { return this.buffer.limit(); }
	public final int capacity() { return this.buffer.capacity(); }
	
	/**
	 * Se è appena stata compiuta una scrittura nel buffer, trasla i dati contenuti in [position, limit) in [0, limit-position),
	 * dopodiché, a differenza di {@link ByteBuffer#compact()}, setta limit = limit-position e position = 0.
	 * @return true se l'operazione è completata con successo, false se lo stato è diverso da State.READ o l'operazione
	 * non è completata con successo. Di conseguenza, se ritorna true lo stato del buffer è sempre State.READ, altrimenti se è
	 * diverso da State.READ non è stata compiuta alcuna operazione.
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
	
	public Byte get(boolean compact) {
		if (this.state == State.INIT) return null;
		else if (this.state == State.WRITTEN) {
			this.buffer.flip();
			this.state = State.READ;
		}
		Byte result = (this.hasRemaining() ? this.buffer.get() : null);
		if (compact) this.compact();
		return result;
	}
	
	public Byte get() { return this.get(true); }
	
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
	
	public boolean put(byte b) { return this.put(b, true); }
			
	/**
	 * Legge dati da un SocketChannel trasferendoli nel buffer interno e pone lo stato a State.WRITTEN .
	 * @param sc SocketChannel da cui leggere i dati.
	 * @return Il numero di bytes letti, -1 se nel canale si raggiunge la fine dello stream.
	 * @throws NotYetConnectedException Se lanciata da sc.read().
	 * @throws IOException Se lanciata da sc.read().
	 */
	public int readFromChannel(ReadableByteChannel sc) throws NotYetConnectedException, IOException {
		Common.notNull(sc);
		int result = 0;
		if (this.state == State.READ) {
			this.buffer.position(this.buffer.limit());
			this.buffer.limit(this.buffer.capacity());
		}
		this.state = State.WRITTEN;
		result = sc.read(this.buffer);
		return result;
	}
	
	/**
	 * Scrive i dati in [position, limit) sul SocketChannel sc, pone lo stato a State.READ ed 
	 * eventualmente compatta i dati rimanenti.
	 * @param sc SocketChannel su cui scrivere i dati.
	 * @param compact Se true al termine della scrittura viene eseguita una this.compact(), 
	 * altrimenti non viene eseguito nulla.
	 * @return Il numero di bytes scritti se lo stato è diverso da State.INIT, -1 altrimenti.
	 * @throws IOException Se lanciata da sc.write() (e.g. in caso di chiusura del canale).
	 */
	public int writeToChannel(WritableByteChannel sc, boolean compact) throws IOException {
		Common.notNull(sc);
		int result = 0;
		if (this.state == State.INIT) result = -1; /* Non c'� niente da scrivere! */
		else { 
			if (this.state == State.WRITTEN) this.buffer.flip(); //position = 0, limit = old_position
			this.state = State.READ; /* Indipendentemente dal risultato della write! */
			result = sc.write(this.buffer);
			if (compact) this.compact();
		}
		return result;
	}
	
	public int writeToChannel(WritableByteChannel sc) throws IOException { return this.writeToChannel(sc, true); }
	
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
	
	/**
	 * Legge al più length bytes da array partendo da offset e pone lo stato a WRITTEN.
	 * @param array Array di bytes da cui leggere i dati.
	 * @param offset Prima posizione in array da cui leggere i dati.
	 * @param length Massimo numero di bytes leggibili dall'array (nei limiti 
	 * della capacità del buffer e della lunghezza dell'array).
	 * @return Il numero di bytes copiati nel buffer.
	 */
	public int readFromArray(byte[] array, int offset, int length) {
		Common.notNull(array); Common.notNeg(offset, length);
		int maxCopy = Math.min(length, array.length - offset);
		if (maxCopy <= 0) throw new IllegalArgumentException();
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
	 * Scrive i dati contenuti nel buffer ([0, position) se lo stato è WRITTEN,
	 * [position, limit) se lo stato è READ) nell'array passato dalla posizione
	 * offset per al più length bytes, ed eventualmente compatta i dati rimanenti
	 * alla fine oppure rimette position al valore iniziale.
	 * @param array Array di bytes in cui scrivere i dati.
	 * @param offset Prima posizione in array su cui scrivere.
	 * @param length Massimo numero di bytes scrivibili in array (nei limiti della
	 * lunghezza di array e del buffer).
	 * @param compact Se true, al termine della scrittura compatta il buffer eliminando
	 * i dati scritti in array, altrimenti ripristina position al valore iniziale.
	 * @return Il numero di bytes scritti se lo stato è diverso da INIT, -1 altrimenti.
	 */
	public int writeToArray(byte[] array, int offset, int length, boolean compact) {
		Common.notNull(array);
		int maxCopy = Math.min(length, array.length - offset);
		if (maxCopy < 0) throw new IllegalArgumentException((length < 0 ? "length < 0" : "offset >= array.length"));
		else if (maxCopy == 0) return 0; //{ Common.debugPrint("writeToArray", "array.length - offset = " + (array.length - offset)); return 0; }
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

	public int writeToArray(byte[] array, int offset, int length) { return this.writeToArray(array, offset, length, true); } 
	
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
	
	public void readAllFromArray(byte[] array, WritableByteChannel chan) throws IOException {
		this.readAllFromArray(array, chan, true);
	}
	
	public byte[] readAllToArray(int length, ReadableByteChannel chan) throws IOException {
		Common.positive(length); Common.notNull(chan);
		byte[] result = new byte[length];
		int bRead = this.remaining(), index = 0;
		int cRead;
		Common.debugln("[start] : bRead = " + bRead);
		while (bRead < length) {
			cRead = this.readFromChannel(chan);
			if (cRead == -1) return null;
			else bRead += cRead;
			if (this.isFull()) while (index < Math.min(bRead, length)) index += this.writeToArray(result, index, length);
		}
		Common.debugln("[end] : bRead = " + bRead + ", index = " + index);
		while (index < length) index += this.writeToArray(result, index, length); 
		return result;
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
	 * @return true sse this.remaining() > 0.
	 */
	public boolean hasRemaining() { return (this.remaining() > 0); }
	
	public boolean isFull() {
		if (this.state == State.INIT) return false;
		else if (this.state == State.WRITTEN) {
			return (this.buffer.position() == this.buffer.capacity());
		} else if (this.state == State.READ) {
			return (this.buffer.limit() == this.buffer.capacity());
		} else throw new IllegalStateException();
	}
	
	/**
	 * Prepares the buffer for a new reading operation, starting from 0 to the current limit.
	 */
	public void rewind() {
		if (this.state == State.READ) {
			this.buffer.rewind();
		} else if (this.state == State.WRITTEN) {
			this.buffer.flip();
			this.state = State.READ;
		}
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