package winsome.server;

import java.io.*;
import java.net.*;
import java.util.*;

import winsome.util.Common;
/**
 * Wincoin -> Bitcoin conversion service.
 * @author Salvatore Correnti
 * @see WinsomeServer
 */
final class BitcoinService {

	private static final int
		DFLTIMEOUT = 5000, /* Default timeout for http request. */
		MINDECIMAL = 1, /* Minimum number of decimals for number generation */
		MAXDECIMAL = 20, /* Maximum number of decimals for number generation */
		DFLDECIMALS = 4; /* Default number of decimals */
	
	private static final String
		URLSTR = "https://www.random.org/decimal-fractions/?num=1&dec=%d&col=1&format=plain&rnd=new";
		
	private final URL url;
	
	public BitcoinService(int decimals) throws MalformedURLException {
		Common.allAndArgs(decimals >= MINDECIMAL, decimals <= MAXDECIMAL);
		String str = String.format(URLSTR, decimals);
		this.url = new URL(str);
	}
		
	public BitcoinService() throws MalformedURLException { this(DFLDECIMALS); }
	
	public final Double convert(double value) throws IOException {
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
		double exc = Double.parseDouble( Common.strip(new String(bs)) );
		return value * exc;
	}
	
	public final Double convert(String strvalue) throws IOException {
		Common.notNull(strvalue);
		try {
			double value = Double.parseDouble(strvalue);
			return this.convert(value);
		} catch (NumberFormatException ex) { return null; }
	}	
}