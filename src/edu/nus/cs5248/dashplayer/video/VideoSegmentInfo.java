package edu.nus.cs5248.dashplayer.video;

import java.util.HashMap;
import java.util.Map;

public class VideoSegmentInfo {

	public VideoSegmentInfo() {
		this.cacheFilePath = "";
		this.sourceURLs = new HashMap<Integer, String>();
	}
	
	public void setCacheInfo(int quality, String cacheFilePath) {
		this.cacheQuality = quality;
		this.cacheFilePath = cacheFilePath;
	}
	
	public String getCacheFilePath() {
		return this.cacheFilePath;
	}
	
	public int getCacheQuality() {
		return this.cacheQuality;
	}
	
	public String getURLForQuality(int quality) {
		return this.sourceURLs.get(quality);
	}
	
	public void setURLForQuality(int quality, String url) { 
		this.sourceURLs.put(quality, url);
	}
	
	private int cacheQuality;
	private String cacheFilePath;
	private Map<Integer, String> sourceURLs;
	
}
