package edu.nus.cs5248.dashplayer.streamer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.nus.cs5248.dashplayer.video.Playlist;
import edu.nus.cs5248.dashplayer.video.VideoInfo;
import edu.nus.cs5248.dashplayer.video.VideoSegmentInfo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;

public enum DashStreamer {

	INSTANCE; //Singleton
	
	protected static final String TAG = "DashStreamer";
	
	public static interface GetVideoListCallback {
		public void getVideoListDidFinish(int result, List<VideoInfo.VideoInfoItem> videoInfos);
	}
	
	public static interface StreamVideoCallback {
		public void streamletDownloadDidFinish(VideoSegmentInfo segmentInfo, long bandwidthBytePerSec);
	}
	
	public void getVideoList(final GetVideoListCallback callback) {
		new GetVideoListTask().execute(new GetVideoListTaskParam(callback));
	}
	
	public void streamVideo(final String title, final Context context, final StreamVideoCallback callback) {
		this.streamVideoTask = new StreamVideoTask();
		this.streamVideoTask.execute(new StreamVideoTaskParam(title, context, callback));
	}
	
	public void changeStreamingStrategy(final int strategy) {
		this.streamVideoTask.setStrategy(strategy);
	}
	
	public static String urlFor(String restAction) {
		return BASE_URL + restAction;
	}
	
	public static String playlistURLFor(String title, boolean m3u8) {
		return BASE_URL + PLAYLIST + title + "." + (m3u8 ? "m3u8" : "mpd");
	}
	
	private StreamVideoTask streamVideoTask;
	
	public static final String VIDEOS_INDEX			= "videos_index.php";
	public static final String PLAYLIST				= "video_files/";

	public static final String VIDEO_TITLE 			= "title";
	public static final String ID 					= "id";
	public static final String RESULT 				= "result";
	public static final String IS_FINAL_STREAMLET 	= "is_final_streamlet";
	public static final String VIDEO_ID 			= "video_id";
	public static final String FILE 				= "file";
	public static final String VIDEO_STREAMLETS 	= "video_streamlets";
	public static final String FILENAME 			= "filename";
	public static final String VIDEOS 				= "videos";
	public static final String TITLE 				= "title";
	public static final String IS_FINALIZED 		= "is_finalized";
	
	//Download strategies
	public static final int HALT = 0;
	public static final int AS_FAST_AS_POSSIBLE = 1;
	public static final int AT_LEAST_FOUR_SECONDS = 2;

	private static final String BASE_URL = "http://pilatus.d1.comp.nus.edu.sg/~a0082245/";
	
	public static final String CACHE_FOLDER			= new File(Environment.getExternalStorageDirectory().getPath(), "dash_cache/").getPath();
}

class GetVideoListTaskParam {
	public GetVideoListTaskParam(final DashStreamer.GetVideoListCallback callback) {
		this.callback = callback;
	}
	
	DashStreamer.GetVideoListCallback callback;
}

class GetVideoListTask extends AsyncTask<GetVideoListTaskParam, Integer, Integer> {
	protected static final String TAG = "GetVideoListTask";

	@Override
	protected Integer doInBackground(GetVideoListTaskParam... params) {
		int result = DashResult.FAIL;
		this.callback = params[0].callback;
		
		try {
			HttpClient client = new DefaultHttpClient();
			String getURL = DashStreamer.urlFor(DashStreamer.VIDEOS_INDEX);
			HttpGet get = new HttpGet(getURL);
			HttpResponse getResponse = client.execute(get);
			HttpEntity responseEntity = getResponse.getEntity();
			
			if (responseEntity != null) {
				JSONObject response = new JSONObject(
						EntityUtils.toString(responseEntity));
	
				result = response.getInt(DashStreamer.RESULT);
	
				this.videoInfos = new ArrayList<VideoInfo.VideoInfoItem>();
				JSONArray arr = response
						.getJSONArray(DashStreamer.VIDEOS);
				
				for (int i = 0; i < arr.length(); ++i) {
					JSONObject videoInfoObject = arr.getJSONObject(i);
					int videoId = videoInfoObject.getInt(DashStreamer.ID);
					String title = videoInfoObject.getString(DashStreamer.TITLE);
					boolean isFinalized = (videoInfoObject.getInt(DashStreamer.IS_FINALIZED) == 1);
					
					this.videoInfos.add(new VideoInfo.VideoInfoItem(videoId, title, isFinalized));
				}
			}
		} catch (ClientProtocolException e) {
			Log.e(TAG, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "IO exception: " + e.getMessage());
		} catch (ParseException e) {
			Log.e(TAG, "JSON parse exception: " + e.getMessage());
		} catch (JSONException e) {
			Log.e(TAG, "JSON exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(TAG, "Unexpected exception: " + e.getMessage());
			e.printStackTrace();
		}
		
		return result;
	}
	
	protected void onPostExecute(Integer result) {
		if (callback != null) {
			callback.getVideoListDidFinish(result, this.videoInfos);
		}
	}
	
	private List<VideoInfo.VideoInfoItem> 		videoInfos;
	private DashStreamer.GetVideoListCallback 	callback;
	
}

class StreamVideoTaskParam {
	public StreamVideoTaskParam(String title, Context context, DashStreamer.StreamVideoCallback callback) {
		this.title = title;
		this.context = context;
		this.callback = callback;
	}
	
	String title;
	Context context;
	DashStreamer.StreamVideoCallback callback;
}

class StreamingProgressInfo {
	public StreamingProgressInfo(long bandwidth, VideoSegmentInfo segment) {
		this.bandwidthBytePerSec = bandwidth;
		this.lastDownloadedSegment = segment;
	}
	
	long bandwidthBytePerSec;
	VideoSegmentInfo lastDownloadedSegment;
}

class StreamVideoTask extends AsyncTask<StreamVideoTaskParam, StreamingProgressInfo, Integer> {
	protected static final String TAG = "StreamVideoTask";
	
	public StreamVideoTask() {
		super();
	}

	@Override
	protected Integer doInBackground(StreamVideoTaskParam... params) {		
		setStrategy(DashStreamer.AS_FAST_AS_POSSIBLE); //Always reset to default strategy when starting
		this.callback = params[0].callback;
		this.title = params[0].title;
		this.context = params[0].context;
		
		Playlist playlist = this.getPlaylist();
		
		if (playlist == null) {
			return DashResult.FAIL;
		}
		
		//The initial quality is decided based on the network link type
		//The estimated bandwidth here is NOT used for calculating subsequent estimated bandwidth
		int quality = playlist.getQualityForBandwidth(getEstimatedBandwidthForCurrentConnection(this.context));
		
		this.estimatedBandwidth = 0; //0 will be treated as uninitialized
		
		for (VideoSegmentInfo segment : playlist) {
			String url = segment.getURLForQuality(quality);
			Log.d(TAG, "Next URL: " + url);
			
			long startTime = System.currentTimeMillis();
			String cacheFilePath = downloadFile(url);
			long endTime = System.currentTimeMillis();
			
			if (cacheFilePath != null && !cacheFilePath.isEmpty()) {
				segment.setCacheInfo(quality, cacheFilePath);
				
				long downloadSpeed = 1000 * (new File(cacheFilePath)).length() / (endTime - startTime);
				Log.d(TAG, "Last download speed=" + downloadSpeed);
				this.updateEstimatedBandwidth(downloadSpeed);
				
				int newQuality = playlist.getQualityForBandwidth(this.estimatedBandwidth);
				if (newQuality != quality) {
					Log.i(TAG, "Switching quality from " + quality + "p to " + newQuality + "p");
					quality = newQuality;
				}
				
				publishProgress(new StreamingProgressInfo(this.estimatedBandwidth, segment));
				actOnDownloadStrategy(endTime - startTime);
			}
			else {
				Log.d(TAG, "Download failed, aborting");
				return DashResult.FAIL;
			}
		}
		
		return DashResult.OK;
	}
	
	private void actOnDownloadStrategy(long lastDownloadTime) {
		while (this.getStrategy() == DashStreamer.HALT) {
			try {
				Log.d(TAG, "HALT. Waiting for strategy change event.");
				synchronized (this.strategyChangedEvent) {
					this.strategyChangedEvent.wait();
				}
			} catch (InterruptedException e) {
				Log.e(TAG, "Interrupted while on HALT mode.");
			}
		}
		
		if (this.getStrategy() == DashStreamer.AT_LEAST_FOUR_SECONDS) {
			long sleepTime = 4000 - lastDownloadTime;
			if (sleepTime > 0) {
				try {
					Log.d(TAG, "Sleeping for " + sleepTime + " ms before next download.");
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					Log.e(TAG, "Interrupted while on AT_LEAST_THREE_SECONDS mode.");
				}
			}
		}
	}
	
	protected void onProgressUpdate(StreamingProgressInfo... info) {
        if (this.callback != null) {
        	callback.streamletDownloadDidFinish(info[0].lastDownloadedSegment, info[0].bandwidthBytePerSec);
        }
    }
	
	private Playlist getPlaylist() {
		Playlist playlist = null;
		
		try {
			HttpClient client = new DefaultHttpClient();
			String getURL = DashStreamer.playlistURLFor(this.title, false);
			HttpGet get = new HttpGet(getURL);
			HttpResponse getResponse = client.execute(get);
			HttpEntity responseEntity = getResponse.getEntity();
			
			if (responseEntity != null) {
				playlist = Playlist.createFromMPD(EntityUtils.toString(responseEntity));
			}
		} catch (ClientProtocolException e) {
			Log.e(TAG, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "IO exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(TAG, "Unexpected exception: " + e.getMessage());
			e.printStackTrace();
		}
		
		return playlist;
	}
	
	private String downloadFile(String url) {
		FileOutputStream fos = null;
		String cacheFile = "";
		
		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet get = new HttpGet(url);
			HttpResponse getResponse = client.execute(get);
			HttpEntity responseEntity = getResponse.getEntity();
			
			if (responseEntity != null) {
				cacheFile = pathForCacheFile(url);
				fos = new FileOutputStream(new File(cacheFile));
				fos.write(EntityUtils.toByteArray(responseEntity));
				fos.close();
			}
		} catch (ClientProtocolException e) {
			Log.e(TAG, "Client protocol exception: " + e.getMessage());
			cacheFile = null;
		} catch (IOException e) {
			Log.e(TAG, "IO exception: " + e.getMessage());
			cacheFile = null;
		} catch (Exception e) {
			Log.e(TAG, "Unexpected exception: " + e.getMessage());
			e.printStackTrace();
			cacheFile = null;
		} finally {
			try { fos.close(); } catch (Exception e) { }
		}
		
		return cacheFile;
	}
	
	public static String extractFileName(String url) {
		return url.substring(url.lastIndexOf('/') + 1);
	}
	
	public static String pathForCacheFile(String url) {
		String fileName = extractFileName(url);
		return new File(DashStreamer.CACHE_FOLDER, fileName).getPath();
	}
	
	public static int getEstimatedBandwidthForCurrentConnection(Context context) {		
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        int estimatedBandwidth = 0;
        
        if (info == null || !info.isConnected()) {
        	Log.d(TAG, "No network connection");
        	estimatedBandwidth = 0;
        }
        else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
        	WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        	int linkSpeedMbps = wm.getConnectionInfo().getLinkSpeed();
        	
        	Log.d(TAG, "Connection: WiFi (" + linkSpeedMbps + " Mb/s)");
        	
        	if (linkSpeedMbps >= 10) {
        		estimatedBandwidth = HIGH_BANDWIDTH;
        	}
        	else if (linkSpeedMbps > 1) {
        		estimatedBandwidth = MEDIUM_BANDWIDTH;
        	}
        	else {
        		estimatedBandwidth = LOW_BANDWIDTH;
        	}
        }
        else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
        	Log.d(TAG, "Connection: Mobile (" + info.getSubtypeName() + ")");
        	switch (info.getSubtype()) {
        	
        	case TelephonyManager.NETWORK_TYPE_HSDPA:
        	case TelephonyManager.NETWORK_TYPE_HSUPA:
        	case TelephonyManager.NETWORK_TYPE_EVDO_B:
        	case TelephonyManager.NETWORK_TYPE_HSPAP:
        	case TelephonyManager.NETWORK_TYPE_LTE:
        		estimatedBandwidth = HIGH_BANDWIDTH;
        		break;
        	
        	case TelephonyManager.NETWORK_TYPE_EVDO_0:
        	case TelephonyManager.NETWORK_TYPE_EVDO_A:
        	case TelephonyManager.NETWORK_TYPE_HSPA:
        	case TelephonyManager.NETWORK_TYPE_UMTS:
        	case TelephonyManager.NETWORK_TYPE_EHRPD:
        		estimatedBandwidth = MEDIUM_BANDWIDTH;
        		break;
        		
        	default:
        		estimatedBandwidth = LOW_BANDWIDTH;
        		break;
        	}
        }
        
        return estimatedBandwidth;
	}
	
	private void updateEstimatedBandwidth(final long lastDownloadBandwidth) {
		if (this.estimatedBandwidth == 0) {
			this.estimatedBandwidth = lastDownloadBandwidth;
		}
		else {
			this.estimatedBandwidth = (long) (0.5 * this.estimatedBandwidth + 0.5 * lastDownloadBandwidth);
		}
	}
	
	public synchronized void setStrategy(final int newStrategy) {
		if (this.strategy != newStrategy) {
			if (newStrategy != DashStreamer.HALT && 
					newStrategy != DashStreamer.AS_FAST_AS_POSSIBLE && 
					newStrategy != DashStreamer.AT_LEAST_FOUR_SECONDS) {
				throw new RuntimeException("Invalid strategy: " + newStrategy);
			}
			
			this.strategy = newStrategy;
			
			synchronized (this.strategyChangedEvent) {
				this.strategyChangedEvent.notify();
			}
		}
	}
	
	private synchronized int getStrategy() {
		return this.strategy;
	}
	
	private String title;
	private long   estimatedBandwidth;
	private Context context;
	private DashStreamer.StreamVideoCallback callback;
	private int strategy;
	private Object strategyChangedEvent = new Object();
	
	//Rough estimate for link speed in kB/s,
	//used for deciding the quality to download for the first segment
	private static final int HIGH_BANDWIDTH 	= 387000; //3Mbps
	private static final int MEDIUM_BANDWIDTH 	=  96000; //768kbps
	private static final int LOW_BANDWIDTH 		=  25000; //200kbps
	
	
}