package winsome.server;

import java.util.*;
import java.util.function.*;

import winsome.server.data.*;
import winsome.util.*;

/**
 * Winsome reward calculator template.
 * @author Salvatore Correnti
 *
 * @param <V> Type of the data structure associated with votes.
 * @param <C> Type of the data structure associated with comments.
 * 
 * @see RewardCalculatorImpl
 */
public interface RewardCalculator<V,C> {
	
	public ToDoubleFunction<V> getVoteStrategy();
	public ToDoubleFunction<C> getCommentStrategy();
	
	public double getRewAuth();
	public double getRewCurs();
	
	public double postReward(Post post, Map< Long, Pair<String, Set<String>> > authsCurs, long timeout);
	public Map<String, Double> computeReward(Table<Long, Post> posts, long timeout);
}