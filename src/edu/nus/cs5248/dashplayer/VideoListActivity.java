package edu.nus.cs5248.dashplayer;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class VideoListActivity extends FragmentActivity
        implements VideoListFragment.Callbacks {

	protected static final String TAG = "VideoListActivity";
    private boolean mTwoPane;
    private VideoDetailFragment activeDetailFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate");
        
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        setContentView(R.layout.activity_video_list);

        if (findViewById(R.id.video_detail_container) != null) {
            mTwoPane = true;
            ((VideoListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.video_list))
                    .setActivateOnItemClick(true);
        }
    }

    @Override
    public void onItemSelected(int id) {
        if (mTwoPane) {
            Bundle arguments = new Bundle();
            arguments.putInt(VideoDetailFragment.ARG_ITEM_ID, id);
            this.activeDetailFragment = new VideoDetailFragment();
            this.activeDetailFragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.video_detail_container, this.activeDetailFragment)
                    .commit();

        } else {
            Intent detailIntent = new Intent(this, VideoDetailActivity.class);
            detailIntent.putExtra(VideoDetailFragment.ARG_ITEM_ID, id);
            startActivity(detailIntent);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dash_player_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.refresh:
            	((VideoListFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.video_list)).updateVideoList();
            	return true;
            case R.id.play_menu:
    	    	if (this.activeDetailFragment != null) {
    	    		this.activeDetailFragment.playMenuSelected();
    	    	}
    	    	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
