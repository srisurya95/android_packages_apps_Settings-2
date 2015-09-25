/*
 * Copyright (C) 2008 Google Inc.
 * The code is modified to work in Metallium - OS project.
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

package com.android.settings.metallium.ringtones;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.MergeCursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.metallium.ringtones.SoundFile;

/**
 * Main screen that shows up when you launch. Handles selecting
 * an audio file or using an intent to record a new one, and then
 * launches Activity from here.
 */
public class RingtoneMakerSelectActivity
    extends ListActivity
    implements LoaderManager.LoaderCallbacks<Cursor>
{
    private SimpleCursorAdapter mAdapter;
    private boolean mWasGetContentIntent;
    private boolean mShowAll;
    private Cursor mInternalCursor;
    private Cursor mExternalCursor;

    // Result codes
    private static final int REQUEST_CODE_EDIT = 1;
    private static final int REQUEST_CODE_CHOOSE_CONTACT = 2;

    // Context menu
    private static final int CMD_EDIT = 4;
    private static final int CMD_DELETE = 5;
    private static final int CMD_SET_AS_DEFAULT = 6;
    private static final int CMD_SET_AS_CONTACT = 7;


    public RingtoneMakerSelectActivity() {
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mShowAll = false;

        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            showFinalAlert(getResources().getText(R.string.sdcard_readonly));
            return;
        }
        if (status.equals(Environment.MEDIA_SHARED)) {
            showFinalAlert(getResources().getText(R.string.sdcard_shared));
            return;
        }
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            showFinalAlert(getResources().getText(R.string.no_sdcard));
            return;
        }

        Intent intent = getIntent();
        mWasGetContentIntent = intent.getAction().equals(
                Intent.ACTION_GET_CONTENT);

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.media_select);

        try {
            mAdapter = new SimpleCursorAdapter(
                    this,
                    // Use a template that displays a text view
                    R.layout.media_select_row,
                    null,
                    // Map from database columns...
                    new String[] {
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media._ID},
                        // To widget ids in the row layout...
                    new int[] {
                        R.id.row_title},
                    0);

            setListAdapter(mAdapter);

            getListView().setItemsCanFocus(true);

            // Normal click - open the editor
            getListView().setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent,
                        View view,
                        int position,
                        long id) {
                    startEditor();
                }
            });

            mInternalCursor = null;
            mExternalCursor = null;
            getLoaderManager().initLoader(INTERNAL_CURSOR_ID,  null, this);
            getLoaderManager().initLoader(EXTERNAL_CURSOR_ID,  null, this);

        } catch (SecurityException e) {
            // No permission to retrieve audio?
            Log.e("Settings", e.toString());

            // TODO error 1
        } catch (IllegalArgumentException e) {
            // No permission to retrieve audio?
            Log.e("Settings", e.toString());
        }

        // Long-press opens a context menu
        registerForContextMenu(getListView());
    }

    private void setSoundIconFromCursor(Cursor cursor) {
        if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_RINGTONE))) {
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_ALARM))) {
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_NOTIFICATION))) {
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_MUSIC))) {
        }

        String filename = cursor.getString(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DATA));
    }

    /** Called with an Activity we started with an Intent returns. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent dataIntent) {
        if (requestCode != REQUEST_CODE_EDIT) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        setResult(RESULT_OK, dataIntent);
        //finish();  // TODO(nfaralli): why would we want to quit the app here?
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.select_options, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        menu.findItem(R.id.action_show_all_audio).setVisible(true);
        menu.findItem(R.id.action_show_all_audio).setEnabled(!mShowAll);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_show_all_audio:
            mShowAll = true;
            refreshListView();
            return true;
        default:
            return false;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        Cursor c = mAdapter.getCursor();
        String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));

        menu.setHeaderTitle(title);

        menu.add(0, CMD_EDIT, 0, R.string.context_menu_edit);
        menu.add(0, CMD_DELETE, 0, R.string.context_menu_delete);

        // Add items to the context menu item based on file type
        if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            menu.add(0, CMD_SET_AS_DEFAULT, 0, R.string.context_menu_default_ringtone);
            menu.add(0, CMD_SET_AS_CONTACT, 0, R.string.context_menu_contact);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION))) {
            menu.add(0, CMD_SET_AS_DEFAULT, 0, R.string.context_menu_default_notification);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case CMD_EDIT:
            startEditor();
            return true;
        case CMD_DELETE:
            confirmDelete();
            return true;
        case CMD_SET_AS_DEFAULT:
            setAsDefaultRingtoneOrNotification();
            return true;
        case CMD_SET_AS_CONTACT:
            return chooseContactForRingtone(item);
        default:
            return super.onContextItemSelected(item);
        }
    }

    private void setAsDefaultRingtoneOrNotification(){
        Cursor c = mAdapter.getCursor();

        // If the item is a ringtone then set the default ringtone,
        // otherwise it has to be a notification so set the default notification sound
        if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))){
            RingtoneManager.setActualDefaultRingtoneUri(
                    RingtoneMakerSelectActivity.this,
                    RingtoneManager.TYPE_RINGTONE,
                    getUri());
            Toast.makeText(
                    RingtoneMakerSelectActivity.this,
                    R.string.default_ringtone_success_message,
                    Toast.LENGTH_SHORT)
                    .show();
        } else {
            RingtoneManager.setActualDefaultRingtoneUri(
                    RingtoneMakerSelectActivity.this,
                    RingtoneManager.TYPE_NOTIFICATION,
                    getUri());
            Toast.makeText(
                    RingtoneMakerSelectActivity.this,
                    R.string.default_notification_success_message,
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private int getUriIndex(Cursor c) {
        int uriIndex;
        String[] columnNames = {
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI.toString(),
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()
        };

        for (String columnName : Arrays.asList(columnNames)) {
            uriIndex = c.getColumnIndex(columnName);
            if (uriIndex >= 0) {
                return uriIndex;
            }
            // On some phones and/or Android versions, the column name includes the double quotes.
            uriIndex = c.getColumnIndex("\"" + columnName + "\"");
            if (uriIndex >= 0) {
                return uriIndex;
            }
        }
        return -1;
    }

    private Uri getUri(){
        //Get the uri of the item that is in the row
        Cursor c = mAdapter.getCursor();
        int uriIndex = getUriIndex(c);
        if (uriIndex == -1) {
            return null;
        }
        String itemUri = c.getString(uriIndex) + "/" +
        c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
        return (Uri.parse(itemUri));
    }

    private boolean chooseContactForRingtone(MenuItem item){
        try {
            //Go to the choose contact activity
            Intent intent = new Intent(Intent.ACTION_EDIT, getUri());
            intent.setClassName(
                    "com.android.settings",
            "com.android.settings.metallium.ringtones.ChooseContactActivity");
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_CONTACT);
        } catch (Exception e) {
            Log.e("Settings", "Couldn't open Choose Contact window");
        }
        return true;
    }

    private void confirmDelete() {
        // See if the selected list item was created to
        // determine which alert message to show
        Cursor c = mAdapter.getCursor();
        String artist = c.getString(c.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST));

        CharSequence ringtoneMakerArtist =
            getResources().getText(R.string.artist_name);

        CharSequence message;
        if (artist.equals(ringtoneMakerArtist)) {
            message = getResources().getText(
                    R.string.confirm_delete);
        } else {
            message = getResources().getText(
                    R.string.confirm_delete_non);
        }

        CharSequence title;
        if (0 != c.getInt(c.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_RINGTONE))) {
            title = getResources().getText(R.string.delete_ringtone);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_ALARM))) {
            title = getResources().getText(R.string.delete_alarm);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_NOTIFICATION))) {
            title = getResources().getText(R.string.delete_notification);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_MUSIC))) {
            title = getResources().getText(R.string.delete_music);
        } else {
            title = getResources().getText(R.string.delete_audio);
        }

        new AlertDialog.Builder(RingtoneMakerSelectActivity.this)
            .setTitle(title)
            .setPositiveButton(
                R.string.delete_ok_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        onDelete();
                    }
                })
            .setNegativeButton(
                R.string.delete_cancel_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                    }
                })
            .setCancelable(true)
            .show();
    }

    private void onDelete() {
        Cursor c = mAdapter.getCursor();
        int dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        String filename = c.getString(dataIndex);

        int uriIndex = getUriIndex(c);
        if (uriIndex == -1) {
            showFinalAlert(getResources().getText(R.string.delete_failed));
            return;
        }

        if (!new File(filename).delete()) {
            showFinalAlert(getResources().getText(R.string.delete_failed));
        }

        String itemUri = c.getString(uriIndex) + "/" +
        c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
        getContentResolver().delete(Uri.parse(itemUri), null, null);
    }

    private void showFinalAlert(CharSequence message) {
        new AlertDialog.Builder(RingtoneMakerSelectActivity.this)
        .setTitle(getResources().getText(R.string.alert_title_failure))
        .setMessage(message)
        .setPositiveButton(
                R.string.alert_ok_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void startEditor() {
        Cursor c = mAdapter.getCursor();
        int dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        String filename = c.getString(dataIndex);
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT, Uri.parse(filename));
            intent.putExtra("was_get_content_intent", mWasGetContentIntent);
            intent.setClassName( "com.android.settings", "com.android.settings.metallium.ringtones.RingtoneMakerEditActivity");
            startActivityForResult(intent, REQUEST_CODE_EDIT);
        } catch (Exception e) {
            Log.e("Settings", "Couldn't start editor");
        }
    }

    private void refreshListView() {
        mInternalCursor = null;
        mExternalCursor = null;
        Bundle args = new Bundle();
        getLoaderManager().restartLoader(INTERNAL_CURSOR_ID,  args, this);
        getLoaderManager().restartLoader(EXTERNAL_CURSOR_ID,  args, this);
    }

    private static final String[] INTERNAL_COLUMNS = new String[] {
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.IS_RINGTONE,
        MediaStore.Audio.Media.IS_ALARM,
        MediaStore.Audio.Media.IS_NOTIFICATION,
        MediaStore.Audio.Media.IS_MUSIC,
        "\"" + MediaStore.Audio.Media.INTERNAL_CONTENT_URI + "\""
    };

    private static final String[] EXTERNAL_COLUMNS = new String[] {
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.IS_RINGTONE,
        MediaStore.Audio.Media.IS_ALARM,
        MediaStore.Audio.Media.IS_NOTIFICATION,
        MediaStore.Audio.Media.IS_MUSIC,
        "\"" + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "\""
    };

    private static final int INTERNAL_CURSOR_ID = 0;
    private static final int EXTERNAL_CURSOR_ID = 1;

    /* Implementation of LoaderCallbacks.onCreateLoader */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        ArrayList<String> selectionArgsList = new ArrayList<String>();
        String selection;
        Uri baseUri;
        String[] projection;

        switch (id) {
        case INTERNAL_CURSOR_ID:
            baseUri = MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
            projection = INTERNAL_COLUMNS;
            break;
        case EXTERNAL_CURSOR_ID:
            baseUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            projection = EXTERNAL_COLUMNS;
            break;
        default:
            return null;
        }

        if (mShowAll) {
            selection = "(_DATA LIKE ?)";
            selectionArgsList.add("%");
        } else {
            selection = "(";
            for (String extension : SoundFile.getSupportedExtensions()) {
                selectionArgsList.add("%." + extension);
                if (selection.length() > 1) {
                    selection += " OR ";
                }
                selection += "(_DATA LIKE ?)";
            }
            selection += ")";

            selection = "(" + selection + ") AND (_DATA NOT LIKE ?)";
            selectionArgsList.add("%espeak-data/scratch%");
        }

        String filter = args != null ? args.getString("filter") : null;
        if (filter != null && filter.length() > 0) {
            filter = "%" + filter + "%";
            selection =
                "(" + selection + " AND " +
                "((TITLE LIKE ?) OR (ARTIST LIKE ?) OR (ALBUM LIKE ?)))";
            selectionArgsList.add(filter);
            selectionArgsList.add(filter);
            selectionArgsList.add(filter);
        }

        String[] selectionArgs =
                selectionArgsList.toArray(new String[selectionArgsList.size()]);
        return new CursorLoader(
                this,
                baseUri,
                projection,
                selection,
                selectionArgs,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER
                );
    }

    /* Implementation of LoaderCallbacks.onLoadFinished */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
        case INTERNAL_CURSOR_ID:
            mInternalCursor = data;
            break;
        case EXTERNAL_CURSOR_ID:
            mExternalCursor = data;
            break;
        default:
            return;
        }
        // TODO: should I use a mutex/synchronized block here?
        if (mInternalCursor != null && mExternalCursor != null) {
            Cursor mergeCursor = new MergeCursor(new Cursor[] {mInternalCursor, mExternalCursor});
            mAdapter.swapCursor(mergeCursor);
        }
    }

    /* Implementation of LoaderCallbacks.onLoaderReset */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }
}
