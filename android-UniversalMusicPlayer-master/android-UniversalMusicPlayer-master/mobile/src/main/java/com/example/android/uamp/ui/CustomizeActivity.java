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
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.android.uamp.MusicService;
import com.example.android.uamp.R;
import com.example.android.uamp.utils.LogHelper;

import java.util.ArrayList;

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class CustomizeActivity extends BaseActivity {

    private static final String TAG = LogHelper.makeLogTag(CustomizeActivity.class);

    private ArrayList<String> sortOrder;
    private LocalBroadcastManager localBroadcastManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        setContentView(R.layout.customize);

        initializeToolbar();

        localBroadcastManager = LocalBroadcastManager.getInstance(this);
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
            }
        });


        final Button submit_button = (Button)findViewById(R.id.submit_button);
        submit_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                navigationView.setCheckedItem(R.id.navigation_allmusic);
                startActivity(new Intent(CustomizeActivity.this, MusicPlayerActivity.class));

                Intent intent = new Intent(MusicService.SORT_ORDER_RESULT);
                intent.putExtra(MusicService.SORT_ORDER_MESSAGE, sortOrder);
                localBroadcastManager.sendBroadcast(intent);

                /*Intent intent = new Intent(CustomizeActivity.this, MusicPlayerActivity.class);
                intent.putExtra("SORT_ORDER", sortOrder);
                startActivityForResult(intent, 6262);*/
            }
        });
    }

    public void addToSortOrder(String text) {
        if(sortOrder.contains(text)) {
            int textIndex = sortOrder.indexOf(text);
            sortOrder.remove(text);
            TextView img=getHiddenTextViewFromText(text);
            img.setVisibility(View.INVISIBLE);
            for(int i=textIndex; i<sortOrder.size(); i++) {
                img=getHiddenTextViewFromText(sortOrder.get(i));
                int sortNum = i + 1;
                img.setText(""+sortNum);
            }
        }
        else if(text.equals("Decades") && sortOrder.contains("Years")) {
            int yearIndex = sortOrder.indexOf("Years");
            sortOrder.add(yearIndex, text);
             for(int i=yearIndex; i<sortOrder.size(); i++) {
                 TextView img = getHiddenTextViewFromText(sortOrder.get(i));
                 int sortNum = i + 1;
                 img.setText("" + sortNum);
                 img.setVisibility(View.VISIBLE);
             }
        }
        else {
             sortOrder.add(text);
             TextView img=getHiddenTextViewFromText(text);
             int sortNum = sortOrder.indexOf(text) + 1;
             img.setText(""+sortNum);
             img.setVisibility(View.VISIBLE);
        }
    }

    // Gets the handle of the hidden text view from the text of the button
    public TextView getHiddenTextViewFromText(String text) {
        TextView img=null;
        if(text.equals("Artists")) {
            img = (TextView) findViewById(R.id.img1);
        }
        else if(text.equals("Albums")) {
            img = (TextView) findViewById(R.id.img2);
        }
        else if(text.equals("Genres")) {
            img = (TextView) findViewById(R.id.img3);
        }
        else if(text.equals("Years")) {
            img = (TextView) findViewById(R.id.img4);
        }
        else if(text.equals("Decades")) {
            img = (TextView) findViewById(R.id.img5);
        }
        return img;
    }
}