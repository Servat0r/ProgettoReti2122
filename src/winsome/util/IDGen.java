package winsome.util;

public final class IDGen {
	
	private long id;
	
	public synchronized long getId() { return id; }
	public synchronized long nextId() { return id++; }
	
	public IDGen(long start) { this.id = start; }
	
	public IDGen() { this(0); }
}