package winsome.server;

final class ServerMsg {

	//TODO Stringhe dei messaggi di risposta
	public static final String
		OK = "OK",
		INT_ERROR = "Errore interno al server",
		//Register
		REG_OK = "Utente '%d' registrato",
		REG_EXISTING = "Utente '%d' già esistente",
		REG_USPW_INV = "Username o password non corretti",
		//Login
		LOGIN_OK = "Login di '%d' effettuato correttamente",
		LOGIN_NEXISTING = "Utente '%d' non esistente",
		LOGIN_ALREADY = "Utente '%d' già loggato",
		LOGIN_ANOTHER = "Altro utente già loggato";

}
