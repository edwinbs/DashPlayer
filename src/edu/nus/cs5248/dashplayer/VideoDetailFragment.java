package edu.nus.cs5248.dashplayer;

import java.util.ArrayDeque;
import java.util.Queue;

import edu.nus.cs5248.dashplayer.streamer.DashStreamer;
import edu.nus.cs5248.dashplayer.video.VideoInfo;
import edu.nus.cs5248.dashplayer.video.VideoSegmentInfo;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class VideoDetailFragment extends Fragment {

	protected static final String TAG = "VideoDetailFragment";
    public static final String ARG_ITEM_ID = "item_id";

    VideoInfo.VideoInfoItem mItem;
    
    private SurfaceHolder 	holder;
    private SurfaceView   	mediaView;
    private MediaPlayer 	mediaPlayer;
    private TextView 		bandwidthText;
    private TextView 		bufferText;
    private TextView 		statusText;
    
    final Queue<VideoSegmentInfo> readySegments = new ArrayDeque<VideoSegmentInfo>();
    VideoSegmentInfo activeSegment = null;

    public VideoDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItem = VideoInfo.ITEM_MAP.get(getArguments().getInt(ARG_ITEM_ID));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	
        View rootView 		= inflater.inflate(R.layout.fragment_video_detail, container, false);
        this.mediaView 		= (SurfaceView) rootView.findViewById(R.id.media_view);
        this.holder 		= mediaView.getHolder();
        this.bandwidthText 	= (TextView) rootView.findViewById(R.id.bandwidth_text);
        this.bufferText 	= (TextView) rootView.findViewById(R.id.buffer_text);
        this.statusText 	= (TextView) rootView.findViewById(R.id.status_text);
        return rootView;
    }
    
    public void playVideo(final VideoSegmentInfo segment) {
    	try {
    		Log.i(TAG, "Playing: " + segment.getCacheFilePath());
    		this.statusText.setText(segment.getCacheFilePath() + " (" + segment.getCacheQuality() + "p)");
    		
	    	this.mediaPlayer = new MediaPlayer();
	    	this.mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
				
				@Override
				public void onCompletion(MediaPlayer mp) {
					mp.release();
					scheduleNext();
				}
			});
	    	this.mediaPlayer.setDataSource(segment.getCacheFilePath());
			this.mediaPlayer.setSurface(this.holder.getSurface());
			this.mediaPlayer.prepare();
			
			int videoWidth = this.mediaPlayer.getVideoWidth();
			int videoHeight = this.mediaPlayer.getVideoHeight();
			
			int surfaceWidth = this.holder.getSurfaceFrame().width();
			ViewGroup.LayoutParams params = this.mediaView.getLayoutParams();
			params.width = surfaceWidth;
			params.height = (int) (((float) videoHeight / (float) videoWidth) * (float) surfaceWidth);
			this.mediaView.setLayoutParams(params);
			
			this.mediaPlayer.start();
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
	public synchronized void queueForPlayback(final VideoSegmentInfo segment) {
		this.readySegments.offer(segment);
		bufferContentChanged();
		
		if (this.activeSegment == null) {
			this.scheduleNext();
		}
	}

	private void bufferContentChanged() {
		if (this.readySegments != null) {
			this.bufferText.setText(Integer.toString(this.readySegments.size()));
		}
		else {
			this.bufferText.setText("0");
		}
	}

	protected synchronized void scheduleNext() {
		this.activeSegment = this.readySegments.poll();
		bufferContentChanged();
		
		if (this.activeSegment != null) {
			playVideo(this.activeSegment);
		} else {
			Log.i(TAG, "Buffer is empty");
			this.statusText.setText(R.string.ready);
		}
	}

	public void playMenuSelected() {
		if (this.mItem == null) {
			return;
		}
		
		DashStreamer.INSTANCE.streamVideo(this.mItem.title, this.getActivity(), new DashStreamer.StreamVideoCallback() {
			
			@Override
			public void streamletDownloadDidFinish(VideoSegmentInfo segmentInfo, long bandwidthBytePerSec) {
				Log.d(TAG, "Quality=" + segmentInfo.getCacheQuality() + "p, Cache: " + segmentInfo.getCacheFilePath());
				queueForPlayback(segmentInfo);
				updateBandwidthText(humanReadableByteCount(bandwidthBytePerSec, true));
			}
		});
	}
	
	private void updateBandwidthText(String text) {
		this.bandwidthText.setText(text);
	}
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB/s", bytes / Math.pow(unit, exp), pre);
	}
}
