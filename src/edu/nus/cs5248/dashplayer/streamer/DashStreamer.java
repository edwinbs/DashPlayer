package edu.nus.cs5248.dashplayer.streamer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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

import android.os.AsyncTask;
import android.util.Log;

public enum DashStreamer {

	INSTANCE; //Singleton
	
	public static interface GetVideoListCallback {
		public void getVideoListDidFinish(int result, List<VideoInfo.VideoInfoItem> videoInfos);
	}
	
	public static interface StreamVideoCallback {
		public void streamletDownloadDidFinish(VideoSegmentInfo segmentInfo);
	}
	
	public void getVideoList(final GetVideoListCallback callback) {
		new GetVideoListTask().execute(new GetVideoListTaskParam(callback));
	}
	
	public void streamVideo(final String title, final StreamVideoCallback callback) {
		new StreamVideoTask().execute(new StreamVideoTaskParam(title, callback));
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
	
	public static final String CACHE_FOLDER			= "/sdcard/dash_cache/";
}

class GetVideoListTaskParam {
	public GetVideoListTaskParam(final DashStreamer.GetVideoListCallback callback) {
		this.callback = callback;
	}
	
	DashStreamer.GetVideoListCallback callback;
}

class GetVideoListTask extends AsyncTask<GetVideoListTaskParam, Integer, Integer> {

	@Override
	protected Integer doInBackground(GetVideoListTaskParam... params) {
		final String FN = "GetVideoListTask::doInBackground()";
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
			Log.e(FN, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(FN, "IO exception: " + e.getMessage());
		} catch (ParseException e) {
			Log.e(FN, "JSON parse exception: " + e.getMessage());
		} catch (JSONException e) {
			Log.e(FN, "JSON exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(FN, "Unexpected exception: " + e.getMessage());
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
	public StreamVideoTaskParam(String title, DashStreamer.StreamVideoCallback callback) {
		this.title = title;
		this.callback = callback;
	}
	
	String title;
	DashStreamer.StreamVideoCallback callback;
}

class StreamVideoTask extends AsyncTask<StreamVideoTaskParam, VideoSegmentInfo, Integer> {

	@Override
	protected Integer doInBackground(StreamVideoTaskParam... params) {
		this.callback = params[0].callback;
		this.title = params[0].title;
		
		Playlist playlist = this.getPlaylist();
		
		if (playlist == null) {
			return DashResult.FAIL;
		}
		
		int quality = Playlist.QUALITY_MEDIUM;
		for (VideoSegmentInfo segment : playlist) {
			String url = segment.getURLForQuality(quality);
			Log.d("StreamVideoTask::doInBackground", "Next URL: " + url);
			
			String cacheFilePath = downloadFile(url);
			segment.setCacheInfo(quality, pathForCacheFile(url));
			publishProgress(segment);
		}
		
		return DashResult.OK;
	}
	
	protected void onProgressUpdate(VideoSegmentInfo... segments) {
        if (this.callback != null) {
        	callback.streamletDownloadDidFinish(segments[0]);
        }
    }
	
	private Playlist getPlaylist() {
		final String FN = "StreamVideoTask::getPlaylist()";
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
			Log.e(FN, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(FN, "IO exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(FN, "Unexpected exception: " + e.getMessage());
			e.printStackTrace();
		}
		
		return playlist;
	}
	
	private String downloadFile(String url) {
		final String FN = "StreamVideoTask::downloadFile()";
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
			Log.e(FN, "Client protocol exception: " + e.getMessage());
		} catch (IOException e) {
			Log.e(FN, "IO exception: " + e.getMessage());
		} catch (Exception e) {
			Log.e(FN, "Unexpected exception: " + e.getMessage());
			e.printStackTrace();
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
	
	private String title;
	private DashStreamer.StreamVideoCallback callback;
	
}