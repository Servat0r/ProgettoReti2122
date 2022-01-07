package winsome.server;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.google.gson.reflect.TypeToken;

import winsome.server.data.Post;
import winsome.server.data.Table;
import winsome.server.data.User;
import winsome.server.data.Wallet;
import winsome.util.Common;

public final class ServerUtils {

	//TODO Stringhe dei messaggi di risposta
	protected static final String
		//Generals
		OK = "OK",
		INTERROR = "Errore interno al server",
		U_NEXISTING = "Utente '%s' non esistente",
		U_NLOGGED = "Utente '%s' non loggato",
		U_NONELOGGED = "Nessun utente loggato",
		PERMDEN = "Permesso negato",
		UNKNOWN_CHAN = "Connessione TCP ignota",
		//Login/Logout/Exit
		U_ALREADY_LOGGED = "Utente '%s' già loggato",
		U_STILL_LOGGED = "Utente ancora loggato",
		U_ANOTHER_LOGGED = "Altro utente già loggato",
		U_USERSNEQ = "Utenti '%s' (collegato) e '%s' (per cui si effettua la richiesta di logout) non coincidenti",
		//Login
		LOGIN_OK = "Login di '%s' effettuato correttamente",
		LOGIN_PWINV = "Nome utente o password non corretta",
		//Logout
		LOGOUT_OK = "Logout di '%s' effettuato correttamente",
		//Post
		POST_INVID = "Id del post (%s) non valido",
		POST_NEXISTS = "Non esiste un post con id = '%d'",
		POST_NINFEED = "Il post con id = '%d' non è nel tuo feed",
		POST_NAUTHOR = "Non sei l'autore del post",
		POST_AUTHOR = "Sei l'autore del post",
		//Register
		REG_OK = "Utente '%d' registrato",
		REG_EXISTING = "Utente '%d' già esistente",
		REG_USPW_INV = "Username o password non corretti",
		//Follow
		FOLLOW_ALREADY = "Segui già '%s'",
		//Unfollow
		UNFOLLOW_ALREADY = "Non sei già più follower di '%s'",
		//Rate
		VOTED_ALREADY = "Hai già votato questo post",
		INV_VOTE_SYNTAX = "Sintassi del voto non valida: usa '+1' per like e '-1' per dislike",
		//Rewin
		REWON_ALREADY = "Hai già fatto il rewin di questo post",
		//Bitcoin wallet
		BTC_CONV = "Errore durante la conversione del portafoglio in bitcoin";
	
	private static final Predicate<String> testLong = (str) -> {
		Common.notNull(str);
		try { Long.parseLong(str); return true; }
		catch (Exception ex) { return false; }
	};
	
	private static final BiPredicate<String, Integer> testTitleComm = (str, len) -> {
		String str2 = Common.dequote(str);
		return (str2.length() <= len);
	};
	
	private static final Predicate<String> testRate = (str) -> { return (str.equals("+1") || str.equals("-1")); };
	
	public static final Predicate<List<String>>
		postTest = (list) -> { //post <title> <comment>
			if (list.size() != 2) return false;
			else {
				String title = list.get(0), content = list.get(1);
				return testTitleComm.test(title, 20) && testTitleComm.test(content, 500);
			}
		},
		
		numTest = (list) -> { return (list.size() == 1 ? testLong.test(list.get(0)) : false); },
		
		rateTest = (list) -> { //rate <idPost> <vote>
			return (list.size() == 2) && testLong.test(list.get(0)) && testRate.test(list.get(1));
		},
		
		commentTest = (list) -> { //comment <idPost> <text>
			return list.size() == 2 && testLong.test(list.get(0));
		};
	
	
	protected static final Type
		USERSTYPE = new TypeToken< Table<String, User> >() {}.getType(),
		POSTSTYPE = new TypeToken< Table<Long, Post> >(){}.getType(),
		WALLETSTYPE = new TypeToken < Table<String, Wallet> >(){}.getType();
}