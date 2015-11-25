package it.maivisto.models;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;

import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.UnlimitedScoredItemAccumulator;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import it.maivisto.utility.SerializeModel;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import ts.evaluation.FeatureSet;
import ts.evaluation.impl.VincenteTS;

/**
 * Build a item content similarity model.
 */
@NotThreadSafe
public class ItemContentMatrixModelBuilder implements Provider<ItemItemModel> {
	private static final Logger logger = LoggerFactory.getLogger(ItemContentMatrixModelBuilder.class);

	private final ItemItemBuildContext context;
	private long limit=0;

	@Inject
	public ItemContentMatrixModelBuilder(@Transient ItemItemBuildContext context) {
		this.context = context;
	}


	@Override
	public ItemItemModel get() {
		ItemContentMatrixModel model=null;
		SerializeModel serial=new SerializeModel();
		
		
				
		if(serial.deSerializeModel("ItemContentMatrixModel")==null){
			LongSortedSet allItems = context.getItems();
			int nitems = allItems.size();
			VincenteTS valueSim = null;
			HashMap<Long,String> icMap = getItemsContentMap();
			limit=allItems.size()*allItems.size();
			int threadCount=0;
			logger.info("building item-content similarity model for {} items", nitems);
			logger.info("item-content similarity model is symmetric");

			Long2ObjectMap<ScoredItemAccumulator> rows = makeAccumulators(allItems);
			try {
				valueSim = new VincenteTS("lib\\TextualSimilarity\\config\\config.properties","lib\\TextualSimilarity\\config\\stacking.xml");

	
			Stopwatch timer = Stopwatch.createStarted();
			int ndone=1;
			for(LongBidirectionalIterator itI = allItems.iterator(); itI.hasNext() ; ) {
				Long i = itI.next();

				if (logger.isDebugEnabled()) 
					logger.info("computing similarities for item {} ({} of {})", i, ndone, nitems);

				for(LongBidirectionalIterator itJ = allItems.iterator(i); itJ.hasNext(); ) {
					Long j = itJ.next();
                     
					String contentI = icMap.get(i);
					String contentJ = icMap.get(j);


					double simIJ;

					//trova similarit� tra contentI e contentJ
					simIJ = valueSim.computeSimilarity(contentI, contentJ).getValue("stacking"); 
				
					rows.get(i).put(j, simIJ);
					rows.get(j).put(i, simIJ);
					logger.info("calcolata riga{}, colonna {} sim->{}", i, j, simIJ);
				
				    
				
					
				}

				if (logger.isDebugEnabled() && ndone % 100 == 0) 
					logger.info("computed {} of {} model rows ({}s/row)", 
							ndone, nitems, 
							String.format("%.3f", timer.elapsed(TimeUnit.MILLISECONDS) * 0.001 / ndone));

				ndone++;
				
			}
		
			
			timer.stop();
			logger.info("built model for {} items in {}", ndone, timer);
			
			model=new ItemContentMatrixModel(finishRows(rows));
			logger.info("Serializing ItemContentMatrixModel...");
			serial.serializeModel(model,"ItemContentMatrixModel");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				logger.info("Err VINCENTE: {}",e.getStackTrace());
			}
		}
		else
			model=(ItemContentMatrixModel) serial.deSerializeModel("ItemContentMatrixModel");
		return model;
	}
	
	

	private HashMap<Long,String> getItemsContentMap(){
		HashMap<Long,String> icMap = new HashMap<Long,String>();
		for(long item : context.getItems()){
			String content = "";
			try {
				content = readItemContent(item);
			}catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			icMap.put(item, content);
		}
		return icMap;
	}

	private String readItemContent(long item) throws IOException{
		StringBuilder sb = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader(new File("data\\abstract\\"+item)));
		String s = "";
		while((s = br.readLine()) != null)
			sb.append(s);
		br.close();
		logger.info("Read content item {}",item);
		return sb.toString();
	}

	private Long2ObjectMap<ScoredItemAccumulator> makeAccumulators(LongSet items) {
		Long2ObjectMap<ScoredItemAccumulator> rows = new Long2ObjectOpenHashMap<ScoredItemAccumulator>(items.size());
		for(Long item : items)
			rows.put(item, new UnlimitedScoredItemAccumulator());       
		return rows;
	}

	private Long2ObjectMap<ImmutableSparseVector> finishRows(Long2ObjectMap<ScoredItemAccumulator> rows) {
		Long2ObjectMap<ImmutableSparseVector> results = new Long2ObjectOpenHashMap<ImmutableSparseVector>(rows.size());
		for (Long2ObjectMap.Entry<ScoredItemAccumulator> e: rows.long2ObjectEntrySet()) 
			results.put(e.getLongKey(), e.getValue().finishVector().freeze());      
		return results;
	}
}