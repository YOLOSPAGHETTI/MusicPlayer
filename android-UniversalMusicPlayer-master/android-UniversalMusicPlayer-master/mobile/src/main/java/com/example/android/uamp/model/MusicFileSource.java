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
import java.util.Date;
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
    private CompletionCalculation cc;

    public MusicFileSource(DBBuilder DBB, CompletionCalculation cc) {
        this.DBB = DBB;
        this.cc = cc;
    }

    @Override
    public Iterator<MediaMetadataCompat> iterator() {
        tracks = new ArrayList<>();
        if (DBB.isEmpty()) {
            populateDBWithMusicFiles();
        }

        //DBB.printTable("Songs",12);

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
            cc.recalculate(0, Math.round(completionDec*100));
            MediaMetadataRetriever mMMDR = new MediaMetadataRetriever();

            try {
                mMMDR.setDataSource(file.getAbsolutePath()); // IllegalStateException, Runtime Exception
                String title = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String album = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                String artist = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String albumArtist = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
                String genre = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                String yearStr = mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
                int year = -1;
                if(yearStr != null) {
                    if(yearStr.length() == 4) {
                        year = ignoreInvalidNums(yearStr);
                    }
                }
                String source = file.toURI().toString();

                int trackNumber = ignoreInvalidNums(mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
                int duration = ignoreInvalidNums(mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                int totalTrackCount = ignoreInvalidNums(mMMDR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS));
                String modifiedDate = (new Date(file.lastModified())).toString();

                LogHelper.d(TAG, "Found music track: ", title);

                if (genre == null) {
                    genre = "Unknown";
                }

                if(!title.equals("")) {
                    DBB.insertFromMetadata(new String[]{source, artist, album, albumArtist, genre, title, modifiedDate}, new Integer[]{duration, year, trackNumber, totalTrackCount});
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Grabs all the id3 data from the DB to populate the music tracks
    private void populateTracksFromDB() {
        String[] projection = {"ID", "Title", "Album", "Artist", "AlbumArtist", "Genre", "Year", "Source", "TrackNumber", "TotalTrackCount", "Duration", "ModifiedDate"};
        String[] selectionArgs = {"%"};
        List[] data = DBB.normalQuery("Songs", projection, "ID like ?", selectionArgs, null, projection.length);
        if(data[0] != null) {
        for (int i = 0; i < data[0].size(); i++) {
            float completionDec = (float)i / (float)(data[0].size()-1);
            cc.recalculate(1, Math.round(completionDec*100));
            try {
                String id = ignoreNulls(data[0].get(i));
                String title = ignoreNulls(data[1].get(i));

                String album = ignoreNulls(data[2].get(i));
                String artist = ignoreNulls(data[3].get(i));
                String albumArtist = ignoreNulls(data[4].get(i));
                String genre = ignoreNulls(data[5].get(i));
                int year = ignoreInvalidNums(ignoreNulls(data[6].get(i)));
                String source = ignoreNulls(data[7].get(i));
                int trackNumber = ignoreInvalidNums(ignoreNulls(data[8].get(i)));
                int totalTrackCount = ignoreInvalidNums(ignoreNulls(data[9].get(i)));
                int duration = ignoreInvalidNums(ignoreNulls(data[10].get(i)));
                String modifiedDate = ignoreNulls(data[11].get(i));

                 MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                        .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, source)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, albumArtist)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                        .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                        .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, year)
                        //.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
                        .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount)
                        .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_DATE_ADDED, modifiedDate)
                        .build();

                tracks.add(metadata);
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
        File storage = new File("/storage/0000-0000/Music/Downloads/");

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
}
