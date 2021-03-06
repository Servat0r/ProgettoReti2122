package winsome.server;

import java.nio.channels.*;
import java.util.*;
import java.util.function.BiConsumer;

import winsome.common.msg.*;
import winsome.util.*;

/**
 * Worker task for server workers pool. This task reads a message from a SocketChannel,
 *  processes it and wakes up server selector. On Exception, an Exception handler
 *  provided by the server handles the Exception.
 * @author Salvatore Correnti
 * @see WinsomeServer
 */
final class Worker implements Runnable {
	
	private final SelectionKey skey;
	private final SocketChannel client;
	private Message msg;
	private MessageBuffer buf;
	private BiConsumer<SelectionKey, Exception> excHandler;
	
	public Worker(SelectionKey skey, BiConsumer<SelectionKey, Exception> excHandler) {
		Common.notNull(skey, excHandler);
		this.skey = skey;
		this.client = (SocketChannel)this.skey.channel();
		this.msg = null;
		this.buf = null;
		this.excHandler = excHandler;
	}
	
	public Worker(SelectionKey skey) { this(skey, WinsomeServer.DFLEXCHANDLER); }
	
	public void run() {
		WinsomeServer server = WinsomeServer.getServer();
		if (server == null) return;
		try {
			String id = null, param = null;
			this.buf = new MessageBuffer(server.bufferCap());
			msg = Message.recvFromChannel(client, buf);
			id = msg.getIdStr();
			param = msg.getParamStr(); //Cannot throw MessageException
			String u = server.translateChannel(client);
			String msgstr = (u != null ? "Received request from user " + u : "Received request from anonymous user ");
			server.logger().log(msgstr);
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
						case Message.USERS : {msg = server.listUsers(skey); break;}
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
				case Message.EXIT : { server.quitReq(skey); return; }
				default : break;
			}
			if (msg == null) msg = Message.newError(Message.UNKNOWN_MSG);
			skey.attach(msg);
			skey.interestOps(SelectionKey.OP_WRITE);
			server.selector().wakeup();
		} catch (InterruptedException ie) {
			msg = Message.newError(ServerUtils.INTERROR);
		} catch (Exception ex) { excHandler.accept(skey, ex); }
	}
	
	public String toString() { return Common.jsonString(this); }
}