package winsome.server;

import java.util.*;
import java.util.function.*;

import winsome.server.action.*;
import winsome.util.*;

/**
 * Implementation of {@link RewardCalculator}.
 * @author Salvatore Correnti
 */
final class RewardCalculatorImpl implements RewardCalculator<Integer, List<Integer>> {
	
	private static final double transf(int cp) { return 2.0 / (1.0 + Math.exp(1-cp)); }
	
	/** Default vote strategy as described in project specification. */
	private static final ToDoubleFunction< Map<String, Integer> >
		voteSumStrategy = (map) -> Math.log1p((double) Math.max(Common.intSum(map.values()), 0));
	
	/** Default comment strategy as described in project specification. */
	private static final ToDoubleFunction< Map<String, List<Integer>> > maxCpStrategy = (map) -> {
		List<Double> cps = new ArrayList<>();
		for (List<Integer> l : map.values()) cps.add( transf(Common.max(l)) );
		return Math.log1p(Common.doubleSum(cps));
	};
	
	/* Authors and curators rewards percentage normalized as in [0,1]. */
	private final double rewAuth, rewCurs;
	/** Vote strategy for calculating votes-related rewards. */
	private final ToDoubleFunction< Map<String, Integer> > voteStrategy;
	/** Comment strategy for calculating comments-related rewards. */
	private final ToDoubleFunction< Map<String, List<Integer>> > commentStrategy;
	/** Map of iterations. */
	private final Map<Long, Double> iterationMap;
	
	public RewardCalculatorImpl(double rewAuth, double rewCur, Map<Long, Double> map,
			ToDoubleFunction< Map<String, Integer> > voteStrategy,
			ToDoubleFunction< Map<String, List<Integer>> > commentStrategy) {
		double total = rewAuth + rewCur;
		this.rewAuth = rewAuth/total;
		this.rewCurs = rewCur/total;
		this.iterationMap = (map != null ? map : new HashMap<>());
		this.voteStrategy = (voteStrategy != null ? voteStrategy : voteSumStrategy);
		this.commentStrategy = (commentStrategy != null ? commentStrategy : maxCpStrategy);
	}
	
	public RewardCalculatorImpl(double rewAuth, double rewCur, Map<Long, Double> map) { this(rewAuth, rewCur, map, null, null); }
	
	/**
	 * Calculates the reward for a single post.
	 * @param iteration "Age of the post".
	 * @param actions List of actions committed for that post.
	 * @return Total reward for that post.
	 */
	public double postReward(double iteration, List<Action> actions) { //Only likes, dislikes and comments!
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

	/**
	 * Calculates the total reward for each user.
	 * @param actions Actions committed in the last period.
	 */
	public Map<String, Double> computeReward(List<Action> actions){
		
		Map< Long, List<Action>> actsForPost = new HashMap<>(); /* Azioni compiute su ogni post */
		Map< Long, Pair<String, Set<String>> > authCursForPost = new HashMap<>(); /*  <autore, {curatori}> */
		Set<Long> deletedPosts = new HashSet<>(); /* Post eliminati nell'ultimo periodo */
		Map<String, Double> rewardsForUser = new HashMap<>(); /* Rewards totali per ogni utente */
		
		Long idPost;
		List<Action> list;
		Pair<String, Set<String>> pair;
		ActionType type;
		/* Costruisce le mappe */
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
		/* Eliminazione post */
		for (long id : deletedPosts) { iterationMap.remove(id); actsForPost.remove(id); authCursForPost.remove(id); }
		/* Calcolo ricompense  e #iterazione per ogni post */
		for (long id : iterationMap.keySet()) {
			double iter = iterationMap.get(id);
			iterationMap.put(id, iter + 1.0);
		}
		for (long id : actsForPost.keySet()) {
			
			Double iter = iterationMap.get(id);
			if (iter == null) { iter = Double.valueOf(1.0); iterationMap.put(id, 1.0); }
			
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
				currew = (this.rewCurs * prew) / ((double)curators.size());
				/* Ricompensa autore */
				if (rewardsForUser.containsKey(author)) authrew += rewardsForUser.get(author);
				rewardsForUser.put(author, authrew);
				/* Ricompensa curatori */
				for (String cur : curators) {
					double d = currew;
					if (rewardsForUser.containsKey(cur)) d += rewardsForUser.get(cur);
					rewardsForUser.put(new String(cur), d);
				}
			}
		}
		return rewardsForUser;
	}
	
	public ToDoubleFunction<Map<String, Integer>> getVoteStrategy() { return voteStrategy; }
	public ToDoubleFunction<Map<String, List<Integer>>> getCommentStrategy() { return commentStrategy; }
	public Map<Long, Double> getIterationMap(){ return iterationMap; } 
	public double getRewAuth() { return rewAuth; }
	public double getRewCurs() { return rewCurs; }
	
	public String toString() { return Common.jsonString(this); }
}