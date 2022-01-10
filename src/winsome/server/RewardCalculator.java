package winsome.server;

import java.util.*;
import java.util.function.*;

import winsome.server.action.*;

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
	
	public ToDoubleFunction< Map<String, V> > getVoteStrategy();
	public ToDoubleFunction< Map<String, C> > getCommentStrategy();
	
	public double getRewAuth();
	public double getRewCurs();
	
	public double postReward(double iteration, List<Action> actions);
	public Map<String, Double> computeReward(List<Action> actions);
}