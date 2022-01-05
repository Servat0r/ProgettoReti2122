package winsome.server;

import java.io.*;
import java.net.*;
import java.util.*;

import winsome.util.Common;

final class BitcoinService {

	private static final int
		DFLTIMEOUT = 5000,
		MINDECIMAL = 1,
		MAXDECIMAL = 20,
		DFLDECIMALS = 4;
	
	private static final String
		URLSTR = "https://www.random.org/decimal-fractions/?num=1&dec=%d&col=1&format=plain&rnd=new";
		
	private final URL url;
	
	public BitcoinService(int decimals, PrintStream err) throws MalformedURLException {
		Common.andAllArgs(decimals >= MINDECIMAL, decimals <= MAXDECIMAL);
		String str = String.format(URLSTR, decimals);
		this.url = new URL(str);
	}
	
	public BitcoinService(int decimals) throws MalformedURLException { this(decimals, null); }
	
	public BitcoinService(PrintStream err) throws MalformedURLException { this(DFLDECIMALS, err); }
	
	public BitcoinService() throws MalformedURLException { this(DFLDECIMALS); }
	
	public synchronized final Double convert(double value) throws IOException {
		URLConnection conn;
		conn = url.openConnection();
		conn.setReadTimeout(DFLTIMEOUT);
		conn.connect();
		InputStream in = conn.getInputStream();
		List<Byte> reads = new ArrayList<>();
		int c;
		while (in.available() > -1) {
			if ( (c = in.read()) < 0) break;
			else reads.add((byte)c);
		}
		in.close();
		byte[] bs = new byte[reads.size()];
		for (int i = 0; i < bs.length; i++) bs[i] = reads.get(i);
		double exc = Double.parseDouble(new String(bs).strip());
		return value * exc;
	}
	
	public synchronized final Double convert(String strvalue) throws IOException {
		Common.notNull(strvalue);
		try {
			double value = Double.parseDouble(strvalue);
			return this.convert(value);
		} catch (NumberFormatException ex) { return null; }
	}	
}