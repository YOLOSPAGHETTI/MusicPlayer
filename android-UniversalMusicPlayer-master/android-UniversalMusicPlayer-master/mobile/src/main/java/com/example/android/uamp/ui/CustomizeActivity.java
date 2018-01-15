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

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.android.uamp.MusicService;
import com.example.android.uamp.R;
import com.example.android.uamp.utils.LogHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class CustomizeActivity extends BaseActivity {

    private static final String TAG = LogHelper.makeLogTag(CustomizeActivity.class);

    private ArrayList<String> sortOrder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        setContentView(R.layout.customize);

        initializeToolbar();

        sortOrder = new ArrayList<>();

        final Button button1 = (Button)findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addToSortOrder((String) button1.getText());
            }
        });

        final Button button2 = (Button)findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addToSortOrder((String) button2.getText());
            }
        });

        final Button button3 = (Button)findViewById(R.id.button3);
        button3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addToSortOrder((String) button3.getText());
            }
        });

        final Button button4 = (Button)findViewById(R.id.button4);
        button4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addToSortOrder((String) button4.getText());
            }
        });

        final Button button5 = (Button)findViewById(R.id.button5);
        button5.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addToSortOrder((String) button5.getText());
            }
        });

        final Button clear_button = (Button)findViewById(R.id.clear_button);
        clear_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sortOrder = null;
                sortOrder = new ArrayList<>();
                TextView img=(TextView) findViewById(R.id.img1);
                img.setVisibility(View.INVISIBLE);
                img=(TextView) findViewById(R.id.img2);
                img.setVisibility(View.INVISIBLE);
                img=(TextView) findViewById(R.id.img3);
                img.setVisibility(View.INVISIBLE);
                img=(TextView) findViewById(R.id.img4);
                img.setVisibility(View.INVISIBLE);
                img=(TextView) findViewById(R.id.img5);
                img.setVisibility(View.INVISIBLE);
                EditText text=(EditText) findViewById(R.id.search1);
                text.setVisibility(View.INVISIBLE);
                text=(EditText) findViewById(R.id.search2);
                text.setVisibility(View.INVISIBLE);
                text=(EditText) findViewById(R.id.search3);
                text.setVisibility(View.INVISIBLE);
                text=(EditText) findViewById(R.id.search4);
                text.setVisibility(View.INVISIBLE);
                text=(EditText) findViewById(R.id.search5);
                text.setVisibility(View.INVISIBLE);
                text=(EditText) findViewById(R.id.search6);
                text.setVisibility(View.INVISIBLE);
                text=(EditText) findViewById(R.id.search7);
                text.setVisibility(View.INVISIBLE);
            }
        });

        final EditText edit1 = (EditText)findViewById(R.id.search5);
        final EditText edit2 = (EditText)findViewById(R.id.search6);
        final EditText edit3 = (EditText)findViewById(R.id.search7);

        final Button submit_button = (Button)findViewById(R.id.submit_button);
        submit_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ArrayList<String> searchStrings = getSearchStrings(edit1, edit2, edit3);
                if(searchStrings != null) {
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    navigationView.setCheckedItem(R.id.navigation_allmusic);
                    Intent intent = new Intent(CustomizeActivity.this, MusicPlayerActivity.class);

                    intent.putExtra(MusicService.SORT_ORDER_MESSAGE, sortOrder);
                    intent.putExtra(MusicService.SEARCH_MESSAGE, searchStrings);
                    startActivityForResult(intent, 6262);
                }
                else {
                    // Pop-up for date error
                }
            }
        });

        setDateTextListeners(edit1, edit2);

        if(!shouldShowControls()) {
            View controls = findViewById(R.id.fragment_playback_controls);
            controls.setVisibility(View.INVISIBLE);
        }
    }

    public void addToSortOrder(String text) {
        if(sortOrder.contains(text)) {
            int textIndex = sortOrder.indexOf(text);
            sortOrder.remove(text);
            TextView img=getHiddenTextViewFromText(text);
            img.setVisibility(View.INVISIBLE);
            setEditTextVisibility(text, View.INVISIBLE);
            for(int i=textIndex; i<sortOrder.size(); i++) {
                img=getHiddenTextViewFromText(sortOrder.get(i));
                int sortNum = i + 1;
                img.setText(""+sortNum);
            }
        }
        else {
             sortOrder.add(text);
             TextView img=getHiddenTextViewFromText(text);
             img.setVisibility(View.VISIBLE);
             setEditTextVisibility(text, View.VISIBLE);
             int sortNum = sortOrder.indexOf(text) + 1;
             img.setText(""+sortNum);
        }
    }

    // Gets all the search strings
    public ArrayList<String> getSearchStrings(EditText edit5, EditText edit6, EditText edit7) {
        ArrayList<String> searchStrings = new ArrayList<>();
        if(edit5.getVisibility() == View.VISIBLE) {
            String day = edit5.getText().toString();
            String month = edit6.getText().toString();
            String year = edit7.getText().toString();

            for(int i=searchStrings.size();i<sortOrder.indexOf("Date Added"); i++) {
                searchStrings.add(i, null);
            }
            if(checkValidDate(day, month, year)) {
                searchStrings.add(sortOrder.indexOf("Date Added"),day+month+year);
            }
            else {
                System.out.println("invalid date");
                return null;
            }
        }
        EditText edit=(EditText) findViewById(R.id.search1);
        if(edit.getVisibility() == View.VISIBLE) {
            String text = edit.getText().toString();
            searchStrings = setSearchStrings(searchStrings, text, "Artists");
        }
        edit=(EditText) findViewById(R.id.search2);
        if(edit.getVisibility() == View.VISIBLE) {
            String text = edit.getText().toString();
            searchStrings = setSearchStrings(searchStrings, text, "Albums");
        }
        edit=(EditText) findViewById(R.id.search3);
        if(edit.getVisibility() == View.VISIBLE) {
            String text = edit.getText().toString();
            searchStrings = setSearchStrings(searchStrings, text, "Genres");
        }
        edit=(EditText) findViewById(R.id.search4);
        if(edit.getVisibility() == View.VISIBLE) {
            String text = edit.getText().toString();
            searchStrings = setSearchStrings(searchStrings, text, "Years");
        }
        return searchStrings;
    }

    private ArrayList<String> setSearchStrings(ArrayList<String> searchStrings, String text, String item) {
        for(int i=searchStrings.size();i<sortOrder.indexOf(item); i++) {
            searchStrings.add(i, null);
        }
        searchStrings.add(sortOrder.indexOf(item),text);

        return searchStrings;
    }


    // Gets the handle of the hidden text view from the text of the button
    public TextView getHiddenTextViewFromText(String text) {
        TextView img=null;
        switch (text) {
            case "Artists":
                img = (TextView) findViewById(R.id.img1);
                break;
            case "Albums":
                img = (TextView) findViewById(R.id.img2);
                break;
            case "Genres":
                img = (TextView) findViewById(R.id.img3);
                break;
            case "Years":
                img = (TextView) findViewById(R.id.img4);
                break;
            case "Date Added":
                img = (TextView) findViewById(R.id.img5);
                break;
        }
        return img;
    }

    // Gets the handle of the hidden text view from the text of the button
    public void setEditTextVisibility(String text, int visibility) {
        EditText img=null;
        switch (text) {
            case "Artists":
                img = (EditText) findViewById(R.id.search1);
                img.setVisibility(visibility);
                break;
            case "Albums":
                img = (EditText) findViewById(R.id.search2);
                img.setVisibility(visibility);
                break;
            case "Genres":
                img = (EditText) findViewById(R.id.search3);
                img.setVisibility(visibility);
                break;
            case "Years":
                img = (EditText) findViewById(R.id.search4);
                img.setVisibility(visibility);
                break;
            case "Date Added":
                img = (EditText) findViewById(R.id.search5);
                img.setVisibility(visibility);
                img = (EditText) findViewById(R.id.search6);
                img.setVisibility(visibility);
                img = (EditText) findViewById(R.id.search7);
                img.setVisibility(visibility);
                break;
        }
    }

    private void setDateTextListeners(final EditText edit1, final EditText edit2) {
        edit1.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {}

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                if (!s.toString().equals("")) {
                    if (Integer.parseInt(s.toString()) > 31) {
                        edit2.setText("31");
                    }
                }
            }
        });
        edit2.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {}

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                if (!s.toString().equals("")) {
                    if (Integer.parseInt(s.toString()) > 12) {
                        edit2.setText("12");
                    }
                }
            }
        });
    }

    private boolean checkValidDate(String day, String month, String year) {
        try {

            SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyy", Locale.US);
            sdf.setLenient(false);

            //if not valid, it will throw ParseException
            Date date = sdf.parse(day+month+year);

        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}