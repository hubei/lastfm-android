package fm.last.android.activity;

import java.io.IOException;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.ViewFlipper;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.OnItemSelectedListener;
import fm.last.android.AndroidLastFmServerFactory;
import fm.last.android.LastFMApplication;
import fm.last.android.LastFm;
import fm.last.android.R;
import fm.last.android.RemoteImageHandler;
import fm.last.android.RemoteImageView;
import fm.last.android.SeparatedListAdapter;
import fm.last.android.Worker;
import fm.last.android.player.RadioPlayerService;
import fm.last.android.widget.TabBar;
import fm.last.android.widget.TabBarListener;
import fm.last.android.widget.NavBar;
import fm.last.android.widget.NavBarListener;
import fm.last.api.LastFmServer;
import fm.last.api.Session;
import fm.last.api.Station;
import fm.last.api.User;
import fm.last.api.ImageUrl;

public class Home extends ListActivity implements TabBarListener,NavBarListener
{

	private static int TAB_RADIO = 0;
	private static int TAB_PROFILE = 1;
	
    private SeparatedListAdapter mMainAdapter;
    private SeparatedListAdapter mProfileAdapter;
    private LastFMStreamAdapter mMyStationsAdapter;
    private LastFMStreamAdapter mMyRecentAdapter;
    private Worker mProfileImageWorker;
    private RemoteImageHandler mProfileImageHandler;
    private RemoteImageView mProfileImage;
    private User mUser;
	LastFmServer mServer = AndroidLastFmServerFactory.getServer();

	TabBar mTabBar;
	ViewFlipper mViewFlipper;
	ListView mProfileList;
	View previousSelectedView = null;
	
	public int test = 5;
	
    @Override
    public void onCreate( Bundle icicle )
    {

        super.onCreate( icicle );
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        setContentView( R.layout.home );
        Session session = ( Session ) LastFMApplication.getInstance().map
                .get( "lastfm_session" );
        TextView tv = ( TextView ) findViewById( R.id.home_usersname );
        tv.setText( session.getName() );
        
        Button b = ( Button ) findViewById( R.id.home_startnewstation );
        b.setOnClickListener( mNewStationListener );

        NavBar n = ( NavBar ) findViewById( R.id.NavBar );
        n.setListener(this);

		mTabBar = (TabBar) findViewById(R.id.TabBar);
		mViewFlipper = (ViewFlipper) findViewById(R.id.ViewFlipper);
		mTabBar.setViewFlipper(mViewFlipper);
		mTabBar.setListener(this);
		mTabBar.addTab("Radio", TAB_RADIO);
		mTabBar.addTab("Profile", TAB_PROFILE);
		mTabBar.setActive("Radio");
       
        mProfileImage = ( RemoteImageView ) findViewById( R.id.home_profileimage );

        mProfileImageWorker = new Worker( "profile image worker" );
        mProfileImageHandler = new RemoteImageHandler( mProfileImageWorker
                .getLooper(), mHandler );

        SetupProfile( session );
        mMyRecentAdapter = new LastFMStreamAdapter( this );

        SetupMyStations( session );
        SetupRecentStations();
        RebuildMainMenu();
        
        mProfileList = (ListView)findViewById(R.id.profile_list_view);
        String[] mStrings = new String[]{"Top Artists", "Top Albums", "Top Tracks", "Recently Played", "Events", "Friends"};
        mProfileList.setAdapter(new ArrayAdapter<String>(this, 
                R.layout.disclosure_row, R.id.label, mStrings)); 
        
		getListView().setOnItemSelectedListener(new OnItemSelectedListener() {

			public void onItemSelected(AdapterView<?> adapter, View view,
					int position, long id) {
				if(previousSelectedView != null) {
					if(previousSelectedView.getTag() == "bottom")
						previousSelectedView.setBackgroundResource(R.drawable.list_item_rest_rounded_bottom);
					else
						previousSelectedView.setBackgroundResource(R.drawable.list_item_rest);
					((ImageView)previousSelectedView.findViewById(R.id.icon)).setImageResource(R.drawable.list_radio_icon_rest);
					((TextView)previousSelectedView.findViewById(R.id.label)).setTextColor(0xFF000000);
				}
				if(position > 0 && getListView().isFocused()) {
					if(view.getTag() == "bottom")
						view.setBackgroundResource(R.drawable.list_item_focus_rounded_bottom);
					else
						view.setBackgroundResource(R.drawable.list_item_focus);
					((ImageView)view.findViewById(R.id.icon)).setImageResource(R.drawable.list_radio_icon_focus);
					((TextView)view.findViewById(R.id.label)).setTextColor(0xFFFFFFFF);
					previousSelectedView = view;
				}
			}

			public void onNothingSelected(AdapterView<?> arg0) {
				if(previousSelectedView != null) {
					if(previousSelectedView.getTag() == "bottom")
						previousSelectedView.setBackgroundResource(R.drawable.list_item_rest_rounded_bottom);
					else
						previousSelectedView.setBackgroundResource(R.drawable.list_item_rest);
					((ImageView)previousSelectedView.findViewById(R.id.icon)).setImageResource(R.drawable.list_radio_icon_rest);
					((TextView)previousSelectedView.findViewById(R.id.label)).setTextColor(0xFF000000);
				}
				previousSelectedView = null;
			}
	    });
		getListView().setOnFocusChangeListener(new View.OnFocusChangeListener() {

			public void onFocusChange(View v, boolean hasFocus) {
				if(v == getListView()) {
					if(hasFocus)
						getListView().getOnItemSelectedListener().onItemSelected(getListView(), getListView().getSelectedView(), getListView().getSelectedItemPosition(), getListView().getSelectedItemId());
					else
						getListView().getOnItemSelectedListener().onNothingSelected(null);
				}
			}
			
		});
    }
    
	public void tabChanged(String text, int index) {
		Log.i("Lukasz", "Changed tab to "+text+", index="+index);
	}
	
	public void backClicked(View child) {
		logout();
	}

	public void middleClicked(View child, int index) {
		
	}

	public void forwardClicked(View child) {
		
	}

	
    @Override
    public void onStart()
    {
        IntentFilter f = new IntentFilter();
        f.addAction( RadioPlayerService.STATION_CHANGED );
        registerReceiver( mStatusListener, f );
        super.onStart();
    }

    @Override
    public void onDestroy() {
    	unregisterReceiver( mStatusListener );
    	super.onDestroy();
    }
    
    private void RebuildMainMenu() 
    {
        mMainAdapter = new SeparatedListAdapter(this);
        mMainAdapter.addSection( getString(R.string.home_mystations), mMyStationsAdapter );
        mMainAdapter.addSection( getString(R.string.home_recentstations), mMyRecentAdapter );
        setListAdapter( mMainAdapter );
        mMainAdapter.notifyDataSetChanged();
    }
    
    private BroadcastReceiver mStatusListener = new BroadcastReceiver()
    {

        @Override
        public void onReceive( Context context, Intent intent )
        {

            String action = intent.getAction();
            if ( action.equals( RadioPlayerService.STATION_CHANGED ) )
            {
            	SetupRecentStations();
            	RebuildMainMenu();
            }
        }
    };
    
    
    private void SetupProfile( final Session session )
    {

        final Handler uiThreadCallback = new Handler();
        final Runnable runInUIThread = new Runnable()
        {

            public void run()
            {

                FinishSetupProfile();
            }
        };

        new Thread()
        {

            @Override
            public void run()
            {
                try {
					mUser = mServer.getUserInfo( session.getKey() );
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                uiThreadCallback.post( runInUIThread );
            }
        }.start();
    }

    private void FinishSetupProfile()
    {
        if( mUser == null)
            return; //TODO HANDLE
    	ImageUrl[] images = mUser.getImages();
        if ( images.length > 0 )
        {
            mProfileImageHandler
                    .removeMessages( RemoteImageHandler.GET_REMOTE_IMAGE );
            mProfileImageHandler
                    .obtainMessage( RemoteImageHandler.GET_REMOTE_IMAGE,
                            images[0].getUrl() ).sendToTarget();
        }
    }

    private void SetupRecentStations()
    {
        mMyRecentAdapter.resetList();
        SQLiteDatabase db = null;
        try
        {
            db = this.openOrCreateDatabase( LastFm.DB_NAME, MODE_PRIVATE, null );
            Cursor c = db.rawQuery( "SELECT * FROM "
                    + LastFm.DB_TABLE_RECENTSTATIONS + " ORDER BY Timestamp DESC LIMIT 4", null );
            int urlColumn = c.getColumnIndex( "Url" );
            int nameColumn = c.getColumnIndex( "Name" );
            if ( c.getCount() > 0 )
            {
                c.moveToFirst();
                int i = 0;
                // Loop through all Results
                do
                {
                    i++;
                    String name = c.getString( nameColumn );
                    String url = c.getString( urlColumn );
                    mMyRecentAdapter.putStation( name, url );
                }
                while ( c.moveToNext() );
            }
            c.close();
            db.close();
            mMyRecentAdapter.updateModel();
        }
        catch ( Exception e )
        {
            System.out.println( e.getMessage() );
        }

    }

    private void SetupMyStations( final Session session )
    {
        mMyStationsAdapter = new LastFMStreamAdapter( this );
        mMyStationsAdapter.putStation( getString(R.string.home_mylibrary), 
        		"lastfm://user/" + Uri.encode( session.getName() ) + "/personal" );
        mMyStationsAdapter.putStation( getString(R.string.home_myloved), 
        		"lastfm://user/" + Uri.encode( session.getName() ) + "/loved" );
        mMyStationsAdapter.putStation( getString(R.string.home_myrecs), 
        		"lastfm://user/" + Uri.encode( session.getName() ) + "/recommended" );
        mMyStationsAdapter.putStation( getString(R.string.home_myneighborhood), 
        		"lastfm://user/" + Uri.encode( session.getName() ) + "/neighbours" );
        mMyStationsAdapter.updateModel();
    }

    public void onListItemClick( ListView l, View v, int position, long id )
    {
    	l.setEnabled(false);
    	l.getOnItemSelectedListener().onItemSelected(l, v, position, id);
    	ViewSwitcher switcher = (ViewSwitcher)v.findViewById(R.id.row_view_switcher);
    	switcher.showNext();
    	LastFMApplication.getInstance().playRadioStation(this, mMainAdapter.getStation(position));
    }

    private OnClickListener mNewStationListener = new OnClickListener()
    {

        public void onClick( View v )
        {

            Intent intent = new Intent( Home.this, NewStation.class );
            startActivity( intent );
        }
    };

    private final Handler mHandler = new Handler()
    {

        public void handleMessage( Message msg )
        {

            switch ( msg.what )
            {
            case RemoteImageHandler.REMOTE_IMAGE_DECODED:
                mProfileImage.setArtwork( ( Bitmap ) msg.obj );
                mProfileImage.invalidate();
                break;

            default:
                break;
            }
        }
    };
    
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        // Parameters for menu.add are:
        // group -- Not used here.
        // id -- Used only when you want to handle and identify the click yourself.
        // title
        MenuItem profile = menu.add(Menu.NONE, 1, Menu.NONE, "Profile");
        profile.setIcon(R.drawable.profile_unknown);
        MenuItem logout = menu.add(Menu.NONE, 0, Menu.NONE, "Logout");
        logout.setIcon(R.drawable.logout);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
        case 0:
            logout();
            return true;
        case 1:
            // TODO Show profile
            return true;
        }
        return false;
    }
    
    private void logout()
    {
        SharedPreferences settings = getSharedPreferences( LastFm.PREFS, 0 );
        SharedPreferences.Editor editor = settings.edit();
        editor.remove( "lastfm_user" );
        editor.remove( "lastfm_pass" );
        editor.commit();
        SQLiteDatabase db = null;
        try
        {
            db = this.openOrCreateDatabase( LastFm.DB_NAME, MODE_PRIVATE, null );
            db.execSQL( "DROP TABLE IF EXISTS "
                            + LastFm.DB_TABLE_RECENTSTATIONS );
            db.close();

            if(LastFMApplication.getInstance().player != null)
        		LastFMApplication.getInstance().player.stop();
        }
        catch ( Exception e )
        {
            System.out.println( e.getMessage() );
        }
        Intent intent = new Intent( Home.this, LastFm.class );
        startActivity( intent );
        finish();
    }


}
