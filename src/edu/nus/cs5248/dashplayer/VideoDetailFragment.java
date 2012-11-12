package edu.nus.cs5248.dashplayer;

import java.io.IOException;
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
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

public class VideoDetailFragment extends Fragment {

    public static final String ARG_ITEM_ID = "item_id";

    VideoInfo.VideoInfoItem mItem;
    
    private SurfaceHolder holder;
    private MediaPlayer mediaPlayer;
    
    final Queue<String> readySegments = new ArrayDeque<String>();
    String activeSegment = null;

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
        View rootView = inflater.inflate(R.layout.fragment_video_detail, container, false);
        
        Button playButton = (Button) rootView.findViewById(R.id.play_button);
        playButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				playButtonClicked();
			}
		});
        
        this.holder = ((SurfaceView) rootView.findViewById(R.id.media_view)).getHolder();
        
        return rootView;
    }
    
    protected void playButtonClicked() {
    	DashStreamer.INSTANCE.streamVideo(this.mItem.title, new DashStreamer.StreamVideoCallback() {
			
			@Override
			public void streamletDownloadDidFinish(VideoSegmentInfo segmentInfo) {
				Log.d("VideoDetailFragment", "Quality=" + segmentInfo.getCacheQuality() + "p, Cache: " + segmentInfo.getCacheFilePath());
				queueForPlayback(segmentInfo.getCacheFilePath());
			}
		});
    }
    
    public void playVideo(final String videoPath) {
    	try {
    		Log.i("VideoDetailFragment::playVideo", "Now playing: " + videoPath);
	    	this.mediaPlayer = new MediaPlayer();
	    	this.mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
				
				@Override
				public void onCompletion(MediaPlayer mp) {
					mp.release();
					scheduleNext();
				}
			});
	    	this.mediaPlayer.setDataSource(videoPath);
			this.mediaPlayer.setSurface(this.holder.getSurface());
			this.mediaPlayer.prepare();
			this.mediaPlayer.start();
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
	public synchronized void queueForPlayback(final String videoPath) {
		this.readySegments.offer(videoPath);
		if (this.activeSegment == null) {
			this.scheduleNext();
		}
	}

	protected synchronized void scheduleNext() {
		if ((this.activeSegment = this.readySegments.poll()) != null) {
			playVideo(this.activeSegment);
		} else {
			Log.i("VideoDetailFragment::scheduleNext", "No more segments");
		}
	}
}
