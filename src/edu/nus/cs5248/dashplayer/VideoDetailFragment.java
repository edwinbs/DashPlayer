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
import android.widget.RelativeLayout;
import android.widget.TextView;

public class VideoDetailFragment extends Fragment {

	protected static final String TAG = "VideoDetailFragment";
    public static final String ARG_ITEM_ID = "item_id";

    VideoInfo.VideoInfoItem mItem;
    
    private RelativeLayout	mediaContainer;
    
    private TextView 		bandwidthText;
    private TextView 		bufferText;
    private TextView 		statusText;
    
    private MediaPlayer 	currentMediaPlayer;
    private SurfaceHolder	currentHolder;
    private SurfaceView		currentSurface;
    
    private MediaPlayer 	nextMediaPlayer;
    private SurfaceHolder	nextHolder;
    private SurfaceView		nextSurface;
    
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
        this.mediaContainer	= (RelativeLayout) rootView.findViewById(R.id.media_container);
        this.bandwidthText 	= (TextView) rootView.findViewById(R.id.bandwidth_text);
        this.bufferText 	= (TextView) rootView.findViewById(R.id.buffer_text);
        this.statusText 	= (TextView) rootView.findViewById(R.id.status_text);
        
        return rootView;
    }
    
    private void prepareNextPlayer(final VideoSegmentInfo segment) {
		Log.i(TAG, "Preparing player for: " + segment.getCacheFilePath());
		
    	this.nextSurface = new SurfaceView(this.getActivity());
        this.nextHolder  = this.nextSurface.getHolder();
        mediaContainer.addView(nextSurface, 0);
        
        this.nextHolder.addCallback(new SurfaceHolder.Callback() {
			
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				try {
					nextMediaPlayer = new MediaPlayer();
			        nextMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
						
						@Override
						public void onCompletion(MediaPlayer mp) {
							mp.release();
							startNextPlayer();
						}
					});
			        nextMediaPlayer.setDataSource(segment.getCacheFilePath());
			        nextMediaPlayer.setSurface(nextHolder.getSurface());
			        nextMediaPlayer.prepare();
			        
			        int videoWidth = nextMediaPlayer.getVideoWidth();
					int videoHeight = nextMediaPlayer.getVideoHeight();
					
					int surfaceWidth = nextHolder.getSurfaceFrame().width();
					ViewGroup.LayoutParams params = nextSurface.getLayoutParams();
					params.width = surfaceWidth;
					params.height = (int) (((float) videoHeight / (float) videoWidth) * (float) surfaceWidth);
					nextSurface.setLayoutParams(params);
					
					nextMediaPlayer.start();
					nextMediaPlayer.pause();
					
					if (currentMediaPlayer == null) {
						startNextPlayer();
					}
				} catch (Exception e) {
		    		e.printStackTrace();
		    	}
			}
			
			@Override
			public void surfaceDestroyed(SurfaceHolder holder) { }
			
			@Override
			public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
		});
    }
    
    private void startNextPlayer() {
    	Log.d(TAG, "Next player is starting...");
    	
    	SurfaceView previousSurface = this.currentSurface;
    	
    	this.currentMediaPlayer = this.nextMediaPlayer;
    	this.currentHolder = this.nextHolder;
    	this.currentSurface = this.nextSurface;
    	
    	if (this.currentMediaPlayer != null) {
    		this.currentMediaPlayer.start();
    	}
    	
    	if (previousSurface != null) {
    		try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		this.mediaContainer.removeView(previousSurface);
    	}
    	
    	this.nextMediaPlayer = null;
    	this.nextHolder = null;
    	this.nextSurface = null;
    	
    	scheduleNext();
    }
    
	public synchronized void queueForPlayback(final VideoSegmentInfo segment) {
		this.readySegments.offer(segment);
		bufferContentChanged();
		
		if (this.activeSegment == null) {
			this.scheduleNext();
		}
	}

	private void bufferContentChanged() {
		int count = this.readySegments.size();
		
		if (count > 8) {
			DashStreamer.INSTANCE.changeStreamingStrategy(DashStreamer.HALT);
		} else if (count > 4) {
			DashStreamer.INSTANCE.changeStreamingStrategy(DashStreamer.AT_LEAST_FOUR_SECONDS);
		} else {
			DashStreamer.INSTANCE.changeStreamingStrategy(DashStreamer.AS_FAST_AS_POSSIBLE);
		}
		
		if (this.readySegments != null) {
			this.bufferText.setText(Integer.toString(this.readySegments.size()));
		} else {
			this.bufferText.setText("0");
		}
	}

	private synchronized void scheduleNext() {
		this.activeSegment = this.readySegments.poll();
		bufferContentChanged();
		
		if (this.activeSegment != null) {
			prepareNextPlayer(this.activeSegment);
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
