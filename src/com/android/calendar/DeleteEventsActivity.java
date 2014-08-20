/**
 * Copyright (C) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.calendar;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeleteEventsActivity extends ListActivity {
    private static final String TAG = "DeleteEvents";
    private static final boolean DEBUG = false;

    private static final String[] CALENDAR_PROJECTION = new String[] {
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
    };

    private ActionBarAdapter mActionBarAdapter;

    private ListView mListView;
    private EventListAdapter mAdapter;
    private AsyncQueryService mService;

    private TextView mHeaderTextView;

    private Map<Long, Long> mSelectedMap = new HashMap<Long, Long>();
    private Map<Long, String> mCalendarsMap = new HashMap<Long, String>();
    private List<Long> mEventList = new ArrayList<Long>();

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mService.startQuery(mService.getNextToken(), null,
                    CalendarContract.Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, null);
        }
    };

    private DeleteEventsLoader mDeleteEventsLoader = null;
    private Loader.OnLoadCompleteListener<Cursor> mDeleteEventsListener =
            new Loader.OnLoadCompleteListener<Cursor>() {
        @Override
        public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null) return;

            if (DEBUG) Log.d(TAG, "onLoadFinished, Events' num: " + cursor.getCount());

            // Clear the event list.
            mEventList.clear();

            // Rebuild the event list.
            if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                do {
                    mEventList.add(cursor.getLong(cursor.getColumnIndex(Events._ID)));
                } while (cursor.moveToNext());
            }

            mHeaderTextView.setText(mEventList.isEmpty() ? R.string.header_label_no_events
                    : R.string.header_label_all_events);
            mAdapter.changeCursor(cursor);

            updateTitle();
        }
    };

    private Handler mActionHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ActionBarAdapter.ACTION_MODE_SELECT_ALL:
                    selectAll();
                    break;
                case ActionBarAdapter.ACTION_MODE_DESELECT_ALL:
                    selectNone();
                    break;
                default:
                    Log.w(TAG, "Do not support this action.");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the action bar.
        createActionBar();

        mListView = getListView();
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        mHeaderTextView = new TextView(this);
        mHeaderTextView.setPadding(16, 8, 8, 8);
        mHeaderTextView.setTextAppearance(this, android.R.style.TextAppearance_Medium);
        mHeaderTextView.setText(R.string.header_label_all_events);
        mListView.addHeaderView(mHeaderTextView, null, false);

        mAdapter = new EventListAdapter(this, R.layout.event_list_item);
        mListView.setAdapter(mAdapter);

        mService = new AsyncQueryService(this) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                if (cursor == null) {
                    return;
                }

                if (DEBUG) Log.d(TAG, "onQueryComplete, num Calendars: " + cursor.getCount());
                mCalendarsMap.clear();
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    if (DEBUG) Log.d(TAG, "Cal ID: "
                            + cursor.getString(cursor.getColumnIndex(Calendars._ID))
                            + ", DISPLAY_NAME: "
                            + cursor.getString(cursor
                                    .getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)));
                    mCalendarsMap.put(cursor.getLong(cursor.getColumnIndex(Calendars._ID)), cursor
                            .getString(cursor.getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)));
                    mAdapter.notifyDataSetChanged();
                }
                cursor.close();
            }
        };

        mService.startQuery(mService.getNextToken(), null,
                CalendarContract.Calendars.CONTENT_URI, CALENDAR_PROJECTION,
                null, null, null);

        if (mDeleteEventsLoader == null) {
            mDeleteEventsLoader = new DeleteEventsLoader(getBaseContext());
            mDeleteEventsLoader.registerListener(0, mDeleteEventsListener);
            mDeleteEventsLoader.startLoading();
        }

        updateTitle();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSelectedMap.isEmpty() && mListView.getCheckedItemCount() > 0) {
            long[] checkedItem = mListView.getCheckedItemIds();
            for (int i = 0; i < checkedItem.length; i++) {
                if (DEBUG) Log.v(TAG, "onResume: " + checkedItem[i]);
                mSelectedMap.put(checkedItem[i], checkedItem[i]);
            }
            mAdapter.notifyDataSetChanged();
            updateTitle();
        }

        getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI,
                true, mObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    protected void onDestroy() {
        if (mDeleteEventsLoader != null && mDeleteEventsListener != null) {
            mDeleteEventsLoader.unregisterListener(mDeleteEventsListener);
            mDeleteEventsLoader.reset();
            mDeleteEventsLoader = null;
            mDeleteEventsListener = null;
        }

        if (mAdapter != null) {
            mAdapter.changeCursor(null);
            mAdapter = null;
        }

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.delete_events_title_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                if (DEBUG) Log.d(TAG, "Action: Delete");
                if (mSelectedMap.size() > 0) {
                    DeleteDialogFragment delete = DeleteDialogFragment.newInstance();
                    delete.show(getFragmentManager(), "dialog");
                } else {
                    Toast.makeText(this, R.string.toast_no_events_selected, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        if (DEBUG) Log.i(TAG, "onListItemClick: position: " + position + " Id: " + id);

        CheckBox checkbox = (CheckBox) v.findViewById(R.id.checkbox);
        checkbox.toggle();

        if (checkbox.isChecked()) {
            mSelectedMap.put(id, id);
        } else {
            mSelectedMap.remove(id);
        }

        updateTitle();
    }

    private void createActionBar() {
        ActionBar actionBar = getActionBar();

        mActionBarAdapter = new ActionBarAdapter(this, ActionBarAdapter.ACTION_MODE_SELECT_ALL);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setListNavigationCallbacks(mActionBarAdapter, null);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_HOME_AS_UP);
    }

    private void updateTitle() {
        if (mActionBarAdapter == null) {
            Log.w(TAG, "update the title, but mActionBarAdapter is null.");
            return;
        }
        // Update the selected number.
        mActionBarAdapter.setSelectedNumber(mSelectedMap.size());

        // Update the action mode.
        int actionMode = ActionBarAdapter.ACTION_MODE_SELECT_ALL;
        if (mEventList != null
                && mSelectedMap != null
                && mEventList.size() > 0
                && mEventList.size() == mSelectedMap.size()) {
            actionMode = ActionBarAdapter.ACTION_MODE_DESELECT_ALL;
        }
        mActionBarAdapter.setActionMode(actionMode, !mEventList.isEmpty());

        // Notify the action bar update.
        mActionBarAdapter.notifyDataSetChanged();
    }

    private String getEventTimeString(long start, long end, boolean allDay) {
        String eventTimeString = null;

        if (allDay) {
            eventTimeString = DateUtils.formatDateTime(this, start,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR |
                            DateUtils.FORMAT_ABBREV_ALL);
        } else {
            eventTimeString = DateUtils.formatDateRange(this, start, end,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                            DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_ALL);
        }

        return eventTimeString;
    }

    private void selectAll() {
        if (DEBUG) Log.i(TAG, "Select all items.");

        mSelectedMap.clear();
        for (Long event : mEventList) {
            mSelectedMap.put(event, event);
        }

        for (int i = 0; i < mListView.getCount(); i++) {
            if (mSelectedMap.containsKey(mListView.getItemIdAtPosition(i))) {
                mListView.setItemChecked(i, true);
            }
        }

        updateTitle();
        mAdapter.notifyDataSetChanged();
    }

    private void selectNone() {
        if (DEBUG) Log.i(TAG, "De-select all items.");

        mSelectedMap.clear();
        for (int i = 0; i < mListView.getCount(); i++) {
            mListView.setItemChecked(i, false);
        }

        updateTitle();
        mAdapter.notifyDataSetChanged();
    }

    public void onPositiveButtonSelected() {
        ArrayList<Long> selectedEventList = new ArrayList<Long>(mSelectedMap.values());

        StringBuilder where = new StringBuilder();
        for (int i = 0; i < selectedEventList.size(); i++) {
            where.append("_ID=" + selectedEventList.get(i));
            if (i < selectedEventList.size() - 1) {
                where.append(" OR ");
            }
        }

        if (DEBUG) Log.d(TAG, "Deleting: where[" + where + "]");
        mService.startDelete(mService.getNextToken(), null, CalendarContract.Events.CONTENT_URI,
                where.toString(), null, 0);
        mSelectedMap.clear();
    }

    public void onNegativeButtonSelected() {
        // Do nothing.
    }

    private class ActionBarAdapter extends BaseAdapter {
        private static final int ACTION_MODE_SELECT_ALL = 1;
        private static final int ACTION_MODE_DESELECT_ALL = 2;

        private boolean mActionEnabled;

        private int mActionMode;
        private int mSelectedNumber;

        public ActionBarAdapter(Context context, int actionMode) {
            super();
            mActionMode = actionMode;
        }

        public void setActionMode(int actionMode, boolean enabled) {
            mActionMode = actionMode;
            mActionEnabled = enabled;
        }

        public void setSelectedNumber(int selectedNumber) {
            mSelectedNumber = selectedNumber;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public Object getItem(int position) {
            return mActionMode;
        }

        @Override
        public long getItemId(int position) {
            return mActionMode;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = null;

            if (convertView == null) {
                v = getLayoutInflater().inflate(android.R.layout.simple_dropdown_item_1line, null);
            } else {
                v = convertView;
            }

            TextView text = (TextView) v.findViewById(android.R.id.text1);
            text.setText(mSelectedNumber + " " + getString(R.string.title_selected));

            return v;
        }

        @Override
        public View getDropDownView(final int position, View convertView, ViewGroup parent) {
            final View v;

            if (convertView == null) {
                v = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
            } else {
                v = convertView;
            }

            TextView text = (TextView) v.findViewById(android.R.id.text1);
            String label = null;
            switch (mActionMode) {
                case ACTION_MODE_SELECT_ALL:
                    label = getString(R.string.action_select_all);
                    break;
                case ACTION_MODE_DESELECT_ALL:
                    label = getString(R.string.action_select_none);
                    break;
                default:
                    Log.w(TAG, "Do not support this action.");
                    break;
            }
            text.setText(label);
            text.setEnabled(mActionEnabled);
            if (mActionHandler != null) {
                text.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (DEBUG) Log.i(TAG, "onTouch, event = " + event.getAction());
                        // If we handle the action immediately, we will found the pop-up
                        // will be changed before the pop-up dismiss. So delay the message
                        // 200ms as the pop-up already dismiss. Of cause, if the user do not
                        // up immediately, it will also changed before the pop-up dismiss.
                        mActionHandler.sendEmptyMessageDelayed(mActionMode, 200);
                        return false;
                    }
                });
            }

            return v;
        }
    }

    private class EventListAdapter extends ResourceCursorAdapter {
        public EventListAdapter(Context context, int layout) {
            super(context, layout, null);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            boolean selected = mSelectedMap.containsKey(
                    cursor.getLong(cursor.getColumnIndex(Events._ID)));
            final View parent = view.findViewById(R.id.parent_view);
            parent.setSelected(selected);

            final CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
            checkbox.setChecked(selected);

            final TextView eventTitle = (TextView) view.findViewById(R.id.event_title);
            eventTitle.setText(cursor.getString(cursor.getColumnIndex(Events.TITLE)));

            final TextView eventTime = (TextView) view.findViewById(R.id.event_time);
            long start = cursor.getLong(cursor.getColumnIndex(Events.DTSTART));
            long end = cursor.getLong(cursor.getColumnIndex(Events.DTEND));

            // if DTEND invalid, check for duration
            if (end == 0) {
                String durationStr = cursor.getString(cursor.getColumnIndex(Events.DURATION));
                if (durationStr != null) {
                    Duration duration = new Duration();

                    try {
                        duration.parse(durationStr);
                        end = start + duration.getMillis();
                    } catch (DateException e) {
                        Log.w(TAG, e.getLocalizedMessage());
                    }
                }
            }
            boolean allDay = cursor.getInt(cursor.getColumnIndex(Events.ALL_DAY)) != 0;
            eventTime.setText(getEventTimeString(start, end, allDay));

            final TextView calAccount = (TextView) view.findViewById(R.id.calendar_account);
            calAccount.setText(mCalendarsMap.get(
                    cursor.getLong(cursor.getColumnIndex(Events.CALENDAR_ID))));
        }
    }

    public static class DeleteDialogFragment extends DialogFragment {

        public interface DeleteDialogListener {
            public void onPositiveButtonSelected();
            public void onNegativeButtonSelected();
        }

        public static DeleteDialogFragment newInstance() {
            DeleteDialogFragment dlgFrg = new DeleteDialogFragment();
            return dlgFrg;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (DEBUG) Log.d(TAG, "onCreateDialog");

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            builder.setMessage(R.string.dialog_delete_message);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ((DeleteEventsActivity) getActivity()).onPositiveButtonSelected();
                }
            });
            builder.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ((DeleteEventsActivity) getActivity()).onNegativeButtonSelected();
                }
            });

            return builder.create();
        }
    }
}
