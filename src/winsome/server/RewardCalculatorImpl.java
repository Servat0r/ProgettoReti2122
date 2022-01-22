package winsome.server;

import java.util.*;
import java.util.function.*;

import winsome.server.data.*;
import winsome.util.*;

/**
 * Implementation of {@link RewardCalculator}.
 * @author Salvatore Correnti
 */
final class RewardCalculatorImpl implements RewardCalculator< List<Rate>, Map< String, Pair<Integer, List<Comment>> > > {
	
	private static final double transf(int cp) { return 2.0 / (1.0 + Math.exp(1-cp)); }
	
	/** Default vote strategy as described in project specification. */
	private static final ToDoubleFunction<List<Rate>>
		voteSumStrategy = votes -> {
			List<Double> rates = new ArrayList<Double>();
			CollectionsUtils.remap( votes, rates, v -> (v.isLike() ? +1.0 : -1.0) );
			return Math.log1p( Math.max(0.0, Common.doubleSum(rates)) );
		};
	
	/** Default comment strategy as described in project specification. */
	private static final ToDoubleFunction< Map<String, Pair<Integer, List<Comment>>> > cpStrategy = (map) -> {
		List<Double> userRew = new ArrayList<>();
		for (String str : map.keySet()) {
			int cp = map.get(str).getKey();
			userRew.add(transf(cp));
		}
		return Math.log1p( Common.doubleSum(userRew) );// collAccumulate(userRew, (p, q) -> p+q) );
	};
	
	/* Authors and curators rewards percentage normalized as in [0,1]. */
	private final double rewAuth, rewCurs;
	/** Vote strategy for calculating votes-related rewards. */
	private final ToDoubleFunction< List<Rate> > voteStrategy;
	/** Comment strategy for calculating comments-related rewards. */
	private final ToDoubleFunction< Map <String, Pair<Integer, List<Comment>>> > commentStrategy;
	
	public RewardCalculatorImpl(double rewAuth, double rewCur,
			ToDoubleFunction< List<Rate> > voteStrategy,
			ToDoubleFunction< Map <String, Pair<Integer, List<Comment>>> > commentStrategy) {
		double total = rewAuth + rewCur;
		this.rewAuth = rewAuth/total;
		this.rewCurs = rewCur/total;
		this.voteStrategy = (voteStrategy != null ? voteStrategy : voteSumStrategy);
		this.commentStrategy = (commentStrategy != null ? commentStrategy : cpStrategy);
	}
	
	public RewardCalculatorImpl(double rewAuth, double rewCur) { this(rewAuth, rewCur, null, null); }
	
	public ToDoubleFunction< List<Rate> > getVoteStrategy() { return voteStrategy; }
	public ToDoubleFunction< Map <String, Pair<Integer, List<Comment>>> > getCommentStrategy() { return commentStrategy; }
	public double getRewAuth() { return rewAuth; }
	public double getRewCurs() { return rewCurs; }
	
	public String toString() { return Common.jsonString(this); }
	
	public double postReward(Post post, Map<Long, Pair<String, Set<String>>> authsCurs, long timeout) {
		Common.notNull(post, authsCurs); Common.allAndArgs(timeout >= 0);
		
		List<Rate> recVotes = new ArrayList<>(), recLikes = new ArrayList<>();
		Map< String, Pair<Integer, List<Comment>> > recComms = new HashMap<>(); //{author -> (Cp, {new comments})}
		
		double iteration = post.getRewardData(recVotes, recComms, timeout);
		
		String author = post.getAuthor();
		Set<String> curs = new TreeSet<>();
		recLikes.addAll(recVotes);
		recLikes.removeIf( v -> !v.isLike() );
		CollectionsUtils.remap(recLikes, curs, Rate::getAuthor);
		CollectionsUtils.remap(recComms.keySet(), curs, str -> str);
		
		if (!curs.isEmpty()) {
			authsCurs.put( post.key(), new Pair<>(author, curs) );
			return (voteStrategy.applyAsDouble(recVotes) + commentStrategy.applyAsDouble(recComms))/iteration;
		} else return 0.0;
	}
	
	public Map<String, Double> computeReward(Table<Long, Post> posts, long timeout) {
		Common.notNull(posts); Common.allAndArgs(timeout >= 0);
		
		Map<Long, Double> postRews = new HashMap<>();
		Map<Long, Pair<String, Set<String>>> authsCurs = new HashMap<>();
		Map<String, Double> rewards = new HashMap<>();
		
		BiConsumer<String, Double> updater = (str, rew) -> rewards.put(str, rewards.get(str) + rew);
		
		Collection<Post> cp = posts.getAll();
		for (Post post : cp) {
			double prew = this.postReward(post, authsCurs, timeout);
			if (prew > 0.0) postRews.put(post.key(), prew);
		}
		
		for (Pair<String, Set<String>> pair : authsCurs.values()) {
			rewards.put(pair.getKey(), 0.0);
			pair.getValue().forEach( str -> rewards.put(str, 0.0) );
		}
		
		for (Long idPost : postRews.keySet()) {
			double prew = postRews.get(idPost);
			Pair<String, Set<String>> pair = authsCurs.get(idPost);
			double authrew = prew * rewAuth, currew = prew * rewCurs / pair.getValue().size();
			updater.accept(pair.getKey(), authrew);
			pair.getValue().forEach(str -> updater.accept(str, currew));
		}
		
		return rewards;
	}
	
}