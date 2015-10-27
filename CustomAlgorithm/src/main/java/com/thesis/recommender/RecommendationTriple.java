package com.thesis.recommender;

public class RecommendationTriple {
	private long itemID;
	private double score;
	private int matID;
	
	public RecommendationTriple(long itemID, double score, int matID) {
		this.itemID=itemID;
		this.score=score;
		this.matID=matID;
	}

	public long getItemID() {
		return itemID;
	}

	public double getScore() {
		return score;
	}

	public int getMatID() {
		return matID;
	}

}
