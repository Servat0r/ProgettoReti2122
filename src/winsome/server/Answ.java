package winsome.server;

final class Answ {

	//TODO Stringhe dei messaggi di risposta
	public static final String
		OK = "OK",
		INT_ERROR = "Errore interno al server",
		NEXISTING = "Utente '%s' non esistente",
		ALREADY = "Utente '%s' già loggato",
		STILL_LOGGED = "Utente ancora loggato",
		ANOTHER = "Altro utente già loggato",
		NLOGGED = "Utente '%s' non loggato",
		NONELOGGED = "Nessun utente loggato",
		USERSNEQ = "Utenti '%s' (collegato) e '%s' (per cui si effettua la richiesta di logout) non coincidenti",
		PERMDEN = "Permesso negato",
		UNKNOWN_CHAN = "Canale di comunicazione ignoto",
		INV_IDPOST = "Id del post (%s) non valido",
		POST_NEXISTS = "Non esiste un post con id = '%d'",
		POST_NINFEED = "Il post con id = '%d' non è nel tuo feed",
		POST_NAUTHOR = "Non sei l'autore del post",
		POST_AUTHOR = "Sei l'autore del post",
		//Register
		REG_OK = "Utente '%d' registrato",
		REG_EXISTING = "Utente '%d' già esistente",
		REG_USPW_INV = "Username o password non corretti",
		//Login
		LOGIN_OK = "Login di '%s' effettuato correttamente",
		LOGIN_PWINV = "Nome utente o password non corretta",
		//Logout
		LOGOUT_OK = "Logout di '%s' effettuato correttamente",
		//Follow
		FOLLOW_ALREADY = "Segui già '%s'",
		//Unfollow
		UNFOLLOW_ALREADY = "Non sei già più follower di '%s'",
		VOTED_ALREADY = "Hai già votato questo post",
		INV_VOTE_SYNTAX = "Sintassi del voto non valida: usa '+1' per like e '-1' per dislike",
		BTC_CONV = "Errore durante la conversione del portafoglio in bitcoin";
}