package com.thesis.recommender;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.PrefetchingItemDAO;
import org.grouplens.lenskit.data.dao.PrefetchingItemEventDAO;
import org.grouplens.lenskit.data.dao.SortOrder;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.source.DataSource;

/**
 * A class to retrieve the standard seed items set. 
 * i1 ← the most popular item, i.e. the item with the greatest number of ratings 
 * 
 * i2 ← the most recently added item not rated yet (new item in the platform)
 * 
 * i3 ← the last positively rated item (the last item added in the platform rated in a positive way)
 * 
 * i4 ← the most popular item in a certain period of time (last week/month/year)
 * 
 * An item is "positive rated" if its rate is equals or greater than the global mean of all ratings.
 */
public class SeedItemSet {

	public static enum Period {LAST_WEEK, LAST_MONTH, LAST_YEAR, EVER};

	private HashSet<Long> set;
	private ItemEventDAO iedao;
	private ItemDAO idao;
	private EventDAO dao;

	private Date firstTimestamp = null;
	private Date lastTimestamp = null;

	public SeedItemSet(DataSource dataset) {
		this.iedao = dataset.getItemEventDAO();
		this.idao = dataset.getItemDAO();
		this.dao = dataset.getEventDAO();
		this.set = new HashSet<Long>();
		find();
	}

	public SeedItemSet(EventDAO dao) {
		this.iedao = new PrefetchingItemEventDAO(dao);
		this.idao = new PrefetchingItemDAO(dao);
		this.dao = dao;
		this.set = new HashSet<Long>();
		find();
	}

	private void find() {
		set.add(getMostPopularItem(Period.EVER));
		set.add(getMostPopularItem(Period.LAST_WEEK));
		set.add(getLastPositivelyRatedItem());
		set.add(getLastItemAddedNotRated());
	}

	public Set<Long> getSeedItemSet() {
		return set;
	}

	private Long getMostPopularItem(Period period) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(getLastTimestamp()); 

		Date thresholdDate = null;
		switch (period) {
		case LAST_WEEK: cal.add(Calendar.DATE, -7); thresholdDate=cal.getTime(); break;
		case LAST_MONTH:cal.add(Calendar.DATE, -30); thresholdDate=cal.getTime();  break;
		case LAST_YEAR: cal.add(Calendar.DATE, -365); thresholdDate=cal.getTime(); break;
		case EVER: break;
		}

		Long idItemMostPopular = null;
		int max=0;
		for(Long itemId : idao.getItemIds()){
			List<Event> events = iedao.getEventsForItem(itemId);
			int count = 0;
			if(thresholdDate == null)
				count = events.size();
			else
			{
				thresholdDate.setHours(0);
				thresholdDate.setMinutes(0);
				thresholdDate.setSeconds(0);
				for(Event ev : events){
					Date rateDate = new Date(ev.getTimestamp()*1000);
					if(rateDate.after(thresholdDate))
						count++;
				}
			}

			if(count > max){
				idItemMostPopular=itemId;
				max=count;
			}
		}

		return idItemMostPopular;
	}

	private Date getFirstTimestamp(){

		if(firstTimestamp == null){
			Cursor<Rating> events = dao.streamEvents(Rating.class, SortOrder.TIMESTAMP);

			for(Rating rating : events){
				firstTimestamp = new Date(rating.getTimestamp()*1000);
				return firstTimestamp;	
			}
		}
		return firstTimestamp;
	}

	private Date getLastTimestamp(){

		if(lastTimestamp == null){
			Cursor<Rating> events = dao.streamEvents(Rating.class, SortOrder.TIMESTAMP);

			long lastTimestampTemp=0;
			for(Rating rating : events)
				lastTimestampTemp = rating.getTimestamp();			
			
			lastTimestamp = new Date(lastTimestampTemp*1000);
		}
		return lastTimestamp;
	}




	private Long getLastPositivelyRatedItem(){
		Long lastPositivelyRatedItem = null;
		Date recentDate = getFirstTimestamp(); 

		for(Long itemId : idao.getItemIds()){
			List<Rating> ratings = iedao.getEventsForItem(itemId, Rating.class);
			int threshold = getPositiveRatingThreshold(ratings);
			Date date = getFirstTimestamp();

			for(Rating rating : ratings){
				Date dateR = new Date(rating.getTimestamp()*1000);
				if(rating.getValue() >= threshold && dateR.after(date)){
					date = dateR;
				}
			}

			if(date.after(recentDate))
				recentDate = date;
			lastPositivelyRatedItem = itemId;
		}

		return lastPositivelyRatedItem;
	}

	private int getPositiveRatingThreshold(List<Rating> ratings){
		int threshold=0;
		for(Rating r : ratings)
			threshold += r.getValue();
		return threshold/ratings.size();
	}



	private Long getLastItemAddedNotRated(){
		Long lastItemAdded = null;
		Date threshold = null;

		for(Long itemId : idao.getItemIds()){
			List<Rating> ratings = iedao.getEventsForItem(itemId, Rating.class);
			Date firstTimestamp=new Date(ratings.get(0).getTimestamp()*1000);
			if(threshold == null){
				threshold = firstTimestamp;
				lastItemAdded=itemId;
			}

			for(Rating r : ratings){
				Date timestamp = new Date(r.getTimestamp()*1000);
				if(timestamp.before(firstTimestamp))
					firstTimestamp = timestamp;
			}

			if(firstTimestamp.after(threshold)){
				threshold=firstTimestamp;
				lastItemAdded=itemId;
			}
		}


		return lastItemAdded;
	}

}