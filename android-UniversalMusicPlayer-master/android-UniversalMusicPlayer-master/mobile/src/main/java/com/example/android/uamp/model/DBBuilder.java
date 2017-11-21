package com.example.android.uamp.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Kyler C on 10/26/2017.
 */

public class DBBuilder extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "SongInfo.db";
    private SQLiteDatabase db;
    private ArrayList<Long> validSongRows = new ArrayList<Long>();
    private long maxSongID;
    private long maxArtistID;
    private long maxAlbumID;
    private long maxGenreID;
    private long maxPlaylistID;

    public DBBuilder(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        this.db = db;
        createTables();
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        this.db = db;
        createTables();
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    // Creates all tables in the DB at initialization
    private void createTables() {
        maxSongID = 0;
        maxArtistID = 0;
        maxAlbumID = 0;
        maxGenreID = 0;
        maxPlaylistID = 0;
        String SQL_CREATE_SONGS = "CREATE TABLE Songs (" +
                "    ID Number(20)," +
                "    Title Varchar2(60)," +
                "    Album Varchar2(60)," +
                "    Artist Varchar2(60)," +
                "    AlbumArtist Varchar2(60)," +
                "    Genre Varchar2(30)," +
                "    Year Number(4)," +
                "    Decade Number(4)," +
                "    Source Varchar2(2000)," +
                "    TrackNumber Number(2)," +
                "    TotalTrackCount Number(2)," +
                "    Duration Number(6)," +
                ");";
        db.execSQL(SQL_CREATE_SONGS);
        String SQL_CREATE_ARTISTS = "CREATE TABLE Artists (" +
                "    ID Number(20)," +
                "    Artist Varchar2(60)," +
                ");";
        db.execSQL(SQL_CREATE_ARTISTS);
        String SQL_CREATE_ALBUMS = "CREATE TABLE Albums (" +
                "    ID Number(20)," +
                "    Album Varchar2(60)," +
                "    AlbumArtist Varchar2(60)," +
                "    TotalTrackCount Number(2)," +
                "    Duration Number(10)," +
                ");";
        db.execSQL(SQL_CREATE_ALBUMS);
        String SQL_CREATE_GENRES = "CREATE TABLE Genres (" +
                "    ID Number(20)," +
                "    Genre Varchar2(30)" +
                ");";
        db.execSQL(SQL_CREATE_GENRES);
        String SQL_CREATE_PLAYLISTS = "CREATE TABLE Playlists (" +
                "    ID Number(20)," +
                "    Playlist Varchar2(60)" +
                ");";
        db.execSQL(SQL_CREATE_PLAYLISTS);
        String SQL_CREATE_PLAYLIST_SONGS = "CREATE TABLE PlaylistSongs (" +
                "    SongID Number(20)," +
                "    PlaylistID Number(20)" +
                ");";
        db.execSQL(SQL_CREATE_PLAYLIST_SONGS);
        String SQL_CREATE_SONG_ARTISTS = "CREATE TABLE SongArtists (" +
                "    SongID Number(20)," +
                "    ArtistID Number(20)" +
                ");";
        db.execSQL(SQL_CREATE_SONG_ARTISTS);
        String SQL_CREATE_SONG_GENRES = "CREATE TABLE SongGenres (" +
                "    SongID Number(20)," +
                "    GenreID Number(20)" +
                ");";
        db.execSQL(SQL_CREATE_SONG_GENRES);
    }

    void insertFromMetadata(String[] strings, Integer[] nums) {

        String source = strings[0];
        String artist = strings[1];
        String album = strings[2];
        String albumArtist = strings[3];
        String genre = strings[4];
        String title = strings[5];
        int duration = nums[0];
        int trackNumber = nums[3];

        boolean newSource = getNewSource(source);

        if(newSource) {
            // Values for one row
            ArrayList<String> artists = parseArtists(artist, title);
            for (int i = 0; i < artists.size(); i++) {
                insertArtist(artists.get(i));
            }

            String genreText = genre;
            String[] genres;

            if (genreText != null) {
                genres = genreText.split(",");
                for (String aGenre : genres) {
                    insertGenre(aGenre);
                }
            }

            insertAlbum(album, albumArtist, trackNumber, duration);

            insertSong(strings, nums);
        }
    }

    private boolean getNewSource(String source) {
        boolean newSource = true;
        String song = easyShortQuery("Songs", "ID", "Source = ?", source, null);
        if(!song.equals("")) {
            newSource = false;
        }

        return newSource;
    }

    // Inserts an artist into the database
    private void insertArtist(String artist) {
        String artistId = easyShortQuery("Artists", "ID", "Artist = ?", artist, null);

        if(artistId.equals("")) {
            ContentValues artistValues = new ContentValues();
            artistValues.put("ID", maxArtistID);
            artistValues.put("Artist", artist);

            artistId = ""+maxArtistID;
            maxArtistID++;
            db.insert("Artists", null, artistValues);
        }
        ContentValues songArtistValues = new ContentValues();
        songArtistValues.put("SongID", maxSongID);
        songArtistValues.put("ArtistID", artistId);

        db.insert("SongArtists", null, songArtistValues);
    }

    // Inserts an album into the database
    private void insertAlbum(String album, String songAlbumArtist, int trackNumber, int duration) {
        if (album != null) {
            String[] projection = {"ID"};
            String[] selectionArgs = {album};
            String albumId = "";
            List albumIdList = easyQuery("Albums", projection, "Album = ?", selectionArgs, null);
            for (int i = 0; i < albumIdList.size(); i++) {
                albumId = albumIdList.get(i).toString();
                String albumArtist = easyShortQuery("Albums", "AlbumArtist", "ID = ?", albumId, null);
                if (songAlbumArtist == null) {
                    songAlbumArtist = "";
                }
                if (songAlbumArtist.equals(albumArtist)) {
                    albumId = albumIdList.get(i).toString();
                    break;
                }
            }

            if (albumId.equals("")) {
                ContentValues albumValues = new ContentValues();
                albumValues.put("ID", maxAlbumID);
                albumValues.put("Album", album);
                albumValues.put("AlbumArtist", songAlbumArtist);
                albumValues.put("TotalTrackCount", trackNumber);
                albumValues.put("Duration", duration);
                maxAlbumID++;
                db.insert("Albums", null, albumValues);
            } else {
                String oldDuration = easyShortQuery("Albums", "Duration", "ID = ?", albumId, null);
                long newDuration = -1;
                if (!oldDuration.equals("")) {
                    newDuration = Long.parseLong(oldDuration) + duration;
                }

                // New value for one column
                ContentValues values = new ContentValues();
                if (newDuration != -1) {
                    values.put("Duration", newDuration);
                } else {
                    values.put("Duration", "");
                }

                // Which row to update, based on the title
                easyShortUpdate("Albums", values, "ID = ?", albumId);
            }
        }
    }

    // Inserts a song into the database
    private void insertSong(String[] strings, Integer[] nums) {
        String source = strings[0];
        String artist = strings[1];
        String album = strings[2];
        String albumArtist = strings[3];
        String genre = strings[4];
        String title = strings[5];
        int duration = nums[0];
        int year = nums[1];
        int decade = nums[2];
        int trackNumber = nums[3];
        int totalTrackCount = nums[4];

        ContentValues songValues = new ContentValues();
        songValues.put("ID", maxSongID);
        songValues.put("Title", title);
        songValues.put("Album", album);
        songValues.put("Artist", artist);
        songValues.put("AlbumArtist", albumArtist);
        songValues.put("Genre", genre);
        songValues.put("Year", year);
        songValues.put("Decade", decade);
        songValues.put("Source", source);
        songValues.put("TrackNumber", trackNumber);
        songValues.put("TotalTrackCount", totalTrackCount);
        songValues.put("Duration", duration);
        maxSongID++;

        db.insert("Songs", null, songValues);
    }

    // Inserts a genre into the database
    private void insertGenre(String genre) {
        String genreId = easyShortQuery("Genres", "ID", "Genre = ?", genre.trim(), null);

        if(genreId.equals("")) {
            ContentValues genreValues = new ContentValues();
            genreValues.put("ID", maxGenreID);
            genreValues.put("Genre", genre);
            genreId = ""+maxGenreID;
            maxGenreID++;

            db.insert("Genres", null, genreValues);
        }
        ContentValues songGenreValues = new ContentValues();
        songGenreValues.put("SongID", maxSongID);
        songGenreValues.put("GenreID", genreId);

        db.insert("SongGenres", null, songGenreValues);
    }

    // Runs short single selection/projection query
    private String easyShortQuery(String table, String projection, String selection, String selectionArgs, String sortOrder) {
        String[] shortProjection = {projection};
        String[] shortSelectionArgs = {selectionArgs};
        List list = easyQuery(table, shortProjection, selection, shortSelectionArgs, sortOrder);
        String value = "";
        if(!list.isEmpty()) {
            if(list.get(0) != null) {
                value = list.get(0).toString();
            }
        }

        return value;
    }

    // Runs query to grab first column
    private List easyQuery(String table, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        db = this.getReadableDatabase();

        Cursor cursor = db.query(
                table,                                 // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort order
        );

        List itemIds = new ArrayList<>();
        while(cursor.moveToNext()) {
            String itemId = cursor.getString(0);
            itemIds.add(itemId);
        }
        cursor.close();

        return itemIds;
    }

    // Runs query to grab all columns
    List[] normalQuery(String table, String[] projection, String selection, String[] selectionArgs, String sortOrder, int columns) {
        db = this.getReadableDatabase();

        Cursor cursor = db.query(
                table,                                 // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort order
        );

        List[] allData = new List[columns];
        boolean newArray = true;
        while (cursor.moveToNext()) {
            for(int i=0; i<columns; i++) {
                if(newArray) {
                    allData[i] = new ArrayList<>();
                }
                String itemId = cursor.getString(i);
                //System.out.println(itemId);
                allData[i].add(itemId);
            }
            newArray = false;
        }
        cursor.close();
        return allData;
    }

    // Runs short single selection/projection query
    private int easyShortUpdate(String table, ContentValues values, String selection, String selectionArgs) {
        db = this.getWritableDatabase();
        String[] shortSelectionArgs = {selectionArgs};

        return db.update(
                table,
                values,
                selection,
                shortSelectionArgs);
    }

    // Gets all the artists contained in the artist field (delimited by commas) and
    // contained in the song field (contained in parentheses concatenated with Feat. or Remix)
    private ArrayList<String> parseArtists(String artistText, String songText) {
        String[] delims = {",", "Feat."};
        String[] songTextIgnore = {"Feat.", "Remix"};
        ArtistParser parser = new ArtistParser(artistText, songText);

        return parser.getArtists(delims, songTextIgnore);
    }

    // Gets the current maximum ID of the item from the DB
    private int getMaxID(String table) {
        db = this.getReadableDatabase();
        String maxStr = easyShortQuery(table, "max(ID)", "ID like ?", "%", null);
        int max = 0;
        if(maxStr != null) {
            if(!maxStr.equals("")) {
                max = Integer.parseInt(maxStr);
            }
        }
        System.out.println(table + "     " + max);
        return max;
    }

    // Gets the maximum IDs of all the items
    void getAllMaxIDs() {
        maxSongID = getMaxID("Songs");
        maxArtistID = getMaxID("Artists");
        maxAlbumID = getMaxID("Albums");
        maxGenreID = getMaxID("Genres");
        maxPlaylistID = getMaxID("Playlists");
    }


    public boolean isEmpty() {
        db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM Songs", null);
        boolean empty;
        if (cursor.moveToFirst())
        {
            empty = false;

        } else
        {
            empty = true;
        }
        cursor.close();
        return empty;
    }
}
