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
		new StreamVideoTask().execute(new StreamVideoTaskParam(title, context, callback));
	}
	
	public static String urlFor(String restAction) {
		return BASE_URL + restAction;
	}
	
	public static String playlistURLFor(String title, boolean m3u8) {
		return BASE_URL + PLAYLIST + title + "." + (m3u8 ? "m3u8" : "mpd");
	}
	
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

	@Override
	protected Integer doInBackground(StreamVideoTaskParam... params) {		
		this.callback = params[0].callback;
		this.title = params[0].title;
		this.context = params[0].context;
		
		Playlist playlist = this.getPlaylist();
		
		if (playlist == null) {
			return DashResult.FAIL;
		}
		
		int quality = getDefaultQualityForCurrentConnection(this.context);
		this.estimatedBandwidth = 0;
		
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
				
				int newQuality = this.qualityForCurrentBandwidth();
				if (newQuality != quality) {
					Log.i(TAG, "Switching quality from " + quality + "p to " + newQuality + "p");
					quality = newQuality;
				}
				
				publishProgress(new StreamingProgressInfo(this.estimatedBandwidth, segment));;
			}
			else {
				Log.d(TAG, "Download failed, aborting");
				return DashResult.FAIL;
			}
		}
		
		return DashResult.OK;
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
		return DashStreamer.CACHE_FOLDER + fileName;
	}
	
	public static int getDefaultQualityForCurrentConnection(Context context) {		
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        int defaultQuality = 0;
        
        if (info == null || !info.isConnected()) {
        	Log.d(TAG, "No network connection");
        	defaultQuality = 0;
        }
        else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
        	WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        	int linkSpeedMbps = wm.getConnectionInfo().getLinkSpeed();
        	
        	Log.d(TAG, "Connection: WiFi (" + linkSpeedMbps + " Mb/s)");
        	
        	if (linkSpeedMbps >= 100) { //Because in practice link speeds < 100Mbps is bad
        		defaultQuality = Playlist.QUALITY_HIGH;
        	}
        	else {
        		defaultQuality = Playlist.QUALITY_MEDIUM;
        	}
        }
        else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
        	Log.d(TAG, "Connection: Mobile (" + info.getSubtypeName() + ")");
        	switch (info.getSubtype()) {
        	
        	case TelephonyManager.NETWORK_TYPE_EVDO_0:
        	case TelephonyManager.NETWORK_TYPE_EVDO_A:
        	case TelephonyManager.NETWORK_TYPE_HSPA:
        	case TelephonyManager.NETWORK_TYPE_UMTS:
        	case TelephonyManager.NETWORK_TYPE_EHRPD:
        		defaultQuality = Playlist.QUALITY_MEDIUM;
        		break;
        		
        	case TelephonyManager.NETWORK_TYPE_HSDPA:
        	case TelephonyManager.NETWORK_TYPE_HSUPA:
        	case TelephonyManager.NETWORK_TYPE_EVDO_B:
        	case TelephonyManager.NETWORK_TYPE_HSPAP:
        	case TelephonyManager.NETWORK_TYPE_LTE:
        		defaultQuality = Playlist.QUALITY_HIGH;
        		break;
        		
        	default:
        		defaultQuality = Playlist.QUALITY_LOW;
        		break;
        	}
        }
        
        return defaultQuality;
	}
	
	private void updateEstimatedBandwidth(final long lastDownloadBandwidth) {
		if (this.estimatedBandwidth == 0) {
			this.estimatedBandwidth = lastDownloadBandwidth;
		}
		else {
			this.estimatedBandwidth = (long) (0.5 * this.estimatedBandwidth + 0.5 * lastDownloadBandwidth);
		}
	}
	
	private int qualityForCurrentBandwidth() {
		if ((this.estimatedBandwidth) >= (3096000 / 8)) {
			return Playlist.QUALITY_HIGH;
		}
		
		if ((this.estimatedBandwidth) >= (768000 / 8)) {
			return Playlist.QUALITY_MEDIUM;
		}
		
		return Playlist.QUALITY_LOW;
	}
	
	private String title;
	private long   estimatedBandwidth;
	private Context context;
	private DashStreamer.StreamVideoCallback callback;
}