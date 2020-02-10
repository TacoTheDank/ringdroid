/*
 * Copyright (C) 2008 Google Inc.
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
package com.ringdroid.view.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.ListActivity
import android.app.LoaderManager.LoaderCallbacks
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.database.MergeCursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ImageView
import android.widget.SearchView
import android.widget.SearchView.OnQueryTextListener
import android.widget.SimpleCursorAdapter
import android.widget.Toast
import com.ringdroid.R
import com.ringdroid.view.activity.RingdroidEditActivity.Companion.onAbout
import com.ringdroid.data.model.soundfile.SoundFile
import java.io.File
import java.util.*

/**
 * Main screen that shows up when you launch Ringdroid. Handles selecting
 * an audio file or using an intent to record a new one, and then
 * launches RingdroidEditActivity from here.
 */
class RingdroidSelectActivity : ListActivity(), LoaderCallbacks<Cursor> {
    private var mFilter: SearchView? = null
    private var mAdapter: SimpleCursorAdapter? = null
    private var mWasGetContentIntent = false
    private var mShowAll = false
    private var mInternalCursor: Cursor? = null
    private var mExternalCursor: Cursor? = null
    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(icicle: Bundle) {
        super.onCreate(icicle)

        mShowAll = false

        setupStorageByStatus()

        mWasGetContentIntent = intent.action ==
                Intent.ACTION_GET_CONTENT
        // Inflate our UI from its XML layout description.
        setContentView(R.layout.media_select)

        setupListAdapter()

    }

    private fun setupStorageByStatus() {
        val status = Environment.getExternalStorageState()
        when {
            status == Environment.MEDIA_MOUNTED_READ_ONLY -> {
                showFinalAlert(resources.getText(R.string.sdcard_readonly))
                return
            }
            status == Environment.MEDIA_SHARED -> {
                showFinalAlert(resources.getText(R.string.sdcard_shared))
                return
            }
            status != Environment.MEDIA_MOUNTED -> {
                showFinalAlert(resources.getText(R.string.no_sdcard))
                return
            }
        }
    }

    private fun setupListAdapter() {
        try {
            mAdapter = SimpleCursorAdapter(
                    this,  // Use a template that displays a text view
                    R.layout.media_select_row,
                    null, arrayOf(
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media._ID), intArrayOf(
                    R.id.row_artist,
                    R.id.row_album,
                    R.id.row_title,
                    R.id.row_icon,
                    R.id.row_options_button),
                    0)
            listAdapter = mAdapter
            listView.itemsCanFocus = true
            // Normal click - open the editor
            listView.onItemClickListener = OnItemClickListener { parent, view, position, id -> startRingdroidEditor() }
            mInternalCursor = null
            mExternalCursor = null
            loaderManager.initLoader(INTERNAL_CURSOR_ID, null, this)
            loaderManager.initLoader(EXTERNAL_CURSOR_ID, null, this)
        } catch (e: java.lang.Exception) { // No permission to retrieve audio?
            Log.e("Ringdroid", e.toString())
            // TODO error 1
        }
        mAdapter!!.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, _ ->
            when (view.id) {
                R.id.row_options_button -> { // Get the arrow ImageView and set the onClickListener to open the context menu.
                    val iv = view as ImageView
                    iv.setOnClickListener { v -> openContextMenu(v) }
                    return@ViewBinder true
                }
                R.id.row_icon -> {
                    setSoundIconFromCursor(view as ImageView, cursor)
                    return@ViewBinder true
                }
                else -> false
            }
        }
        // Long-press opens a context menu
        registerForContextMenu(listView)
    }

    private fun setSoundIconFromCursor(view: ImageView, cursor: Cursor) {
        when {
            0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_RINGTONE)) -> {
                view.setImageResource(R.drawable.type_ringtone)
                (view.parent as View).setBackgroundColor(
                        resources.getColor(R.color.type_bkgnd_ringtone))
            }
            0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_ALARM)) -> {
                view.setImageResource(R.drawable.type_alarm)
                (view.parent as View).setBackgroundColor(
                        resources.getColor(R.color.type_bkgnd_alarm))
            }
            0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_NOTIFICATION)) -> {
                view.setImageResource(R.drawable.type_notification)
                (view.parent as View).setBackgroundColor(
                        resources.getColor(R.color.type_bkgnd_notification))
            }
            0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_MUSIC)) -> {
                view.setImageResource(R.drawable.type_music)
                (view.parent as View).setBackgroundColor(
                        resources.getColor(R.color.type_bkgnd_music))
            }
        }
        val filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        if (!SoundFile.isFilenameSupported(filename)) {
            (view.parent as View).setBackgroundColor(
                    resources.getColor(R.color.type_bkgnd_unsupported))
        }
    }

    /** Called with an Activity we started with an Intent returns.  */
    @Override
    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  dataIntent: Intent) {
        if (requestCode != REQUEST_CODE_EDIT) {
            return
        }
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        setResult(Activity.RESULT_OK, dataIntent)
        //finish();  // TODO(nfaralli): why would we want to quit the app here?
    }

    @Override
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.select_options, menu)
        mFilter = menu.findItem(R.id.action_search_filter).actionView as SearchView
        if (mFilter != null) {
            mFilter!!.setOnQueryTextListener(object : OnQueryTextListener {
                override fun onQueryTextChange(newText: String): Boolean {
                    refreshListView()
                    return true
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    refreshListView()
                    return true
                }
            })
        }
        return true
    }

    @Override
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_about).isVisible = true
        menu.findItem(R.id.action_record).isVisible = true
        // TODO(nfaralli): do we really need a "Show all audio" item now?
        menu.findItem(R.id.action_show_all_audio).isVisible = true
        menu.findItem(R.id.action_show_all_audio).isEnabled = !mShowAll
        return true
    }

    @Override
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_about -> {
            onAbout(this)
            true
        }
        R.id.action_record -> {
            onRecord()
            true
        }
        R.id.action_show_all_audio -> {
            mShowAll = true
            refreshListView()
            true
        }
        else -> false
    }

    @Override
    override fun onCreateContextMenu(menu: ContextMenu,
                                     v: View,
                                     menuInfo: ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val c = mAdapter!!.cursor
        val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
        menu.setHeaderTitle(title)
        menu.add(0, CMD_EDIT, 0, R.string.context_menu_edit)
        menu.add(0, CMD_DELETE, 0, R.string.context_menu_delete)
        // Add items to the context menu item based on file type
        if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            menu.add(0, CMD_SET_AS_DEFAULT, 0, R.string.context_menu_default_ringtone)
            menu.add(0, CMD_SET_AS_CONTACT, 0, R.string.context_menu_contact)
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION))) {
            menu.add(0, CMD_SET_AS_DEFAULT, 0, R.string.context_menu_default_notification)
        }
    }

    @Override
    override fun onContextItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        CMD_EDIT -> {
            startRingdroidEditor()
            true
        }
        CMD_DELETE -> {
            confirmDelete()
            true
        }
        CMD_SET_AS_DEFAULT -> {
            setAsDefaultRingtoneOrNotification()
            true
        }
        CMD_SET_AS_CONTACT -> chooseContactForRingtone(item)
        else -> super.onContextItemSelected(item)
    }

    private fun setAsDefaultRingtoneOrNotification() {
        val c = mAdapter!!.cursor
        // If the item is a ringtone then set the default ringtone,
// otherwise it has to be a notification so set the default notification sound
        when {
            0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)) -> {
                RingtoneManager.setActualDefaultRingtoneUri(
                        this@RingdroidSelectActivity,
                        RingtoneManager.TYPE_RINGTONE,
                        uri)
                Toast.makeText(
                        this@RingdroidSelectActivity,
                        R.string.default_ringtone_success_message,
                        Toast.LENGTH_SHORT)
                        .show()
            }
            else -> {
                RingtoneManager.setActualDefaultRingtoneUri(
                        this@RingdroidSelectActivity,
                        RingtoneManager.TYPE_NOTIFICATION,
                        uri)
                Toast.makeText(
                        this@RingdroidSelectActivity,
                        R.string.default_notification_success_message,
                        Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    private fun getUriIndex(c: Cursor): Int {
        var uriIndex: Int
        val columnNames = arrayOf(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI.toString(),
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()
        )
        for (columnName in Arrays.asList(*columnNames)) {
            uriIndex = c.getColumnIndex(columnName)
            if (uriIndex >= 0) {
                return uriIndex
            }
            // On some phones and/or Android versions, the column name includes the double quotes.
            uriIndex = c.getColumnIndex("\"" + columnName + "\"")
            if (uriIndex >= 0) {
                return uriIndex
            }
        }
        return -1
    }

    //Get the uri of the item that is in the row
    private val uri: Uri?
        get() { //Get the uri of the item that is in the row
            val c = mAdapter!!.cursor
            val uriIndex = getUriIndex(c)
            if (uriIndex == -1) {
                return null
            }
            val itemUri = c.getString(uriIndex) + "/" +
                    c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            return Uri.parse(itemUri)
        }

    private fun chooseContactForRingtone(item: MenuItem): Boolean {
        try { //Go to the choose contact activity
            val intent = Intent(Intent.ACTION_EDIT, uri)
            intent.setClassName(
                    "com.ringdroid",
                    "com.ringdroid.view.activity.ChooseContactActivity")
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_CONTACT)
        } catch (e: Exception) {
            Log.e("Ringdroid", "Couldn't open Choose Contact window")
        }
        return true
    }

    private fun confirmDelete() { // See if the selected list item was created by Ringdroid to
// determine which alert message to show
        val c = mAdapter!!.cursor
        val artist = c.getString(c.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST))
        val ringdroidArtist = resources.getText(R.string.artist_name)
        val message: CharSequence
        message = when (artist) {
            ringdroidArtist -> {
                resources.getText(
                        R.string.confirm_delete_ringdroid)
            }
            else -> {
                resources.getText(
                        R.string.confirm_delete_non_ringdroid)
            }
        }

        val title: CharSequence
        title = when {
            0 != c.getInt(c.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_RINGTONE)) -> {
                resources.getText(R.string.delete_ringtone)
            }
            0 != c.getInt(c.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_ALARM)) -> {
                resources.getText(R.string.delete_alarm)
            }
            0 != c.getInt(c.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_NOTIFICATION)) -> {
                resources.getText(R.string.delete_notification)
            }
            0 != c.getInt(c.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.IS_MUSIC)) -> {
                resources.getText(R.string.delete_music)
            }
            else -> {
                resources.getText(R.string.delete_audio)
            }
        }
        AlertDialog.Builder(this@RingdroidSelectActivity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        R.string.delete_ok_button
                ) { _, _ -> onDelete() }
                .setNegativeButton(
                        R.string.delete_cancel_button
                ) { _, _ -> }
                .setCancelable(true)
                .show()
    }

    private fun onDelete() {
        val c = mAdapter!!.cursor
        val dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val filename = c.getString(dataIndex)
        val uriIndex = getUriIndex(c)
        if (uriIndex == -1) {
            showFinalAlert(resources.getText(R.string.delete_failed))
            return
        }
        if (!File(filename).delete()) {
            showFinalAlert(resources.getText(R.string.delete_failed))
        }
        val itemUri = c.getString(uriIndex) + "/" +
                c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        contentResolver.delete(Uri.parse(itemUri), null, null)
    }

    private fun showFinalAlert(message: CharSequence) {
        AlertDialog.Builder(this@RingdroidSelectActivity)
                .setTitle(resources.getText(R.string.alert_title_failure))
                .setMessage(message)
                .setPositiveButton(
                        R.string.alert_ok_button
                ) { dialog, whichButton -> finish() }
                .setCancelable(false)
                .show()
    }

    private fun onRecord() {
        try {
            val intent = Intent(Intent.ACTION_EDIT, Uri.parse("record"))
            intent.putExtra("was_get_content_intent", mWasGetContentIntent)
            intent.setClassName("com.ringdroid", "com.ringdroid.view.activity.RingdroidEditActivity")
            startActivityForResult(intent, REQUEST_CODE_EDIT)
        } catch (e: Exception) {
            Log.e("Ringdroid", "Couldn't start editor")
        }
    }

    private fun startRingdroidEditor() {
        val c = mAdapter!!.cursor
        val dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val filename = c.getString(dataIndex)
        try {
            val intent = Intent(Intent.ACTION_EDIT, Uri.parse(filename))
            intent.putExtra("was_get_content_intent", mWasGetContentIntent)
            intent.setClassName("com.ringdroid", "com.ringdroid.view.activity.RingdroidEditActivity")
            startActivityForResult(intent, REQUEST_CODE_EDIT)
        } catch (e: Exception) {
            Log.e("Ringdroid", "Couldn't start editor")
        }
    }

    private fun refreshListView() {
        mInternalCursor = null
        mExternalCursor = null
        val args = Bundle()
        args.putString("filter", mFilter!!.query.toString())
        loaderManager.restartLoader(INTERNAL_CURSOR_ID, args, this)
        loaderManager.restartLoader(EXTERNAL_CURSOR_ID, args, this)
    }

    /* Implementation of LoaderCallbacks.onCreateLoader */
    @Override
    override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor> {
        val selectionArgsList = ArrayList<String>()
        var selection: String
        val baseUri: Uri
        val projection: Array<String>
        when (id) {
            INTERNAL_CURSOR_ID -> {
                baseUri = MediaStore.Audio.Media.INTERNAL_CONTENT_URI
                projection = INTERNAL_COLUMNS
            }
            EXTERNAL_CURSOR_ID -> {
                baseUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                projection = EXTERNAL_COLUMNS
            }
            else -> return null
        }
        if (mShowAll) {
            selection = "(_DATA LIKE ?)"
            selectionArgsList.add("%")
        } else {
            selection = "("
            for (extension in SoundFile.supportedExtensions) {
                selectionArgsList.add("%.$extension")
                if (selection.length > 1) {
                    selection += " OR "
                }
                selection += "(_DATA LIKE ?)"
            }
            selection += ")"
            selection = "($selection) AND (_DATA NOT LIKE ?)"
            selectionArgsList.add("%espeak-data/scratch%")
        }
        var filter = args?.getString("filter")
        if (filter != null && filter.length > 0) {
            filter = "%$filter%"
            selection = "(" + selection + " AND " +
                    "((TITLE LIKE ?) OR (ARTIST LIKE ?) OR (ALBUM LIKE ?)))"
            selectionArgsList.add(filter)
            selectionArgsList.add(filter)
            selectionArgsList.add(filter)
        }
        val selectionArgs = selectionArgsList.toTypedArray()
        return CursorLoader(
                this,
                baseUri,
                projection,
                selection,
                selectionArgs,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER
        )
    }

    /* Implementation of LoaderCallbacks.onLoadFinished */
    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        when (loader.id) {
            INTERNAL_CURSOR_ID -> mInternalCursor = data
            EXTERNAL_CURSOR_ID -> mExternalCursor = data
            else -> return
        }
        // TODO: should I use a mutex/synchronized block here?
        if (mInternalCursor != null && mExternalCursor != null) {
            val mergeCursor: Cursor = MergeCursor(arrayOf(mInternalCursor!!, mExternalCursor!!))
            mAdapter!!.swapCursor(mergeCursor)
        }
    }

    /* Implementation of LoaderCallbacks.onLoaderReset */
    override fun onLoaderReset(loader: Loader<Cursor>) { // This is called when the last Cursor provided to onLoadFinished()
// above is about to be closed.  We need to make sure we are no
// longer using it.
        mAdapter!!.swapCursor(null)
    }

    companion object {
        // Result codes
        private const val REQUEST_CODE_EDIT = 1
        private const val REQUEST_CODE_CHOOSE_CONTACT = 2
        // Context menu
        private const val CMD_EDIT = 4
        private const val CMD_DELETE = 5
        private const val CMD_SET_AS_DEFAULT = 6
        private const val CMD_SET_AS_CONTACT = 7
        private val INTERNAL_COLUMNS = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.IS_NOTIFICATION,
                MediaStore.Audio.Media.IS_MUSIC,
                "\"" + MediaStore.Audio.Media.INTERNAL_CONTENT_URI + "\""
        )
        private val EXTERNAL_COLUMNS = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.IS_NOTIFICATION,
                MediaStore.Audio.Media.IS_MUSIC,
                "\"" + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "\""
        )
        private const val INTERNAL_CURSOR_ID = 0
        private const val EXTERNAL_CURSOR_ID = 1
    }
}