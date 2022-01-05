package winsome.server;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;

import winsome.common.msg.*;
import winsome.util.*;

//Legge e interpreta una richiesta proveniente dal client, la esegue e risponde al client medesimo
final class Worker implements Runnable {
	
	private final SelectionKey skey;
	private final SocketChannel client;
	private final WinsomeServer server;
	private Message msg;
	private MessageBuffer buf;
	
	public Worker(SelectionKey skey, WinsomeServer server) {
		Common.notNull(skey, server);
		this.skey = skey;
		this.client = (SocketChannel)this.skey.channel();
		this.server = server;
		this.msg = null;
		this.buf = new MessageBuffer(server.bufferCap());
	}
	
	public void run() {
		try {
			String id = null, param = null;
			try {
				msg = Message.recvFromChannel(client, buf);
				Debug.println(msg);
				id = msg.getIdStr();
				param = msg.getParamStr(); //Cannot throw MessageException
				String u = server.translateChannel(client);
				String msgstr = (u != null ? "Received request from user " + u : "Received request from anonymous user ");
				server.log("%s: it is (%s, %s - %s)", msgstr, id, param, msg.getArguments().toString());
			} catch (MessageException mex) { mex.printStackTrace(); }
			List<String> args = msg.getArguments();
			msg = null;
			switch(id) {
				case Message.LOGIN : {msg = server.login(skey, args); break;}
				case Message.LOGOUT : {msg = server.logout(skey, args); break;}
				case Message.FOLLOW : {msg = server.followUser(skey, args); break;}
				case Message.UNFOLLOW : {msg = server.unfollowUser(skey, args); break;}
				case Message.LIST : {
					switch (param) {
						case Message.FOLLOWING : {msg = server.listFollowing(skey); break;}
						case Message.USERS : {msg = server.listUsers(skey); Debug.println(msg); break;}
						default : break;
					};
					break;
				}
				case Message.BLOG : {msg = server.viewBlog(skey); break;}
				case Message.POST : {msg = server.createPost(skey, args); break;}
				case Message.SHOW : {
					switch (param) {
						case Message.FEED : {msg = server.showFeed(skey); break;}
						case Message.POST : {msg = server.showPost(skey, args); break;}
						default : break;
					};
					break;
				}
				case Message.DELETE : {msg = server.deletePost(skey, args); break;}
				case Message.REWIN : {msg = server.rewinPost(skey, args); break;}
				case Message.RATE : {msg = server.ratePost(skey, args); break;}
				case Message.COMMENT : {msg = server.addComment(skey, args); break;}
				case Message.WALLET : {
					switch (param) {
						case Message.EMPTY: {msg = server.getWallet(skey); break;}
						case Message.BTC: {msg = server.getWalletInBitcoin(skey); break;}
						default : break;
					};
					break;
				}
				case Message.QUIT :
				case Message.EXIT : { msg = server.quitReq(skey); break; }
				default : break;
			}
			if (msg == null) msg = Message.newError(Message.UNKNOWN_MSG);
			Debug.println( String.format("Risultato finale: %s", msg.toString()) );
			skey.attach(msg);
			skey.interestOps(SelectionKey.OP_WRITE);
			server.selector().wakeup();
		}
		catch (IOException ioe) { server.closeConnection(skey); }
		catch (IllegalArgumentException illarg) { }
		finally { Debug.println("Finally clause"); }
	}
}