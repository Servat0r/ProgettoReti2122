package winsome.test.action;

import java.io.*;
import java.util.*;
import java.security.NoSuchAlgorithmException;

import winsome.server.data.*;
import winsome.util.*;
import com.google.gson.*;
import com.google.gson.stream.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

final class JSONDataTestMain {

	private static final String UFNAME = "users.json", PFNAME = "posts.json";
	
	public static final Type
		USERTYPE = new TypeToken< Table<String,User> >() {}.getType(),
		POSTTYPE = new TypeToken< Table<Long, Post> >(){}.getType();
	
	public static void main(String[] args) throws NoSuchAlgorithmException, IOException, DeserializationException {
		Table<String, User> users = new Table<>(true);
		Table<Long, Post> posts = new Table<>(true);
		Table<String, Wallet> wallets = new Table<>(true); 
		users.putIfAbsent(new User( "user1", "pw1", posts, wallets, Arrays.asList("tag1") ));
		users.putIfAbsent(new User("user2", "pw2", posts, wallets, Arrays.asList("tag2", "tag3") ));
		User u1 = users.get("user1"), u2 = users.get("user2");
		long idPost1 = u1.createPost("Titolo del post 1", "Contenuto del post 1");
		long idPost2 = u2.createPost("Titolo del post 2", "Contenuto del post 2");
		Gson gson = Serialization.GSON;
		JsonWriter
			userWriter = Serialization.fileWriter(UFNAME),
			postWriter = Serialization.fileWriter(PFNAME);
				
		gson.toJson(posts, POSTTYPE, postWriter);
		gson.toJson(users, USERTYPE, userWriter);

		userWriter.close(); postWriter.close();

		JsonReader
			userReader = Serialization.fileReader(UFNAME),
			postReader = Serialization.fileReader(PFNAME);
		
		Table<String, User> users2 = gson.fromJson(userReader, USERTYPE);
		Table<Long, Post> posts2 = gson.fromJson(postReader, POSTTYPE);
		
		
		//posts2.deserialize();
		for (Post p : posts2.getAll()) p.deserialize();
		Common.printLn(posts2);
		
		//users2.deserialize();
		for (User u : users2.getAll()) u.deserialize(users2, posts2, wallets);
		Common.printLn(users2);
	}

}