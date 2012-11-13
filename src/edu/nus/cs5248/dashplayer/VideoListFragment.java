package edu.nus.cs5248.dashplayer;

import java.util.List;

import edu.nus.cs5248.dashplayer.video.VideoInfo;
import edu.nus.cs5248.dashplayer.video.VideoInfo.VideoInfoItem;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class VideoListFragment extends ListFragment {

	protected static final String TAG = "VideoListFragment";
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    private Callbacks mCallbacks = sDummyCallbacks;
    private int mActivatedPosition = ListView.INVALID_POSITION;
    private ArrayAdapter<VideoInfo.VideoInfoItem> mAdapter = null;

    public interface Callbacks {

        public void onItemSelected(int id);
    }

    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(int id) {
        	Log.d(TAG, "Selected video id=" + id);
        }
    };

    public VideoListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate");
        
        this.mAdapter = new ArrayAdapter<VideoInfo.VideoInfoItem>(getActivity(),
                android.R.layout.simple_list_item_activated_1,
                android.R.id.text1,
                VideoInfo.ITEMS);
        
        setListAdapter(this.mAdapter);   
        this.updateVideoList();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null && savedInstanceState
                .containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = sDummyCallbacks;
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        mCallbacks.onItemSelected(VideoInfo.ITEMS.get(position).id);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    public void setActivateOnItemClick(boolean activateOnItemClick) {
        getListView().setChoiceMode(activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE);
    }

    public void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

	public void updateVideoList() {
		VideoInfo.updateVideoList(new VideoInfo.UpdateVideoListCallback() {
			
			@Override
			public void updateVideoListDidFinish(int result,
					List<VideoInfoItem> videoInfos) {
				mAdapter.notifyDataSetChanged();
			}
		});
	}
}
