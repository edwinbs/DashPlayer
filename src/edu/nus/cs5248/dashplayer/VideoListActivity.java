package edu.nus.cs5248.dashplayer;

import edu.nus.cs5248.dashplayer.video.VideoInfo;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class VideoListActivity extends FragmentActivity
        implements VideoListFragment.Callbacks {

    private boolean mTwoPane;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d("VideoListActivity", "onCreate");
        
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
            VideoDetailFragment fragment = new VideoDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.video_detail_container, fragment)
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
        inflater.inflate(R.menu.list_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.refresh:
            	VideoInfo.updateVideoList();
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
