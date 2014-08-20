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

import android.content.Context;
import android.content.CursorLoader;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.EventsEntity;

public class DeleteEventsLoader extends CursorLoader {
    private static final Uri CONTENT_URI = CalendarContract.EventsEntity.CONTENT_URI;
    private static final String[] PROJECTION = new String[] {
            Events._ID,
            Events.TITLE,
            Events.ALL_DAY,
            Events.CALENDAR_ID,
            Events.DTSTART,
            Events.DTEND,
            Events.DURATION,
            Events.EVENT_TIMEZONE,
            Events.RRULE,
            Events.RDATE,
            Events.LAST_DATE,
            EventsEntity.DELETED
    };
    private static final String WHERE = CalendarContract.EventsEntity.DELETED + "=0 AND "
            + Calendars.CALENDAR_ACCESS_LEVEL + ">=" + Calendars.CAL_ACCESS_CONTRIBUTOR;
    private static final String EVENTS_SORT_ORDER =
            Events.DTSTART + " ASC, " + Events.TITLE + " ASC ";

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            startLoading();
        }
    };

    public DeleteEventsLoader(Context context) {
        super(context, CONTENT_URI, PROJECTION, WHERE, null, EVENTS_SORT_ORDER);

        // Register the content observer for event change.
        context.getContentResolver().registerContentObserver(CONTENT_URI, true, mObserver);
    }

    @Override
    protected void finalize() throws Throwable {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.finalize();
    }
}
