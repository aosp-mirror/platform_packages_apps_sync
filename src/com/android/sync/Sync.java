/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.sync;

import android.app.Activity;
import android.app.ActivityThread;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.database.Cursor;
import android.database.DatabaseUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class Sync extends Activity {
    private static final String TAG = "SyncPanel";
    // TODO: move this to a centralized place?
    private static final String SYNC_CONNECTION_SETTING_CHANGED
            = "com.android.sync.SYNC_CONN_STATUS_CHANGED";

    private static final int SYNC_BUTTON = 1;
    private static final int CANCEL_BUTTON = 2;

    private Button mSyncButton;
    private Button mAdvancedButton;
    private LinearLayout mAdvancedSection;
    private Button mHistoryButton;
    private TextView mAccountTextView;
    private TextView mStatusView;
    private TextView mMoreStatusView;

    private CheckBox mListenForTicklesCheckBox;
    private Spinner mContentNameSpinner;
    private CheckBox mSyncProviderOnTickleCheckBox;

    private List mProviderNames;
    private List mProviderInfos;

    private HashMap<String, Boolean> mProvidersToTickle = null;

    android.provider.Sync.Settings.QueryMap mSyncSettings;

    private OnClickListener mStartSync = new OnClickListener() {
        public void onClick(View v) {
            doSync();
        }
    };

    private OnClickListener mCancelSync = new OnClickListener() {
        public void onClick(View v) {
            mStatusView.setText("Cancelled");
            setSyncButton(SYNC_BUTTON);
            getContentResolver().cancelSync(null /* cancel any sync */);
        }
    };

    private OnClickListener mToggleListenForTickles = new OnClickListener() {

        public void onClick(View v) {
            boolean oldListenForTickles = mSyncSettings.getListenForNetworkTickles();
            boolean listenForTickles = mListenForTicklesCheckBox.isChecked();
            if (oldListenForTickles != listenForTickles) {
                mSyncSettings.setListenForNetworkTickles(listenForTickles);
                Intent intent = new Intent();
                intent.setAction(SYNC_CONNECTION_SETTING_CHANGED);
                sendBroadcast(intent);
            }
        }
    };

    private AdapterView.OnItemSelectedListener mContentNameListener =
            new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(
                        AdapterView parent, View v,
                        int position,
                        long id) {
                    updateProviderUi();
                }

                public void onNothingSelected(AdapterView parent) {
                    // TODO: when does this happen? What does it mean?
                }
            };
    
    private OnClickListener mToggleSyncOnTickle = new OnClickListener() {
        public void onClick(View v) {
            String providerName = getSelectedProviderName();
            boolean syncOnTickle =
                    mSyncProviderOnTickleCheckBox.isChecked();
            ContentResolver cr = getContentResolver();
            boolean oldShouldSyncOnTickle =
                    mSyncSettings.getSyncProviderAutomatically(providerName);
            if (syncOnTickle != oldShouldSyncOnTickle) {
                mSyncSettings.setSyncProviderAutomatically(providerName, syncOnTickle);
                updateCachedSettingsAndProviderUi();
            }
        }
    };

    private android.provider.Sync.Active.QueryMap mActiveSyncQueryMap;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();

        // init views
        setContentView(R.layout.sync_screen);

        mAccountTextView = (TextView) findViewById(R.id.account);
        mStatusView = (TextView) findViewById(R.id.status);
        mMoreStatusView = (TextView) findViewById(R.id.moreStatus);
        mSyncButton = (Button) findViewById(R.id.sync);
        mAdvancedButton = (Button) findViewById(R.id.advanced);
        mAdvancedSection = (LinearLayout) findViewById(R.id.advancedSection);
        mAdvancedSection.setVisibility(View.INVISIBLE);
        mAdvancedButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                int visibility = mAdvancedSection.getVisibility();
                mAdvancedSection.setVisibility(visibility == View.INVISIBLE ?
                        View.VISIBLE : View.INVISIBLE);
            }});
        mHistoryButton = (Button) mAdvancedSection.findViewById(R.id.history);
        mHistoryButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_RUN);
                intent.setClass(Sync.this, SyncHistory.class);
                intent.putExtra("authority", getSelectedProviderName());
                startActivity(intent);
            }});

        final ContentResolver contentResolver = getContentResolver();

        mSyncSettings = new android.provider.Sync.Settings.QueryMap(contentResolver,
                true /* keep updated */, null);
        mSyncSettings.addObserver(new Observer() {
            public void update(Observable o, Object arg) {
                updateCachedSettingsAndProviderUi();
            }
        });

        mActiveSyncQueryMap = new android.provider.Sync.Active.QueryMap(getContentResolver(),
                false /* don't keep updated, we will change this in onResume()/onPause() */,
                null /* use this thread's handler for notifications */);
        mActiveSyncQueryMap.addObserver(mSyncObserver);

        mProviderNames = new ArrayList();
        mProviderInfos = new ArrayList();
        try {
            ActivityThread.getPackageManager().querySyncProviders(mProviderNames,
                    mProviderInfos);
        } catch (RemoteException e) {
        }
        mProviderNames.add(0, "All");
        mProviderInfos.add(0, null);

        ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_spinner_item, mProviderNames);
        adapter.setDropDownViewResource(
                android.R.layout.simple_dropdown_item_1line);
        mContentNameSpinner = (Spinner) findViewById(R.id.contentName);
        mContentNameSpinner.setAdapter(adapter);
        mContentNameSpinner.setOnItemSelectedListener(mContentNameListener);

        mListenForTicklesCheckBox =
                (CheckBox) findViewById(R.id.listenForTickles);
        mListenForTicklesCheckBox.setOnClickListener(mToggleListenForTickles);

        mSyncProviderOnTickleCheckBox =
                (CheckBox) findViewById(R.id.syncProviderOnTickle);
        mSyncProviderOnTickleCheckBox.setOnClickListener(
                mToggleSyncOnTickle);

        updateCachedSettingsAndProviderUi();
        updateStatusUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActiveSyncQueryMap.setKeepUpdated(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActiveSyncQueryMap.setKeepUpdated(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mActiveSyncQueryMap != null) {
            mActiveSyncQueryMap.close();
            mActiveSyncQueryMap = null;
        }
    }

    private void setSyncButton(int state) {
        if (state == CANCEL_BUTTON) {
            mSyncButton.setOnClickListener(mCancelSync);
            mSyncButton.setText("Cancel");
        } else {
            mSyncButton.setOnClickListener(mStartSync);
            mSyncButton.setText("Sync");
        }
    }

    private void doSync() {
        mStatusView.setText("Syncing");
        setSyncButton(CANCEL_BUTTON);

        Uri filter = null;
        String providerName = getSelectedProviderName();
        if (providerName != null) {
            filter = Uri.parse("content://" + providerName);
        }

        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
        getContentResolver().startSync(filter, extras);
    }

    /**
     * Returns the name of the selected provider, or null if "all" is selected.
     *
     * @return the name of the selected provider, or null if "all" is selected
     */
    private String getSelectedProviderName() {
        int selectedIndex = mContentNameSpinner.getSelectedItemPosition();
        if (selectedIndex != 0) {
            return (String) mProviderNames.get(selectedIndex);
        } else {
            return null;
        }
    }

    private Observer mSyncObserver = new Observer() {
        public void update(Observable o, Object arg) {
            updateStatusUi();
        }
    };

    private int mProgressCount = 0;

    private String nextProgressText() {
        int progressCount = mProgressCount;
        mProgressCount++;
        switch (progressCount) {
            case 0: return "|";
            case 1: return "/";
            case 2: return "-";
            default:
                mProgressCount = 0;
                return "\\";
        }
    }
    
    private void updateStatusUi() {
        ContentValues syncInfo = mActiveSyncQueryMap.getActiveSyncInfo();
        boolean syncing = syncInfo != null;
        
        if (!syncing) {

            ContentValues values = queryPreviousSyncInfo();
            if (values == null) {
                mAccountTextView.setVisibility(View.INVISIBLE);
                mStatusView.setText("The sync history is empty");
                mMoreStatusView.setText("");
            } else {
                mAccountTextView.setVisibility(View.VISIBLE);
                String account = values.getAsString(android.provider.Sync.History.ACCOUNT);
                String authority = values.getAsString(android.provider.Sync.History.AUTHORITY);
                long elapsedTime = values.getAsLong(android.provider.Sync.History.ELAPSED_TIME);
                String result = android.provider.Sync.History.mesgToString(
                        values.getAsString(android.provider.Sync.History.MESG));
                mAccountTextView.setText("Account: " + account);
                mStatusView.setText("spent " + (elapsedTime / 1000) + " second(s) on "
                        + authority);
                mMoreStatusView.setText("result: " + result);
            }
        } else {
            String account = syncInfo.getAsString(android.provider.Sync.Active.ACCOUNT);
            String authority = syncInfo.getAsString(android.provider.Sync.Active.AUTHORITY);
            mAccountTextView.setVisibility(View.VISIBLE);
            mAccountTextView.setText("Account: " + account);
            mStatusView.setText("Syncing " + authority);
            mMoreStatusView.setText(nextProgressText());
        }


        if (syncing) {
            setSyncButton(CANCEL_BUTTON);
        } else {
            setSyncButton(SYNC_BUTTON);
        }
    }

    private synchronized void updateProviderUi() {
        String providerName = getSelectedProviderName();
        if (providerName != null) {
            boolean syncOnTickle = mProvidersToTickle.get(providerName);
            String text = "Sync " + providerName +
                    " when changes happen on the server";
            mSyncProviderOnTickleCheckBox.setChecked(syncOnTickle);
            mSyncProviderOnTickleCheckBox.setText(text);
            mSyncProviderOnTickleCheckBox.setVisibility(View.VISIBLE);
        } else {
            mSyncProviderOnTickleCheckBox.setVisibility(View.INVISIBLE);
        }
    }

    private synchronized void updateCachedSettingsAndProviderUi() {
        if (mProvidersToTickle == null) {
            mProvidersToTickle = new HashMap<String, Boolean>();
            Adapter adapter = mContentNameSpinner.getAdapter();
            int numProviders = adapter.getCount();
            for (int i = 0; i < numProviders; i++) {
                mProvidersToTickle.put((String) adapter.getItem(i), false);
            }
        }
        for (String provider : mProvidersToTickle.keySet()) {
            mProvidersToTickle.put(provider, mSyncSettings.getSyncProviderAutomatically(provider));
        }
        mListenForTicklesCheckBox.setChecked(mSyncSettings.getListenForNetworkTickles());
        updateProviderUi();
    }

    private ContentValues queryPreviousSyncInfo() {
        Cursor c = getContentResolver().query(android.provider.Sync.History.CONTENT_URI, null,
                "event=" + android.provider.Sync.History.EVENT_STOP, null,
                android.provider.Sync.History.EVENT_TIME + " desc");
        try {
            if (c.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(c, values);
                return values;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }
}
