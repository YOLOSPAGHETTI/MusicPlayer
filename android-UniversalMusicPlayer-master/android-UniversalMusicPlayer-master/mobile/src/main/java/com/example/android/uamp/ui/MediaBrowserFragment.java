/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.uamp.ui;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.uamp.R;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.MediaIDHelper;
import com.example.android.uamp.utils.NetworkHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A Fragment that lists all the various browsable queues available
 * from a {@link android.service.media.MediaBrowserService}.
 * <p/>
 * It uses a {@link MediaBrowserCompat} to connect to the {@link com.example.android.uamp.MusicService}.
 * Once connected, the fragment subscribes to get all the children.
 * All {@link MediaBrowserCompat.MediaItem}'s that can be browsed are shown in a ListView.
 */
public class MediaBrowserFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(MediaBrowserFragment.class);

    private static final String ARG_MEDIA_ID = "media_id";

    private BrowseAdapter mBrowserAdapter;
    private String mMediaId;
    private MediaFragmentListener mMediaFragmentListener;
    private View mErrorView;
    private TextView mErrorMessage;
    private final BroadcastReceiver mConnectivityChangeReceiver = new BroadcastReceiver() {
        private boolean oldOnline = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            // We don't care about network changes while this fragment is not associated
            // with a media ID (for example, while it is being initialized)
            if (mMediaId != null) {
                boolean isOnline = NetworkHelper.isOnline(context);
                if (isOnline != oldOnline) {
                    oldOnline = isOnline;
                    checkForUserVisibleErrors(false);
                    if (isOnline) {
                        mBrowserAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaControllerCompat.Callback mMediaControllerCallback =
            new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null) {
                return;
            }
            LogHelper.d(TAG, "Received metadata change to media ",
                    metadata.getDescription().getMediaId());
            mBrowserAdapter.notifyDataSetChanged();
        }

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            LogHelper.d(TAG, "Received state change: ", state);
            checkForUserVisibleErrors(false);
            mBrowserAdapter.notifyDataSetChanged();
        }
    };

    private final MediaBrowserCompat.SubscriptionCallback mSubscriptionCallback =
        new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId,
                                         @NonNull List<MediaBrowserCompat.MediaItem> children) {
                try {
                    LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                        "  count=" + children.size());
                    checkForUserVisibleErrors(children.isEmpty());
                    mBrowserAdapter.clear();
                    for (MediaBrowserCompat.MediaItem item : children) {
                        mBrowserAdapter.add(item);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                } catch (Throwable t) {
                    LogHelper.e(TAG, "Error on childrenloaded", t);
                }
            }

            @Override
            public void onError(@NonNull String id) {
                LogHelper.e(TAG, "browse fragment subscription onError, id=" + id);
                Toast.makeText(getActivity(), R.string.error_loading_media, Toast.LENGTH_LONG).show();
                checkForUserVisibleErrors(true);
            }
        };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaFragmentListener = (MediaFragmentListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogHelper.d(TAG, "fragment.onCreateView");
        View rootView = inflater.inflate(R.layout.fragment_list, container, false);

        mErrorView = rootView.findViewById(R.id.playback_error);
        mErrorMessage = (TextView) mErrorView.findViewById(R.id.error_message);

        mBrowserAdapter = new BrowseAdapter(getActivity());

        ListView listView = (ListView) rootView.findViewById(R.id.list_view);
        listView.setAdapter(mBrowserAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                checkForUserVisibleErrors(false);
                MediaBrowserCompat.MediaItem item = mBrowserAdapter.getItem(position);
                mMediaFragmentListener.onMediaItemSelected(item);
            }
        });
		
        if(getMediaId() == null) {
            setAlphabetVisibility(rootView, View.INVISIBLE);
        }
        else {
            setAlphabetVisibility(rootView, View.VISIBLE);
        }
		
        setAlphabetButtonListeners(rootView);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        // fetch browsing information to fill the listview:
        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();

        LogHelper.d(TAG, "fragment.onStart, mediaId=", mMediaId,
                "  onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected();
        }

        // Registers BroadcastReceiver to track network connection changes.
        this.getActivity().registerReceiver(mConnectivityChangeReceiver,
            new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();
        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        if (mediaBrowser != null && mediaBrowser.isConnected() && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId);
        }
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            controller.unregisterCallback(mMediaControllerCallback);
        }
        this.getActivity().unregisterReceiver(mConnectivityChangeReceiver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMediaFragmentListener = null;
    }

    public String getMediaId() {
        Bundle args = getArguments();
        if (args != null) {
            return args.getString(ARG_MEDIA_ID);
        }
        return null;
    }

    public void setMediaId(String mediaId) {
        Bundle args = new Bundle(1);
        args.putString(MediaBrowserFragment.ARG_MEDIA_ID, mediaId);
        setArguments(args);
    }

    // Called when the MediaBrowser is connected. This method is either called by the
    // fragment.onStart() or explicitly by the activity in the case where the connection
    // completes after the onStart()
    public void onConnected() {
        if (isDetached()) {
            return;
        }
        mMediaId = getMediaId();
        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener.getMediaBrowser().getRoot();
        }
        updateTitle();

        // Unsubscribing before subscribing is required if this mediaId already has a subscriber
        // on this MediaBrowser instance. Subscribing to an already subscribed mediaId will replace
        // the callback, but won't trigger the initial callback.onChildrenLoaded.
        //
        // This is temporary: A bug is being fixed that will make subscribe
        // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
        // subscriber or not. Currently this only happens if the mediaID has no previous
        // subscriber or if the media content changes on the service side, so we need to
        // unsubscribe first.
        mMediaFragmentListener.getMediaBrowser().unsubscribe(mMediaId);

        mMediaFragmentListener.getMediaBrowser().subscribe(mMediaId, mSubscriptionCallback);

        // Add MediaController callback so we can redraw the list when metadata changes:
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            controller.registerCallback(mMediaControllerCallback);
        }
    }

    private void checkForUserVisibleErrors(boolean forceError) {
        boolean showError = forceError;
        // If offline, message is about the lack of connectivity:
        if (!NetworkHelper.isOnline(getActivity())) {
            mErrorMessage.setText(R.string.error_no_connection);
            showError = true;
        } else {
            // otherwise, if state is ERROR and metadata!=null, use playback state error message:
            MediaControllerCompat controller = ((FragmentActivity) getActivity())
                    .getSupportMediaController();
            if (controller != null
                && controller.getMetadata() != null
                && controller.getPlaybackState() != null
                && controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_ERROR
                && controller.getPlaybackState().getErrorMessage() != null) {
                mErrorMessage.setText(controller.getPlaybackState().getErrorMessage());
                showError = true;
            } else if (forceError) {
                // Finally, if the caller requested to show error, show a generic message:
                mErrorMessage.setText(R.string.error_loading_media);
                showError = true;
            }
        }
        mErrorView.setVisibility(showError ? View.VISIBLE : View.GONE);
        LogHelper.d(TAG, "checkForUserVisibleErrors. forceError=", forceError,
            " showError=", showError,
            " isOnline=", NetworkHelper.isOnline(getActivity()));
    }

    private void updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT.equals(mMediaId)) {
            mMediaFragmentListener.setToolbarTitle(null);
            return;
        }

        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        mediaBrowser.getItem(mMediaId, new MediaBrowserCompat.ItemCallback() {
            @Override
            public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
                mMediaFragmentListener.setToolbarTitle(
                        item.getDescription().getTitle());
            }
        });
    }

    // An adapter for showing the list of browsed MediaItem's
    private static class BrowseAdapter extends ArrayAdapter<MediaBrowserCompat.MediaItem> {

        public BrowseAdapter(Activity context) {
            super(context, R.layout.media_list_item, new ArrayList<MediaBrowserCompat.MediaItem>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaBrowserCompat.MediaItem item = getItem(position);
            return MediaItemViewHolder.setupListView((Activity) getContext(), convertView, parent,
                    item);
        }
    }

    public interface MediaFragmentListener extends MediaBrowserProvider {
        void onMediaItemSelected(MediaBrowserCompat.MediaItem item);
        void setToolbarTitle(CharSequence title);
    }

    private void setAlphabetVisibility(View view, int visibility) {
        final ListView listView = (ListView) view.findViewById(R.id.list_view);

        final Button buttonNum = (Button)view.findViewById(R.id.letterNum);
        if(buttonNum.getVisibility() != visibility) {
            buttonNum.setVisibility(visibility);

            final Button buttonA = (Button) view.findViewById(R.id.letterA);
            buttonA.setVisibility(visibility);

            final Button buttonB = (Button) view.findViewById(R.id.letterB);
            buttonB.setVisibility(visibility);

            final Button buttonC = (Button) view.findViewById(R.id.letterC);
            buttonC.setVisibility(visibility);

            final Button buttonD = (Button) view.findViewById(R.id.letterD);
            buttonD.setVisibility(visibility);

            final Button buttonE = (Button) view.findViewById(R.id.letterE);
            buttonE.setVisibility(visibility);

            final Button buttonF = (Button) view.findViewById(R.id.letterF);
            buttonF.setVisibility(visibility);

            final Button buttonG = (Button) view.findViewById(R.id.letterG);
            buttonG.setVisibility(visibility);

            final Button buttonH = (Button) view.findViewById(R.id.letterH);
            buttonH.setVisibility(visibility);

            final Button buttonI = (Button) view.findViewById(R.id.letterI);
            buttonI.setVisibility(visibility);

            final Button buttonJ = (Button) view.findViewById(R.id.letterJ);
            buttonJ.setVisibility(visibility);

            final Button buttonK = (Button) view.findViewById(R.id.letterK);
            buttonK.setVisibility(visibility);

            final Button buttonL = (Button) view.findViewById(R.id.letterL);
            buttonL.setVisibility(visibility);

            final Button buttonM = (Button) view.findViewById(R.id.letterM);
            buttonM.setVisibility(visibility);

            final Button buttonN = (Button) view.findViewById(R.id.letterN);
            buttonN.setVisibility(visibility);

            final Button buttonO = (Button) view.findViewById(R.id.letterO);
            buttonO.setVisibility(visibility);

            final Button buttonP = (Button) view.findViewById(R.id.letterP);
            buttonP.setVisibility(visibility);

            final Button buttonQ = (Button) view.findViewById(R.id.letterQ);
            buttonQ.setVisibility(visibility);

            final Button buttonR = (Button) view.findViewById(R.id.letterR);
            buttonR.setVisibility(visibility);

            final Button buttonS = (Button) view.findViewById(R.id.letterS);
            buttonS.setVisibility(visibility);

            final Button buttonT = (Button) view.findViewById(R.id.letterT);
            buttonT.setVisibility(visibility);

            final Button buttonU = (Button) view.findViewById(R.id.letterU);
            buttonU.setVisibility(visibility);

            final Button buttonV = (Button) view.findViewById(R.id.letterV);
            buttonV.setVisibility(visibility);

            final Button buttonW = (Button) view.findViewById(R.id.letterW);
            buttonW.setVisibility(visibility);

            final Button buttonX = (Button) view.findViewById(R.id.letterX);
            buttonX.setVisibility(visibility);

            final Button buttonY = (Button) view.findViewById(R.id.letterY);
            buttonY.setVisibility(visibility);

            final Button buttonZ = (Button) view.findViewById(R.id.letterZ);
            buttonZ.setVisibility(visibility);
        }
    }

    private void setAlphabetButtonListeners(View view) {
        final ListView listView = (ListView) view.findViewById(R.id.list_view);

        final Button buttonNum = (Button)view.findViewById(R.id.letterNum);
		if(buttonNum.getVisibility() == View.VISIBLE) {
            setButtonListener(buttonNum, ' ', listView);

            final Button buttonA = (Button) view.findViewById(R.id.letterA);
            setButtonListener(buttonA, 'A', listView);

            final Button buttonB = (Button) view.findViewById(R.id.letterB);
            setButtonListener(buttonB, 'B', listView);

            final Button buttonC = (Button) view.findViewById(R.id.letterC);
            setButtonListener(buttonC, 'C', listView);

            final Button buttonD = (Button) view.findViewById(R.id.letterD);
            setButtonListener(buttonD, 'D', listView);

            final Button buttonE = (Button) view.findViewById(R.id.letterE);
            setButtonListener(buttonE, 'E', listView);

            final Button buttonF = (Button) view.findViewById(R.id.letterF);
            setButtonListener(buttonF, 'F', listView);

            final Button buttonG = (Button) view.findViewById(R.id.letterG);
            setButtonListener(buttonG, 'G', listView);

            final Button buttonH = (Button) view.findViewById(R.id.letterH);
            setButtonListener(buttonH, 'H', listView);

            final Button buttonI = (Button) view.findViewById(R.id.letterI);
            setButtonListener(buttonI, 'I', listView);

            final Button buttonJ = (Button) view.findViewById(R.id.letterJ);
            setButtonListener(buttonJ, 'J', listView);

            final Button buttonK = (Button) view.findViewById(R.id.letterK);
            setButtonListener(buttonK, 'K', listView);

            final Button buttonL = (Button) view.findViewById(R.id.letterL);
            setButtonListener(buttonL, 'L', listView);

            final Button buttonM = (Button) view.findViewById(R.id.letterM);
            setButtonListener(buttonM, 'M', listView);

            final Button buttonN = (Button) view.findViewById(R.id.letterN);
            setButtonListener(buttonN, 'N', listView);

            final Button buttonO = (Button) view.findViewById(R.id.letterO);
            setButtonListener(buttonO, 'O', listView);

            final Button buttonP = (Button) view.findViewById(R.id.letterP);
            setButtonListener(buttonP, 'P', listView);

            final Button buttonQ = (Button) view.findViewById(R.id.letterQ);
            setButtonListener(buttonQ, 'Q', listView);

            final Button buttonR = (Button) view.findViewById(R.id.letterR);
            setButtonListener(buttonR, 'R', listView);

            final Button buttonS = (Button) view.findViewById(R.id.letterS);
            setButtonListener(buttonS, 'S', listView);

            final Button buttonT = (Button) view.findViewById(R.id.letterT);
            setButtonListener(buttonT, 'T', listView);

            final Button buttonU = (Button) view.findViewById(R.id.letterU);
            setButtonListener(buttonU, 'U', listView);

            final Button buttonV = (Button) view.findViewById(R.id.letterV);
            setButtonListener(buttonV, 'V', listView);

            final Button buttonW = (Button) view.findViewById(R.id.letterW);
            setButtonListener(buttonW, 'W', listView);

            final Button buttonX = (Button) view.findViewById(R.id.letterX);
            setButtonListener(buttonX, 'X', listView);

            final Button buttonY = (Button) view.findViewById(R.id.letterY);
            setButtonListener(buttonY, 'Y', listView);

            final Button buttonZ = (Button) view.findViewById(R.id.letterZ);
            setButtonListener(buttonZ, 'Z', listView);
        }
    }

    private void setButtonListener(final Button button, final char letter, final ListView listView) {
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                listView.setSelection(getFirstItemPositionForLetter(letter));
            }
        });
    }

    private int getFirstItemPositionForLetter(char letter) {
        int position = 0;
        char nextLetter = (char)((int)letter+1);
        for(int i=0; i<mBrowserAdapter.getCount(); i++) {
            MediaBrowserCompat.MediaItem item = mBrowserAdapter.getItem(i);
            String word = item.toString().substring(33);
            if(letter != ' ') {
                if (word.toUpperCase().startsWith(letter + "")) {
                    position = i;
                    break;
                }
                if (letter != 'Z') {
                    if (word.toUpperCase().startsWith(nextLetter + "")) {
                        position = i;
                        break;
                    }
                } else if (i == mBrowserAdapter.getCount() - 1) {
                    position = i;
                }
            }
            else {
                position = 0;
            }
        }
        return position;
    }
}
