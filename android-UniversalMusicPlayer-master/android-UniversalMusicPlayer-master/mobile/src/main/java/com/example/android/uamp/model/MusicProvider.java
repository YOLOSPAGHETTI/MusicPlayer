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
import android.graphics.Bitmap;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SONG;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_YEAR;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DATE;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_CUSTOM;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT;
import static com.example.android.uamp.utils.MediaIDHelper.createMediaID;

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    private MusicProviderSource mSource;

    private DBBuilder DBB;
    private CompletionCalculation cc;
    private ArrayList<String> sortOrder;
    private ArrayList<String> searchStrings;
    private List<MediaMetadataCompat> musicList;
    private List<MediaMetadataCompat> sortedMusicList;
    private List<String> artistList;
    private List<String> albumList;
    private List<String> genreList;
    private List<Integer> yearList;
    private List<String> dateList;
    private List<String> sortedArtistList;
    private List<String> sortedAlbumList;
    private List<String> sortedGenreList;
    private List<Integer> sortedYearList;
    private List<String> sortedDateList;
    private List<String> songIDList;
    private List<String> artistIDList;
    private List<String> albumIDList;
    private List<String> genreIDList;

    private List<MediaMetadataCompat> currentList;

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    private volatile State mCurrentState = State.NON_INITIALIZED;

    public interface Callback {
        void onMusicCatalogReady(boolean success);
    }

    public MusicProvider(DBBuilder DBB, CompletionCalculation cc) {
        this(new MusicFileSource(DBB, cc));
        this.DBB = DBB;
        this.cc = cc;
    }

    public MusicProvider(MusicProviderSource source) {
        mSource = source;
        musicList = new ArrayList<>();
        sortedMusicList = new ArrayList<>();
        artistList = new ArrayList<>();
        albumList = new ArrayList<>();
        genreList = new ArrayList<>();
        yearList = new ArrayList<>();
        dateList = new ArrayList<>();
        sortedArtistList = new ArrayList<>();
        sortedAlbumList = new ArrayList<>();
        sortedGenreList = new ArrayList<>();
        sortedYearList = new ArrayList<>();
        sortedDateList = new ArrayList<>();
        songIDList = new ArrayList<>();
        artistIDList = new ArrayList<>();
        albumIDList = new ArrayList<>();
        genreIDList = new ArrayList<>();
        currentList = new ArrayList<>();
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    public Iterable<MediaMetadataCompat> getShuffledMusic() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(musicList.size());
        for (MediaMetadataCompat mutableMetadata : musicList) {
            shuffled.add(mutableMetadata);
        }
        Collections.shuffle(shuffled);
        return shuffled;
    }

    public List<MediaMetadataCompat> searchMusic(String metadataField, String query) {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        ArrayList<MediaMetadataCompat> result = new ArrayList<>();
        query = query.toLowerCase(Locale.US);
        for (MediaMetadataCompat track : musicList) {
            if (track.getString(metadataField).toLowerCase(Locale.US)
                    .contains(query)) {
                result.add(track);
            }
        }
        return result;
    }


    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    public MediaMetadataCompat getMusic(String musicId) {
        return songIDList.contains(musicId) ? musicList.get(songIDList.indexOf(musicId)) : null;
    }

    public synchronized void updateMusicArt(String musicId, Bitmap albumArt, Bitmap icon) {
        MediaMetadataCompat metadata = getMusic(musicId);
        metadata = new MediaMetadataCompat.Builder(metadata)

                // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                // example, on the lockscreen background when the media session is active.
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                // set small version of the album art in the DISPLAY_ICON. This is used on
                // the MediaDescription and thus it should be small to be serialized if
                // necessary
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                .build();

        MediaMetadataCompat newMetadata = musicList.get(songIDList.indexOf(musicId));
        if (newMetadata == null) {
            throw new IllegalStateException("Unexpected error: Inconsistent data structures in " +
                    "MusicProvider");
        }
        newMetadata = metadata;
    }

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
                while (tracks.hasNext()) {
                    MediaMetadataCompat item = tracks.next();
                    //String musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
                    musicList.add(item);
                }
                cc.recalculate(2, 20);
                getSongInfoFromDB();
                cc.recalculate(2, 40);
                getArtistsFromDB();
                cc.recalculate(2, 60);
                getAlbumsFromDB();
                cc.recalculate(2, 80);
                getGenresFromDB();
                cc.recalculate(2, 100);

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

    public List<MediaBrowserCompat.MediaItem> getChildren(String mediaId, Resources resources) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        //System.out.println(mediaId);
        //System.out.println(sortOrder);
        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return mediaItems;
        }

        else if (MEDIA_ID_ROOT.equals(mediaId)) {
            mediaItems.add(createBrowsableMediaItemForRoot(MEDIA_ID_MUSICS_BY_SONG, resources.getString(R.string.browse_songs), resources.getString(R.string.browse_song_subtitle), Uri.parse("android.resource://" +
                    "com.example.android.uamp/drawable/ic_by_genre")));
            mediaItems.add(createBrowsableMediaItemForRoot(MEDIA_ID_MUSICS_BY_ARTIST, resources.getString(R.string.browse_artists), resources.getString(R.string.browse_artist_subtitle), Uri.parse("android.resource://" +
                    "com.example.android.uamp/drawable/ic_by_genre")));
            mediaItems.add(createBrowsableMediaItemForRoot(MEDIA_ID_MUSICS_BY_ALBUM, resources.getString(R.string.browse_albums), resources.getString(R.string.browse_album_subtitle), Uri.parse("android.resource://" +
                    "com.example.android.uamp/drawable/ic_by_genre")));
            mediaItems.add(createBrowsableMediaItemForRoot(MEDIA_ID_MUSICS_BY_GENRE, resources.getString(R.string.browse_genres), resources.getString(R.string.browse_genre_subtitle), Uri.parse("android.resource://" +
                    "com.example.android.uamp/drawable/ic_by_genre")));
            mediaItems.add(createBrowsableMediaItemForRoot(MEDIA_ID_MUSICS_BY_YEAR, resources.getString(R.string.browse_years), resources.getString(R.string.browse_year_subtitle), Uri.parse("android.resource://" +
                    "com.example.android.uamp/drawable/ic_by_genre")));
            mediaItems.add(createBrowsableMediaItemForRoot(MEDIA_ID_MUSICS_BY_DATE, resources.getString(R.string.browse_dates), resources.getString(R.string.browse_date_subtitle), Uri.parse("android.resource://" +
                    "com.example.android.uamp/drawable/ic_by_genre")));
            mediaItems.add(createBrowsableMediaItemForRoot(MEDIA_ID_MUSICS_CUSTOM, resources.getString(R.string.browse_custom), resources.getString(R.string.browse_custom_subtitle), Uri.parse("android.resource://" +
                    "com.example.android.uamp/drawable/ic_by_genre")));
        }
        if (MEDIA_ID_MUSICS_BY_SONG.equals(mediaId)) {
            currentList.clear();
            if(sortedMusicList.size() == 0) {
                sortedMusicList.addAll(musicList);
                sortMusic(sortedMusicList);
            }
            for (Object metadataObj : sortedMusicList) {
                MediaMetadataCompat metadata = ((MediaMetadataCompat) metadataObj);
                mediaItems.add(createMediaItem(metadata, MediaMetadataCompat.METADATA_KEY_TITLE, mediaId));
            }
        }
        else if (MEDIA_ID_MUSICS_BY_ARTIST.equals(mediaId)) {
            createSortedItemList(artistList, sortedArtistList);
            mediaItems = createMediaItems(sortedArtistList, mediaId, mediaItems);
        }
        else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_ARTIST)) {
            String artist = MediaIDHelper.getHierarchy(mediaId)[1];
            String ID = artistIDList.get(artistList.indexOf(artist));
            currentList.clear();
            for (Object metadataObj : getMusicFromItem("SongArtists", "SongID", "ArtistID = ?", ID)) {
                MediaMetadataCompat metadata = ((MediaMetadataCompat) metadataObj);
                mediaItems.add(createMediaItem(metadata, MediaMetadataCompat.METADATA_KEY_ARTIST, MEDIA_ID_MUSICS_BY_ARTIST));
            }
        }
        else if (MEDIA_ID_MUSICS_BY_ALBUM.equals(mediaId)) {
            createSortedItemList(albumList, sortedAlbumList);
            mediaItems = createMediaItems(sortedAlbumList, mediaId, mediaItems);
        }
        else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_ALBUM)) {
            String album = MediaIDHelper.getHierarchy(mediaId)[1];
            String ID = albumIDList.get(albumList.indexOf(album));
            //String albumArtist = DBB.easyShortQuery("Albums", "AlbumArtist", "ID = ?", ID, null);
            currentList.clear();
            for (Object metadataObj : getMusicFromItem("Songs", "ID", "Album = ?", album)) {
                MediaMetadataCompat metadata = ((MediaMetadataCompat) metadataObj);
                mediaItems.add(createMediaItem(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM, MEDIA_ID_MUSICS_BY_ALBUM));
            }
        }
        else if (MEDIA_ID_MUSICS_BY_GENRE.equals(mediaId)) {
            createSortedItemList(genreList, sortedGenreList);
            mediaItems = createMediaItems(sortedGenreList, mediaId, mediaItems);
        }
        else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_GENRE)) {
            String genre = MediaIDHelper.getHierarchy(mediaId)[1];
            String ID = genreIDList.get(genreList.indexOf(genre));
            currentList.clear();
            for (Object metadataObj : getMusicFromItem("SongGenres", "SongID", "GenreID = ?", ID)) {
                MediaMetadataCompat metadata = ((MediaMetadataCompat) metadataObj);
                mediaItems.add(createMediaItem(metadata, MediaMetadataCompat.METADATA_KEY_GENRE, MEDIA_ID_MUSICS_BY_GENRE));
            }
        }
        else if (MEDIA_ID_MUSICS_BY_YEAR.equals(mediaId)) {
            createSortedItemList(yearList, sortedYearList);
            mediaItems = createMediaItems(sortedYearList, mediaId, mediaItems);
        }
        else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_YEAR)) {
            String year = MediaIDHelper.getHierarchy(mediaId)[1];
            currentList.clear();
            for (Object metadataObj : getMusicFromItem("Songs", "ID", "Year = ?" , year)) {
                MediaMetadataCompat metadata = ((MediaMetadataCompat) metadataObj);
                mediaItems.add(createMediaItem(metadata, MediaMetadataCompat.METADATA_KEY_YEAR, MEDIA_ID_MUSICS_BY_YEAR));
            }
        }
        else if (MEDIA_ID_MUSICS_BY_DATE.equals(mediaId)) {
            createSortedItemList(dateList, sortedDateList);
            mediaItems = createMediaItems(sortedDateList, mediaId, mediaItems);
        }
        else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_DATE)) {
            String dateStr = MediaIDHelper.getHierarchy(mediaId)[1];
            currentList.clear();
            for (Object metadataObj : getMusicFromItem("Songs", "ID", "ModifiedDate = ?" , dateStr)) {
                MediaMetadataCompat metadata = ((MediaMetadataCompat) metadataObj);
                mediaItems.add(createMediaItem(metadata, MusicProviderSource.CUSTOM_METADATA_TRACK_DATE_ADDED, MEDIA_ID_MUSICS_BY_DATE));
            }
        }
        else if (mediaId.equals(MEDIA_ID_MUSICS_CUSTOM)) {
            if(sortOrder != null) {
                String newMediaId = extractMediaIdFromSortOrder(0);
                List list = extractListFromSortOrder(0);
                String searchString = searchStrings.get(0);
                mediaItems = createCustomMediaItems(list, mediaId, newMediaId, mediaItems, searchString);
            }
        }
        else if (mediaId.startsWith(MEDIA_ID_MUSICS_CUSTOM)) {
            if(sortOrder != null) {
                int lastIdVal = MediaIDHelper.getHierarchy(mediaId).length;
                int index = (lastIdVal - 1)/2;

                if(index<sortOrder.size()) {
                    String newMediaId = extractMediaIdFromSortOrder(index);
                    List list = getListFromItem(newMediaId, mediaId);
                    String searchString = searchStrings.get(index);
                    if(searchString==null) {
                        searchString = "";
                    }
                    for (Object obj : list) {
                        String item = obj.toString();
                        if (item.toLowerCase().contains(searchString.toLowerCase())) {
                            mediaItems.add(createBrowsableMediaItemForCustomData(item, newMediaId, mediaId));
                        }
                    }
                }
                else {
                    List list = getListFromItem(MEDIA_ID_MUSICS_BY_SONG, mediaId);
                    currentList.clear();
                    for (Object obj : list) {
                        MediaMetadataCompat metadata = ((MediaMetadataCompat) obj);
                        mediaItems.add(createMediaItem(metadata, MediaMetadataCompat.METADATA_KEY_TITLE, MEDIA_ID_MUSICS_BY_SONG));
                    }
                }
            }
        }
        else {
            LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId);
        }
        return mediaItems;
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForRoot(String id, String title, String subtitle, Uri icon) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconUri(icon)
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForData(String data, String media_id) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, media_id, data))
                .setTitle(data)
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForCustomData(String data, String new_media_id, String media_id) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, media_id, new_media_id, data))
                .setTitle(data)
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaMetadataCompat metadata, String key, String id) {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        currentList.add(metadata);
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

    // Pulls a list of all the songs and their corresponding ID's from the DB
    private void getSongInfoFromDB() {
        String[] projection = {"ID", "Title", "Year", "ModifiedDate"};
        String[] selectionArgs = {"%"};
        List[] data = DBB.normalQuery("Songs", projection, "ID like ?", selectionArgs, null, projection.length);

        try {
            if (data[0] != null) {
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    int year = ignoreInvalidNums(ignoreNulls(data[2].get(i)));
                    String dateStr = ignoreNulls(data[3].get(i));

                    songIDList.add(ID);

                    if (!yearList.contains(year)) {
                        yearList.add(year);
                    }
                    if (!dateList.contains(dateStr)) {
                        dateList.add(dateStr);
                    }
                }
            }
        } catch (Exception e) {
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
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    String artist = ignoreNulls(data[1].get(i));

                    artistIDList.add(ID);
                    artistList.add(artist);
                }
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
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    String album = ignoreNulls(data[1].get(i));

                    albumIDList.add(ID);
                    albumList.add(album);
                }
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
                for (int i = 0; i < data[0].size(); i++) {
                    String ID = ignoreNulls(data[0].get(i));
                    String genre = ignoreNulls(data[1].get(i));

                    genreIDList.add(ID);
                    genreList.add(genre);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createSortedItemList(List list, List sortedList) {
        if(sortedList.size() == 0) {
            sortedList.addAll(list);
            if(list == yearList) {
                sortNums(sortedList);
            }
            else if(list == dateList) {
                sortDates(sortedList);
            }
            else {
                sortStrings(sortedList);
            }
        }
    }

    private List<MediaBrowserCompat.MediaItem> createMediaItems(List sortedList, String mediaId, List<MediaBrowserCompat.MediaItem> mediaItems) {
        for (Object itemObj : sortedList) {
            String item = itemObj.toString();
            mediaItems.add(createBrowsableMediaItemForData(item, mediaId));
        }

        return mediaItems;
    }

    private List<MediaBrowserCompat.MediaItem> createCustomMediaItems(List sortedList, String mediaId, String newMediaId,
                                                                          List<MediaBrowserCompat.MediaItem> mediaItems, String searchString) {
        for (Object obj : sortedList) {
            String item = obj.toString();
            if(item.toLowerCase().contains(searchString.toLowerCase())) {
                mediaItems.add(createBrowsableMediaItemForCustomData(item, newMediaId, mediaId));
            }
        }

        return mediaItems;
    }

    private List getListFromItem(String newMediaId, String mediaId) {
        List list = new ArrayList<>();
        int lastIdVal = MediaIDHelper.getHierarchy(mediaId).length;
        String[] selection = new String[6];
        for(int i=0; i<6; i++) {
            boolean hasVal = false;
            for(int j=2; j<lastIdVal; j++) {
                if(j%2==0) {
                    String prevMediaId = MediaIDHelper.getHierarchy(mediaId)[j-1];
                    String currentMediaId = MediaIDHelper.getHierarchy(mediaId)[j];
                    int slot = getSelectionSlot(prevMediaId);
                    if(slot == i) {
                        selection[i] = currentMediaId;
                        hasVal = true;
                        break;
                    }
                }
            }
            if(!hasVal) {
                selection[i] = "%";
            }
        }
        String query = buildQuery(newMediaId);
        List<String>[] data;
        switch(newMediaId) {
            case(MEDIA_ID_MUSICS_BY_SONG):
                data = DBB.customQuery(query, selection, 2);
                boolean exists = false;
                for (int i = 0; i < data[0].size(); i++) {
                    for (int j = 0; j < list.size(); j++)
                    {
                        MediaMetadataCompat metadata = (MediaMetadataCompat)list.get(j);
                        if(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).equals(ignoreNulls(data[0].get(i)))) {
                            exists = true;
                            break;
                        }
                    }
                    if(!exists) {
                        list.add(musicList.get(songIDList.indexOf(ignoreNulls(data[0].get(i)))));
                    }
                    exists = false;
                }
                if(mediaId.contains(MEDIA_ID_MUSICS_BY_ALBUM)) {
                    sortMusicByTrackNum(list);
                }
                else {
                    sortMusic(list);
                }
                break;
            case(MEDIA_ID_MUSICS_BY_ARTIST):
                data = DBB.customQuery(query, selection, 2);
                for (int i = 0; i < data[1].size(); i++) {
                    if(!list.contains(ignoreNulls(data[1].get(i)))) {
                        list.add(ignoreNulls(data[1].get(i)));
                    }
                }
                sortStrings(list);
                break;

            case(MEDIA_ID_MUSICS_BY_ALBUM):
                data = DBB.customQuery(query, selection, 1);
                for (int i = 0; i < data[0].size(); i++) {
                    if(!list.contains(ignoreNulls(data[0].get(i)))) {
                        list.add(ignoreNulls(data[0].get(i)));
                    }
                }
                sortStrings(list);
                break;

            case(MEDIA_ID_MUSICS_BY_GENRE):
                data = DBB.customQuery(query, selection, 2);
                for (int i = 0; i < data[1].size(); i++) {
                    if(!list.contains(ignoreNulls(data[1].get(i)))) {
                        list.add(ignoreNulls(data[1].get(i)));
                    }
                }
                sortStrings(list);
                break;

            case(MEDIA_ID_MUSICS_BY_YEAR):
                data = DBB.customQuery(query, selection, 1);
                for (int i = 0; i < data[0].size(); i++) {
                    if(!list.contains(ignoreNulls(data[0].get(i)))) {
                        list.add(ignoreNulls(data[0].get(i)));
                    }
                }
                sortStrings(list);
                break;

            case(MEDIA_ID_MUSICS_BY_DATE):
                data = DBB.customQuery(query, selection, 1);
                for (int i = 0; i < data[0].size(); i++) {
                    if(!list.contains(ignoreNulls(data[0].get(i)))) {
                        list.add(ignoreNulls(data[0].get(i)));
                    }
                }
                sortStrings(list);
                break;
        }
        return list;
    }

    private String buildQuery(String newMediaId) {
        String projection = "";
        switch (newMediaId) {
            case (MEDIA_ID_MUSICS_BY_SONG):
                projection = "A.ID, A.Title";
                break;
            case (MEDIA_ID_MUSICS_BY_ARTIST):
                projection = "B.ID, B.Artist";
                break;
            case (MEDIA_ID_MUSICS_BY_ALBUM):
                projection = "A.Album";
                break;
            case (MEDIA_ID_MUSICS_BY_GENRE):
                projection = "D.ID, D.Genre";
                break;
            case (MEDIA_ID_MUSICS_BY_YEAR):
                projection = "A.Year";
                break;
            case (MEDIA_ID_MUSICS_BY_DATE):
                projection = "A.ModifiedDate";
                break;
        }

        return "select " + projection + "\n" +
                "from Songs A, Artists B, Genres D,\n" +
                "SongArtists E, SongGenres F\n" +
                "where A.ID = E.SongID and\n" +
                "A.ID = F.SongID and\n" +
                "B.ID = E.ArtistID and\n" +
                //"A.Album = C.Album and\n" +
                //"A.AlbumArtist = C.AlbumArtist and\n" +
                "D.ID = F.GenreID and\n" +
                "A.Title like ? and\n" +
                "B.Artist like ? and\n" +
                "A.Album like ? and\n" +
                "D.Genre like ? and\n" +
                "A.Year like ? and\n" +
                "A.ModifiedDate like ?";
    }

    private int getSelectionSlot(String currentMediaId) {
        int slot = -1;
        switch (currentMediaId) {
            case (MEDIA_ID_MUSICS_BY_SONG):
                slot = 0;
                break;
            case (MEDIA_ID_MUSICS_BY_ARTIST):
                slot = 1;
                break;
            case (MEDIA_ID_MUSICS_BY_ALBUM):
                slot = 2;
                break;
            case (MEDIA_ID_MUSICS_BY_GENRE):
                slot = 3;
                break;
            case (MEDIA_ID_MUSICS_BY_YEAR):
                slot = 4;
                break;
            case (MEDIA_ID_MUSICS_BY_DATE):
                slot = 5;
                break;
        }
        return slot;
    }

    private List getMusicFromItem(String table, String projectionStr,  String selectionStr, String selection) {
        String[] projection = {projectionStr};
        String[] selectionArgs = {selection};
        List[] data = DBB.normalQuery(table, projection, selectionStr, selectionArgs, null, projection.length);
        List<MediaMetadataCompat> list = new ArrayList<>();

        try {
            if (data[0] != null) {
                for (int i = 0; i < data[0].size(); i++) {
                    list.add(musicList.get(songIDList.indexOf(ignoreNulls(data[0].get(i)))));
                }
                if(selectionStr.contains("Album")) {
                    sortMusicByTrackNum(list);
                }
                else {
                    sortMusic(list);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List getMusicFromAlbum(String table, String projectionStr,  String selectionStr, String selection, String albumArtist) {
        String[] projection = {projectionStr};
        String[] selectionArgs = {selection, albumArtist};
        List[] data = DBB.normalQuery(table, projection, selectionStr, selectionArgs, null, projection.length);
        List<MediaMetadataCompat> list = new ArrayList<>();

        try {
            if (data[0] != null) {
                for (int i = 0; i < data[0].size(); i++) {
                    list.add(musicList.get(songIDList.indexOf(ignoreNulls(data[0].get(i)))));
                }
                sortMusicByTrackNum(list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void sortMusic(List list) {
        Collections.sort(list, new Comparator<MediaMetadataCompat>() {
            @Override
            public int compare(MediaMetadataCompat metadata1, MediaMetadataCompat metadata2) {
                String item1 = metadata1.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                String item2 = metadata2.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
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

    private void sortMusicByTrackNum(List list) {
        Collections.sort(list, new Comparator<MediaMetadataCompat>() {
            @Override
            public int compare(MediaMetadataCompat left, MediaMetadataCompat right) {
                final long leftTrack = left.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER);
                final long rightTrack = right.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER);
                return (leftTrack < rightTrack) ? -1 : (leftTrack == rightTrack ? 0 : 1);
            }
        });
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

    // Sorts the lists with numbers in ascending order
    private void sortDates(List items) {
        Collections.sort(items, new Comparator<String>() {
                @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                @Override
                public int compare(String item1, String item2) {
                int date1Num = 0;
                int date2Num = 0;
                //Fri Oct 06 22:52:31 EDT 2017
                try {
                    // Switch to dd-MMM-yyyy for next update
                    Date date1 = new SimpleDateFormat("dd-MMM-yyyy", Locale.US).parse(item1);
                    Date date2 = new SimpleDateFormat("dd-MMM-yyyy", Locale.US).parse(item2);
                    date1Num = Integer.parseInt(new SimpleDateFormat("yyyyMMdd", Locale.US).format(date1));
                    date2Num = Integer.parseInt(new SimpleDateFormat("yyyyMMdd", Locale.US).format(date2));
                }
                catch(ParseException e) {
                    e.printStackTrace();
                }
                return Integer.compare(date2Num, date1Num);
                }
            });
    }

    // Sorts the lists with numbers in ascending order
    private void sortNums(List items) {
        Collections.sort(items, new Comparator<Integer>()
        {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public int compare(Integer item1, Integer item2)
            {
                return Integer.compare(item1, item2);
            }
        });
    }

    private String extractMediaIdFromSortOrder(int index) {
        String mediaId = "";
        String item = sortOrder.get(index);
        switch (item) {
            case "Artists":
                mediaId = MEDIA_ID_MUSICS_BY_ARTIST;
                break;
            case "Albums":
                mediaId = MEDIA_ID_MUSICS_BY_ALBUM;
                break;
            case "Genres":
                mediaId = MEDIA_ID_MUSICS_BY_GENRE;
                break;
            case "Years":
                mediaId = MEDIA_ID_MUSICS_BY_YEAR;
                break;
            case "Date Added":
                mediaId = MEDIA_ID_MUSICS_BY_DATE;
                break;
        }
        return mediaId;
    }

    private List extractListFromSortOrder(int index) {
        List list = null;
        String item = sortOrder.get(index);
        switch (item) {
            case "Artists":
                createSortedItemList(artistList, sortedArtistList);
                list = sortedArtistList;
                break;
            case "Albums":
                createSortedItemList(albumList, sortedAlbumList);
                list = sortedAlbumList;
                break;
            case "Genres":
                createSortedItemList(genreList, sortedGenreList);
                list = sortedGenreList;
                break;
            case "Years":
                createSortedItemList(yearList, sortedYearList);
                list = sortedYearList;
                break;
            case "Date Added":
                createSortedItemList(dateList, sortedDateList);
                list = sortedDateList;
                break;
        }
        return list;
    }

    public List<MediaMetadataCompat> getCurrentList() {
        //System.out.println(currentList.get(0));
        return currentList;
    }

    public void customize(ArrayList<String> sortOrder, ArrayList<String> searchStrings) {
        this.sortOrder = sortOrder;
        this.searchStrings = searchStrings;
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
