import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

public class SeedRecommender extends AbstractItemRecommender {

	private EventDAO dao;
	private UserEventDAO uedao;
	private ItemDAO idao;
	private ItemScorer scorer;
	private ItemItemModel model;
	@Inject
	public SeedRecommender(EventDAO dao, UserEventDAO uedao, ItemDAO idao, ItemScorer scorer, ItemItemModel model) {
		this.uedao = uedao;
		this.idao = idao;
		this.dao = dao;
		this.scorer = scorer;
		this.model = model;
	}




	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {
		candidates = getEffectiveCandidates(user, candidates, excludes);

		ScoredItemAccumulator recommendations = new TopNScoredItemAccumulator(n);
		
		SeedItemSet set = new SeedItemSet(dao);
		Set<Long> seeds = set.getSeedItemSet();
		for(Long seed : seeds)
			recommendations.put(seed, scorer.score(user, seed));


		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);
		if (userHistory != null) 
			for(Rating rating : userHistory)
				seeds.add(rating.getItemId());

		for(Long item : seeds){
			SparseVector neighbors = model.getNeighbors(item);

			if(!neighbors.isEmpty()) {
				for(Long i : neighbors.keysByValue(true)){
					if(i != item){
						recommendations.put(i,scorer.score(user, i)); 
						break;
					}
				}
			}
		}

		return recommendations.finish();
	}


	private LongSet getEffectiveCandidates(long user, LongSet candidates, LongSet exclude) {
		if (candidates == null) 
			candidates = idao.getItemIds();

		if (exclude == null) {
			UserHistory<Event> userRatings = uedao.getEventsForUser(user);
			exclude = (userRatings == null) ? LongSets.EMPTY_SET : userRatings.itemSet();
		}

		if (!exclude.isEmpty()) {
			candidates = LongUtils.setDifference(candidates, exclude);
		}
		return candidates;
	}




	protected LongSet getPredictableItems(long user) {
		return idao.getItemIds();
	}
}
