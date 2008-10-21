/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.pim.Time;
import android.provider.Sync;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.os.Bundle;

public class SyncHistory extends Activity {

    private static final class SyncHistoryAdapter extends CursorAdapter {
        
        private final LayoutInflater mInflater;
        private final Time mTime = new Time(Time.getCurrentTimezone());

        public SyncHistoryAdapter(Context context, Cursor cursor) {
            super(context, cursor);
            mInflater = (LayoutInflater)
                    context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate(R.layout.sync_event, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            setText(view, R.id.account, cursor.getString(1));
            setText(view, R.id.authority, cursor.getString(2));
            setText(view, R.id.event, Sync.History.EVENTS[cursor.getInt(3)]);
            setText(view, R.id.source, Sync.History.SOURCES[cursor.getInt(4)]);
            mTime.set(cursor.getLong(5));
            String prettyTime = mTime.format("%m/%d %H:%M:%S");
            setText(view, R.id.time, prettyTime);
            setText(view, R.id.mesg, cursor.getString(6));
        }

        private static void setText(View view, int id, String text) {
            if (TextUtils.isEmpty(text)) {
                return;
            }
            TextView textView = (TextView) view.findViewById(id);
            textView.setText(text);
        }
    }
    
    private final static String[] sHistoryProjection =
            new String[] { Sync.History._ID,
                           Sync.History.ACCOUNT, Sync.History.AUTHORITY,
                           Sync.History.EVENT, Sync.History.SOURCE,
                           Sync.History.EVENT_TIME, Sync.History.MESG };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.sync_history_screen);
        ListView items = (ListView) findViewById(R.id.events);
        Cursor cursor = getContentResolver().query(Sync.History.CONTENT_URI,
                            sHistoryProjection,
                            null /* selection */,
                            null /* selectionArgs */,
                            Sync.History.EVENT_TIME + " DESC");
        startManagingCursor(cursor);
        items.setAdapter(new SyncHistoryAdapter(this /* context */, cursor));
    }
}
