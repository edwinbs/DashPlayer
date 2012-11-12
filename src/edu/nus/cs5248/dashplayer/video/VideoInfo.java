package edu.nus.cs5248.dashplayer.video;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;

import edu.nus.cs5248.dashplayer.streamer.DashResult;
import edu.nus.cs5248.dashplayer.streamer.DashStreamer;

public class VideoInfo {

    public static class VideoInfoItem {

        public int id;
        public String title;
        public boolean isFinalized;

        public VideoInfoItem(int id, String title, boolean isFinalized) {
            this.id = id;
            this.title = title;
            this.isFinalized = isFinalized;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public static List<VideoInfoItem> ITEMS = new ArrayList<VideoInfoItem>();
    public static Map<Integer, VideoInfoItem> ITEM_MAP = new HashMap<Integer, VideoInfoItem>();
    
    private static void addItem(VideoInfoItem item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.id, item);
    }
    
    public static void updateVideoList() {
    	Log.d("VideoInfo", "Fetching video list from server...");
    	DashStreamer.INSTANCE.getVideoList(new DashStreamer.GetVideoListCallback() {
			
			@Override
			public void getVideoListDidFinish(int result, List<VideoInfoItem> videoInfos) {
				Log.d("VideoInfo", "Get video list finished, result=" + result);
				
				if (result != DashResult.OK)
					return;
				
				ITEMS.clear();
				
				for (VideoInfoItem item : videoInfos) {
					Log.d("VideoInfo", "id=" + item.id + " title=" + item.title + " isFinalized=" + item.isFinalized);
					if (item.isFinalized) {
						addItem(item);
					}
				}
			}
		});
    }
}
