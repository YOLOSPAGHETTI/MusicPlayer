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

import android.media.MediaMetadataRetriever;
import android.support.v4.media.MediaMetadataCompat;

import com.example.android.uamp.utils.LogHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class to get a list of MusicTrack's based on the file system
 * configuration.
 */
public class MusicFileSource implements MusicProviderSource {

    private static final String TAG = LogHelper.makeLogTag(MusicFileSource.class);

    private ArrayList<MediaMetadataCompat> tracks;
    private ArrayList<File> allMusicFiles = new ArrayList<File>();

    private DBBuilder DBB;
    private long completionPerc = 0;

    public MusicFileSource(DBBuilder DBB) {
        this.DBB = DBB;
    }

    @Override
    public Iterator<MediaMetadataCompat> iterator() {
        tracks = new ArrayList<>();
        if (DBB.isEmpty()) {
            populateDBWithMusicFiles();
        }
        else {
        }
        populateTracksFromDB();
        //Looper.prepare();
        //DBJob job = new DBJob(DBB);

        return tracks.iterator();
    }

    // Populates the DB with the id3 data pulled from the local music files
    private void populateDBWithMusicFiles() {
        getAllMusicFiles();
        DBB.getAllMaxIDs();

        for (int i = 0; i < allMusicFiles.size(); i++) {
            File file = allMusicFiles.get(i);
            float completionDec = (float)i / (float)(allMusicFiles.size()-1);
            completionPerc = Math.round(completionDec*100);
            MediaMetadataRetriever mMMDR = new MediaMetadataRetriever();

            try {
                mMMDR.setDataSource(file.getAbsolutePath()); // IllegalStateException
                String title = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String album = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                String artist = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String albumArtist = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
                String genre = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                String yearStr = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
                int year = -1;
                int decade = -1;
                if(yearStr != null) {
                    if(yearStr.length() == 4) {
                        year = ignoreInvalidNums(yearStr);
                        decade = Integer.parseInt(("" + year).substring(0, ("" + year).length() - 1) + "0");
                    }
                }
                String source = file.toURI().toString();
                /*byte[] iconBytes = mMMDR.getEmbeddedPicture();
                Bitmap bitmap = null;
                if(iconBytes.length > 128) {
                    int factor = iconBytes.length/128;
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = factor;
                    bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length, opts);
                }
                else {
                    bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
                }*/

                int trackNumber = ignoreInvalidNums(mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
                int duration = ignoreInvalidNums(mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                int totalTrackCount = ignoreInvalidNums(mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS));

                LogHelper.d(TAG, "Found music track: ", title);

                if (genre == null) {
                    genre = "Unknown";
                }

                DBB.insertFromMetadata(new String[]{source, artist, album, albumArtist, genre, title}, new Integer[]{duration, year, decade, trackNumber, totalTrackCount});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Grabs all the id3 data from the DB to populate the music tracks
    private void populateTracksFromDB() {
        ArrayList<MediaMetadataCompat> tracks = new ArrayList<>();
        String[] projection = {"ID", "Title", "Album", "Artist", "AlbumArtist", "Genre", "Year", "Decade", "Source", "TrackNumber", "TotalTrackCount", "Duration"};
        String[] selectionArgs = {"%"};
        List[] data = DBB.normalQuery("Songs", projection, "ID like ?", selectionArgs, null, projection.length);
        if(data[0] != null) {
        for (int i = 0; i < data[0].size(); i++) {
            float completionDec = (float)i / (float)(data[0].size()-1);
            completionPerc = Math.round(completionDec*100);
            try {
                String id = ignoreNulls(data[0].get(i));
                String title = ignoreNulls(data[1].get(i));

                if (!title.equals("")) {
                    String album = ignoreNulls(data[2].get(i));
                    String artist = ignoreNulls(data[3].get(i));
                    String albumArtist = ignoreNulls(data[4].get(i));
                    String genre = ignoreNulls(data[5].get(i));
                    int year = ignoreInvalidNums(ignoreNulls(data[6].get(i)));
                    int decade = ignoreInvalidNums(ignoreNulls(data[7].get(i)));
                    String source = ignoreNulls(data[8].get(i));
                    int trackNumber = ignoreInvalidNums(ignoreNulls(data[9].get(i)));
                    int totalTrackCount = ignoreInvalidNums(ignoreNulls(data[10].get(i)));
                    int duration = ignoreInvalidNums(ignoreNulls(data[11].get(i)));


                    MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                            .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, source)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, albumArtist)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                            .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, year)
                            .putLong(MusicProviderSource.CUSTOM_METADATA_TRACK_DECADE, decade)
                            //.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
                            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount)
                            .build();

                    tracks.add(metadata);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        }
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
        if(obj != null) {
            text = obj.toString();
        }
        return text;
    }

    // Gets all local music files from phone storage
    private void getAllMusicFiles() {
        //File root = new File("/");
        //File storage = new File("/storage");
        File storage = new File("/storage/0000-0000/Music/Downloads/Download 48");

        //File[] allRootFolders = root.listFiles();
        File[] allStorageFolders = storage.listFiles();

        //getFolderFromRootDir(allRootFolders);
        getFolderFromRootDir(allStorageFolders);
    }

    // Gets the folders/files from the root folder
    private void getFolderFromRootDir(File[] files) {
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    getMusicFilesInFolder(file);
                } else {
                    String name = file.getName();
                    if (name.contains(".3gp") || name.contains(".mp4") || name.contains(".m4a") || name.contains(".aac") || name.contains(".ts")
                            || name.contains(".flac") || name.contains(".mid") || name.contains(".xmf") || name.contains(".mxmf") || name.contains(".rttl")
                            || name.contains(".rtx") || name.contains(".ota") || name.contains(".imy") || name.contains(".mp3") || name.contains(".mkv")
                            || name.contains(".wav") || name.contains(".ogg")) {
                        allMusicFiles.add(file);
                    }
                }
            }
        }
        else {
            System.out.println("root is empty");
        }
    }

    // Gets the folders/files from all folder levels
    private void getMusicFilesInFolder(File file) {
        if(file.isDirectory()) {
            String subdir = file.getAbsolutePath();
            File subfolder = new File(subdir);
            File[] listOfFiles = subfolder.listFiles();
            if (listOfFiles != null) {
                for (File listOfFile : listOfFiles) {
                    if (listOfFile.isDirectory()) {
                        getMusicFilesInFolder(listOfFile);
                    } else {
                        String name = listOfFile.getName();
                        if (name.contains(".3gp") || name.contains(".mp4") || name.contains(".m4a") || name.contains(".aac") || name.contains(".ts")
                                || name.contains(".flac") || name.contains(".mid") || name.contains(".xmf") || name.contains(".mxmf") || name.contains(".rttl")
                                || name.contains(".rtx") || name.contains(".ota") || name.contains(".imy") || name.contains(".mp3") || name.contains(".mkv")
                                || name.contains(".wav") || name.contains(".ogg")) {
                            allMusicFiles.add(listOfFile);
                        }
                    }
                }
            }
        }
    }

    // Returns the percentage of music files that have been analyzed
    public long getCompletionPerc() {
        return completionPerc;
    }
}
