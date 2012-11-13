package edu.nus.cs5248.dashplayer.video;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import android.util.SparseArray;

import edu.nus.cs5248.dashplayer.streamer.DashResult;
import edu.nus.cs5248.dashplayer.streamer.DashStreamer;

public class VideoInfo {
	protected static final String TAG = "VideoInfo";

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
    
    public static interface UpdateVideoListCallback {
    	public void updateVideoListDidFinish(int result, List<VideoInfoItem> videoInfos);
    }

    public static List<VideoInfoItem> ITEMS = new ArrayList<VideoInfoItem>();
    public static SparseArray<VideoInfoItem> ITEM_MAP = new SparseArray<VideoInfoItem>(32);
    
    private static void addItem(VideoInfoItem item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.id, item);
    }
    
    public static void updateVideoList(final UpdateVideoListCallback callback) {
    	Log.d(TAG, "Fetching video list from server...");
    	DashStreamer.INSTANCE.getVideoList(new DashStreamer.GetVideoListCallback() {
			
			@Override
			public void getVideoListDidFinish(int result, List<VideoInfoItem> videoInfos) {
				Log.d(TAG, "Get video list finished, result=" + result);
				
				if (result != DashResult.OK)
					return;
				
				ITEMS.clear();
				ITEM_MAP.clear();
				
				for (VideoInfoItem item : videoInfos) {
					Log.v(TAG, "id=" + item.id + " title=" + item.title + " isFinalized=" + item.isFinalized);
					if (item.isFinalized) {
						addItem(item);
					}
				}
				
				if (callback != null) {
					callback.updateVideoListDidFinish(result, videoInfos);
				}
			}
		});
    }
}
