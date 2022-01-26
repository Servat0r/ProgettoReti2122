package winsome.server;

import java.nio.channels.*;
import java.util.*;
import java.util.function.*;

import com.google.gson.stream.*;

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
	
	public Worker(SelectionKey skey) {
		Common.notNull(skey);
		this.skey = skey;
		this.client = (SocketChannel)this.skey.channel();
		this.msg = null;
		this.buf = null;
	}
	
	public void run() {
		WinsomeServer server = WinsomeServer.getServer();
		if (server == null) return;
		try {
			this.buf = new MessageBuffer(server.bufferCap());
			msg = Message.recvFromChannel(client, buf);
			
			int id = msg.getIdCode(), param = msg.getParamCode(); //Cannot throw MessageException
			String u = server.translateChannel(client);
			String msgstr = (u != null ? "Received request from user " + u : "Received request from anonymous user ");
			server.logger().log(msgstr);
			List<String> args = Common.convertArgs(msg);
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
						case Message.POSTDATA : {msg = server.showPost(skey, args); break;}
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
		} catch (RuntimeException rtex) {
			server.logger().logException(rtex);
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			server.signalIllegalState(rtex);
		} catch (Exception ex) {
			server.closeConnection(skey);
			server.logger().logException(ex);
		}
	}
	
	public String toString() {
		BiFunction<JsonWriter, Worker, Exception> encoder = 
			(wr, t) -> {
				try { WinsomeServer.gson().toJson(t, t.getClass(), wr); return null; }
				catch (Exception ex) { return ex; }
			};
		return Common.toString(this, WinsomeServer.gson(), encoder);
	}
	
}