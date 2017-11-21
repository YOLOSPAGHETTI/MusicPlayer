package com.example.android.uamp.model;

/**
 * Created by Kyler C on 11/2/2017.
 */

import java.util.*;
class ArtistParser {
    private String artistText;
    private String songText;
    private String[] delims;
    public ArrayList<String> artists = new ArrayList<>();


    public ArtistParser(String artistText, String songText) {
        this.artistText = artistText;
        this.songText = songText;
    }

    public ArrayList<String> getArtists(String[] delims, String[] songTextIgnore) {
        this.delims = delims;

        if(songText != null) {
            if (!songText.equals("")) {
                splitByDelims(artistText);
            }
        }
        if(songText != null) {
            if(!songText.equals("")) {
                getArtistsFromSong(songTextIgnore);
            }
        }

        return artists;
    }

    public void splitByDelims(String text) {
        String[][] temp = new String[delims.length][];

        for(int i=0; i<delims.length; i++) {
            temp[i] = text.split(delims[i]);
            for(int j=0; j<temp[i].length; j++) {
                for(int k=0; k<delims.length; k++) {
                    if(temp[i][j].contains(delims[k])) {
                        temp[i][j] = temp[i][j].substring(0, temp[i][j].indexOf(delims[k]));
                    }
                }
            }
        }

        for(int i=0; i<delims.length; i++) {
            for(int j=0; j<temp[i].length; j++) {
                if(!temp[i][j].trim().equals("") && !artists.contains(temp[i][j].trim())) {
                    artists.add(temp[i][j].trim());
                }
            }
        }
    }

    public void getArtistsFromSong(String[] songTextIgnore) {
        int spot;
        String songArtistText="";
        for(int i=0; i<songTextIgnore.length; i++) {
            if (songText.contains(songTextIgnore[i])) {
                spot = songText.indexOf(songTextIgnore[i]);
                boolean openParen = false;
                for(int j=0; j<songText.length(); j++) {
                    if(songText.charAt(j) == '(') {
                        openParen = true;
                    }
                    if(songText.charAt(j) == ')') {
                        if(j<spot) {
                            songArtistText = "";
                        }
                        openParen = false;
                    }
                    if(openParen) {
                        if(j<spot || j>spot+songTextIgnore[i].length()) {
                            songArtistText = songArtistText+songText.charAt(j);
                        }
                    }
                }

                splitByDelims(songArtistText);
            }
        }
    }
}