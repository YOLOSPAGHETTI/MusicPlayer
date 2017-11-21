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

package com.example.android.uamp.model;

import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.example.android.uamp.R;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.MediaIDHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_YEAR;
import static com.example.android.uamp.utils.MediaIDHelper.createMediaID;

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    private MusicProviderSource mSource;

    private DBBuilder DBB;
    private ArrayList sortOrder;
    private List level1 = new ArrayList<String>();
    private List level2 = new ArrayList<String>();
    private List level3 = new ArrayList<String>();
    private List level4 = new ArrayList<String>();
    private List level5 = new ArrayList<String>();
    private List songList = new ArrayList<String>();
    private List artistList = new ArrayList<String>();
    private List albumList = new ArrayList<String>();
    private List genreList = new ArrayList<String>();
    private List yearList = new ArrayList<Integer>();
    private List decadeList = new ArrayList<Integer>();
    private List songIDList = new ArrayList<String>();
    private List artistIDList = new ArrayList<String>();
    private List albumIDList = new ArrayList<String>();
    private List genreIDList = new ArrayList<String>();

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    private volatile State mCurrentState = State.NON_INITIALIZED;

    public interface Callback {
        void onMusicCatalogReady(boolean success);
    }

    public MusicProvider(DBBuilder DBB, ArrayList sortOrder) {
        this(new MusicFileSource(DBB));
        this.DBB = DBB;
        this.sortOrder = sortOrder;
    }

    public MusicProvider(MusicProviderSource source) {
        mSource = source;
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    /*public Iterable<MediaMetadataCompat> getShuffledMusic() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(mMusicListById.size());
        for (MutableMediaMetadata mutableMetadata : mMusicListById.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        Collections.shuffle(shuffled);
        return shuffled;
    }*/

    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    public void retrieveMediaAsync(final Callback callback) {
        LogHelper.d(TAG, "retrieveMediaAsync called");
        if (mCurrentState == State.INITIALIZED) {
            if (callback != null) {
                // Nothing to do, execute callback immediately
                callback.onMusicCatalogReady(true);
            }
            return;
        }

        // Asynchronously load the music catalog in a separate thread
        new AsyncTask<Void, Void, State>() {
            @Override
            protected State doInBackground(Void... params) {
                retrieveMedia();
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (callback != null) {
                    callback.onMusicCatalogReady(current == State.INITIALIZED);
                }
            }
        }.execute();
    }

    private synchronized void retrieveMedia() {
        try {
            if (mCurrentState == State.NON_INITIALIZED) {
                mCurrentState = State.INITIALIZING;

                Iterator<MediaMetadataCompat> tracks = mSource.iterator();

                getSongsFromDB();
                getArtistsFromDB();
                getAlbumsFromDB();
                getGenresFromDB();
                yearList = getYearsFromDB("%");
                decadeList = getDecadesFromDB("%");
                if(sortOrder != null) {
                    createSortLists();
                }

                mCurrentState = State.INITIALIZED;
            }
        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                mCurrentState = State.NON_INITIALIZED;
            }
        }
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForRoot(Resources resources, String id, String title, String subtitle, Uri icon) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconUri(icon)
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForData(String data,
                                                                         Resources resources, String media_id) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, media_id, data))
                .setTitle(data)
                .setSubtitle(resources.getString(
                        R.string.browse_musics_by_genre_subtitle, data))
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaMetadataCompat metadata, String key, String id) {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        String label;
        if (id.equals(MEDIA_ID_MUSICS_BY_YEAR)) {
            label = "" + metadata.getLong(key);
        } else {
            label = metadata.getString(key);
        }
        String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                metadata.getDescription().getMediaId(), id, label);
        MediaMetadataCompat copy = new MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build();
        return new MediaBrowserCompat.MediaItem(copy.getDescription(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);

    }

    public long getSourceCompletionPerc() {
        return mSource.getCompletionPerc();
    }

    // Pulls a list of all the songs and their corresponding ID's from the DB
    private void getSongsFromDB() {
        String[] projection = {"ID", "Title"};
        String[] selectionArgs = {"%"};
        List[] data = DBB.normalQuery("Songs", projection, "ID like ?", selectionArgs, null, projection.length);

        try {
            if (data[0] != null) {
                List unsortedIDs = new ArrayList<String>();
                List unsortedSongs = new ArrayList<String>();
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    String title = ignoreNulls(data[1].get(i));

                    songIDList.add(ID);
                    songList.add(title);
                    unsortedIDs.add(ID);
                    unsortedSongs.add(title);
                }
                sortStrings(songList);
                sortIDs(unsortedSongs, unsortedIDs, songIDList);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Pulls a list of all the artists and their corresponding ID's from the DB
    private void getArtistsFromDB() {
        String[] projection = {"ID", "Artist"};
        String[] selectionArgs = {"%"};
        List[] data = DBB.normalQuery("Artists", projection, "ID like ?", selectionArgs, null, projection.length);

        try {
            if (data[0] != null) {
                List unsortedIDs = new ArrayList<String>();
                List unsortedArtists = new ArrayList<String>();
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    String artist = ignoreNulls(data[1].get(i));

                    artistIDList.add(ID);
                    artistList.add(artist);
                    unsortedIDs.add(ID);
                    unsortedArtists.add(artist);
                }
                sortStrings(artistList);
                sortIDs(unsortedArtists, unsortedIDs, artistIDList);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Pulls a list of all the albums and their corresponding ID's from the DB
    private void getAlbumsFromDB() {
        String[] projection = {"ID", "Album"};
        String[] selectionArgs = {"%"};
        List[] data = DBB.normalQuery("Albums", projection, "ID like ?", selectionArgs, null, projection.length);

        try {
            if (data[0] != null) {
                List unsortedIDs = new ArrayList<String>();
                List unsortedAlbums = new ArrayList<String>();
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    String album = ignoreNulls(data[1].get(i));

                    albumIDList.add(ID);
                    albumList.add(album);
                    unsortedIDs.add(ID);
                    unsortedAlbums.add(album);
                }
                sortStrings(albumList);
                sortIDs(unsortedAlbums, unsortedIDs, albumIDList);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Pulls a list of all the genres and their corresponding ID's from the DB
    private void getGenresFromDB() {
        String[] projection = {"ID", "Genre"};
        String[] selectionArgs = {"%"};
        List[] data = DBB.normalQuery("Genres", projection, "ID like ?", selectionArgs, null, projection.length);

        try {
            if (data[0] != null) {
                List unsortedIDs = new ArrayList<String>();
                List unsortedGenres = new ArrayList<String>();
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    String genre = ignoreNulls(data[1].get(i));

                    genreIDList.add(ID);
                    genreList.add(genre);
                    unsortedIDs.add(ID);
                    unsortedGenres.add(genre);
                }
                sortStrings(genreList);
                sortIDs(unsortedGenres, unsortedIDs, genreIDList);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Pulls a list of years from the DB with the passed song ID(s)
    private List getYearsFromDB(String... selectionArray) {
        String selection = "";
        for (String aSelectionArray : selectionArray) {
            selection = selection + "," + aSelectionArray;
        }
        String[] projection = {"Year"};
        String[] selectionArgs = {selection};
        List[] data = DBB.normalQuery("Songs", projection, "ID in (?)", selectionArgs, null, projection.length);
        List list = new ArrayList<>();

        try {
            if (data[0] != null) {
                for (int i = 0; i < data[0].size(); i++) {
                    int year = ignoreInvalidNums(ignoreNulls(data[0].get(i)));

                    list.add(year);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // Pulls a list of decades from the DB with the passed song ID(s)
    private List getDecadesFromDB(String... selectionArray) {
        String selection = "";
        for (String aSelectionArray : selectionArray) {
            selection = selection + "," + aSelectionArray;
        }
        String[] projection = {"Decade"};
        String[] selectionArgs = {selection};
        List[] data = DBB.normalQuery("Songs", projection, "ID in (?)", selectionArgs, null, projection.length);
        List list = new ArrayList<>();

        try {
            if (data[0] != null) {
                for (int i = 0; i < data[0].size(); i++) {
                    int decade = ignoreInvalidNums(ignoreNulls(data[0].get(i)));

                    list.add(decade);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // Creates the media hierarchy based on the sort order
    private void createSortLists() {
        int maxLevel = -1;
        for(int i=0; i<sortOrder.size(); i++) {
            String name = sortOrder.get(i).toString();
            if (i==0) {
                level1 = assignLevel(name);
            }
            else if (i==1) {
                level2 = assignLevel(name);
            }
            else if (i==2) {
                level3 = assignLevel(name);
            }
            else if (i==3) {
                level4 = assignLevel(name);
            }
            else if (i==4) {
                level5 = assignLevel(name);
            }
            maxLevel = i+1;
        }
        if (maxLevel==1) {
            level2 = songList;
        }
        else if (maxLevel==2) {
            level3 = songList;
        }
        else if (maxLevel==3) {
            level4 = songList;
        }
        else if (maxLevel==4) {
            level5 = songList;
        }
    }

    // Returns the corresponding list based on the passed item name
    private List assignLevel(String name) {
        List list = new ArrayList<String>();
        switch (name) {
            case "Artists":
                list = artistList;
                break;
            case "Albums":
                list = albumList;
                break;
            case "Genres":
                list = genreList;
                break;
            case "Years":
                list = yearList;
                break;
            case "Decades":
                list = decadeList;
                break;
        }
        return list;
    }

    // Sorts the lists with strings in alphabetical order (ignoring a starting "The ")
    private void sortStrings(List items) {
        Collections.sort(items, new Comparator<String>()
        {
            @Override
            public int compare(String item1, String item2)
            {
                if(item1.startsWith("The ")) {
                    item1 = item1.replace("The ", "");
                }
                if(item2.startsWith("The ")) {
                    item2 = item2.replace("The ", "");
                }
                return item1.compareToIgnoreCase(item2);
            }
        });
    }

    // Sorts the lists of IDs to line up the indexes of the corresponding items
    private void sortIDs(final List items, final List unsortedIDs, List IDs) {
        Collections.sort(IDs, new Comparator<String>()
        {
            @Override
            public int compare(String ID1, String ID2)
            {
                int index1 = unsortedIDs.indexOf(ID1);
                int index2 = unsortedIDs.indexOf(ID2);
                String item1 = (String)items.get(index1);
                String item2 = (String)items.get(index2);
                if(item1.startsWith("The ")) {
                    item1 = item1.replace("The ", "");
                }
                if(item2.startsWith("The ")) {
                    item2 = item2.replace("The ", "");
                }
                return item1.compareToIgnoreCase(item2);
            }
        });
    }

    // Sorts the lists with numbers in ascending order
    private List sortNums(List items) {
        Collections.sort(items, new Comparator<Long>()
        {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public int compare(Long item1, Long item2)
            {
                return Long.compare(item1, item2);
            }
        });
        return items;
    }

    // Returns -1 for any characters entered in expected number fields, otherwise returns the number
    private int ignoreInvalidNums(String text) {
        int num = -1;
        try {
            if(text != null) {
                if(!text.equals("")) {
                    num = Integer.parseInt(text);
                }
            }
        }
        catch(NumberFormatException e) {
            System.out.println("Invalid number: " + text);
        }

        return num;
    }

    // Returns empty string if null, otherwise returns embedded string
    private String ignoreNulls(Object obj) {
        String text = "";
        if (obj != null) {
            text = obj.toString();
        }
        return text;
    }
}
