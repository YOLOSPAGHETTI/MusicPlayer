# MusicPlayer
This is a personal project to modify the Universal Music Player on Android to include the following functionality:
<br>*Reads local music files from phone storage
<br>*Stores id3 data in SQLLiteDatabase for fast reference at startup
<br>*Separates individual artists/genres out of id3 tags that include multiple artists/genres to allow them to be sorted individually (method defined below)
<br>*Allows user to sort/filter on song, artist, album, genre, year and decade
<br>
<br><h1>Separation process</h1>
<br>*Separates genres by comma
<br>*Separates artists by comma
<br>*Pulls artists from song field if they are contained in parentheses and next to the words "Feat." or "Remix"
<br>
<br>
<br>Still a work in progress.
<br>
<br>Universal Music Player Source
<br>https://github.com/googlesamples/android-UniversalMusicPlayer
