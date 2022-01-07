package winsome.server;

import java.util.*;
import java.util.function.*;

import winsome.server.action.*;
import winsome.util.*;

final class RewardCalculator {
	
	private static final ToDoubleFunction<Integer> transf = (cp) -> 2.0 / (1.0 + Math.exp(1-cp));
	
	private static final ToDoubleFunction< Map<String, Integer> >
		voteSumStrategy = (map) -> Math.log1p((double) Math.max(Common.intSum(map.values()), 0));
	
	private static final ToDoubleFunction< Map<String, List<Integer>> > maxCpStrategy = (map) -> {
		List<Double> cps = new ArrayList<>();
		for (List<Integer> l : map.values()) cps.add( transf.applyAsDouble(Common.max(l)) );
		return Math.log1p(Common.doubleSum(cps));
	};
	
	private final double rewAuth;
	private final double rewCur;
	private final ToDoubleFunction< Map<String, Integer> > voteStrategy;
	private final ToDoubleFunction< Map<String, List<Integer>> > commentStrategy;
	private final Map<Long, Double> iterationMap;
	
	private double postReward(double iteration, List<Action> actions) { //Only likes, dislikes and comments!
		Common.notNull(actions);
		Map<String, Integer> voteMap = new HashMap<>();
		Map<String, List<Integer>> commentMap = new HashMap<>();
		for (Action act : actions) {
			switch (act.getType()) {
				case LIKE : { voteMap.put(new String(act.getActor()), 1); break; }
				case DISLIKE : { voteMap.put(new String(act.getActor()), -1); break; }
				case COMMENT : {
					List<Integer> l = commentMap.get(act.getActor());
					if (l == null) { l = new ArrayList<>(); commentMap.put(new String(act.getActor()), l); }
					l.add(act.getNComments());
					break;
				}
				default : { throw new IllegalStateException(); }
			}
		}
		double
			voteRew = voteStrategy.applyAsDouble(voteMap),
			commentRew = commentStrategy.applyAsDouble(commentMap);
		return (voteRew + commentRew) / iteration++;
	}
	
	
	public RewardCalculator(double rewAuth, double rewCur,
			ToDoubleFunction< Map<String, Integer> > voteStrategy,
			ToDoubleFunction< Map<String, List<Integer>> > commentStrategy) {
		double total = rewAuth + rewCur;
		this.rewAuth = rewAuth/total;
		this.rewCur = rewCur/total;
		this.iterationMap = new HashMap<>();
		this.voteStrategy = (voteStrategy != null ? voteStrategy : voteSumStrategy);
		this.commentStrategy = (commentStrategy != null ? commentStrategy : maxCpStrategy);
	}
	
	public RewardCalculator(double rewAuth, double rewCur) { this(rewAuth, rewCur, null, null); }
	
	public Map<String, Double> computeReward(List<Action> actions){
		
		Map< Long, List<Action>> actsForPost = new HashMap<>(); //Azioni compiute su ogni post
		Map< Long, Pair<String, Set<String>> > authCursForPost = new HashMap<>(); // <autore, {curatori}>
		Set<Long> deletedPosts = new HashSet<>();
		//eliminazione post
		Map<String, Double> rewardsForUser = new HashMap<>(); //Rewards totali per ogni utente
		
		Long idPost;
		List<Action> list;
		Pair<String, Set<String>> pair;
		ActionType type;
		for (Action act : actions) {
			type = act.getType();
			if (type != ActionType.DELETE) {
				
				idPost = act.getIdPost();
				
				list = actsForPost.get(idPost);
				if (list == null) { list = new ArrayList<>(); actsForPost.put(idPost, list); }
				
				pair = authCursForPost.get(idPost);
				if (pair == null) {
					pair = new Pair<String, Set<String>>(act.getAuthor(), new HashSet<>());
					authCursForPost.put(idPost, pair);
				}
				
				if (type == ActionType.LIKE || type == ActionType.DISLIKE || type == ActionType.COMMENT) {
					list.add(act);
					if (type != ActionType.DISLIKE && !act.getActor().equals(act.getAuthor()))
						pair.getValue().add(act.getActor());
				}
				
			} else deletedPosts.add(act.getIdPost());
		}
		//Eliminazione post
		for (long id : deletedPosts) { iterationMap.remove(id); actsForPost.remove(id); authCursForPost.remove(id); }
		//Calcolo ricompense  e #iterazione per ogni post
		for (long id : actsForPost.keySet()) {
			
			Double iter = iterationMap.get(id);
			iter = (iter != null ? iter + 1.0 : 1.0);
			iterationMap.put(id, iter);
			
			double prew, authrew, currew;
			String author;
			Set<String> curators;
			prew = this.postReward(iter.doubleValue(), actsForPost.get(id));
			if (prew > 0.0) {
				pair = authCursForPost.get(id);
				author = new String(pair.getKey());
				curators = pair.getValue();
				Common.allAndState(!curators.isEmpty());
				/* Le ricompense sono generate dagli utenti diversi dall'autore del post che interagiscono
				 * con lo stesso, quindi l'insieme dei curatori NON può essere vuoto!
				 */
				authrew = this.rewAuth * prew;
				currew = (this.rewCur * prew) / ((double)curators.size());
				//Ricompensa autore
				if (rewardsForUser.containsKey(author)) authrew += rewardsForUser.get(author);
				rewardsForUser.put(author, authrew);
				//Ricompensa curatori
				for (String cur : curators) {
					double d = currew;
					if (rewardsForUser.containsKey(cur)) d += rewardsForUser.get(cur);
					rewardsForUser.put(new String(cur), d);
				}
			}
		}
		return rewardsForUser;
	}
	
	public String toString() { return Common.jsonString(this); }
}