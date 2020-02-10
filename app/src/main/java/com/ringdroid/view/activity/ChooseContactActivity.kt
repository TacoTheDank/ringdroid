/*
 * Copyright (C) 2009 Google Inc.
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

import android.app.ListActivity
import android.app.LoaderManager.LoaderCallbacks
import android.content.ContentValues
import android.content.CursorLoader
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import com.ringdroid.R

/**
 * After a ringtone has been saved, this activity lets you pick a contact
 * and assign the ringtone to that contact.
 */
class ChooseContactActivity : ListActivity(), TextWatcher, LoaderCallbacks<Cursor> {

    private var mFilter: TextView? = null
    private var mAdapter: SimpleCursorAdapter? = null
    private var mRingtoneUri: Uri? = null
    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(icicle: Bundle) {
        super.onCreate(icicle)
        setTitle(R.string.choose_contact_title)
        val intent = intent
        mRingtoneUri = intent.data
        // Inflate our UI from its XML layout description.
        setContentView(R.layout.choose_contact)

        try {
            mAdapter = SimpleCursorAdapter(
                    this,  // Use a template that displays a text view
                    R.layout.contact_row,  // Set an empty cursor right now. Will be set in onLoadFinished()
                    null, arrayOf(
                    ContactsContract.Contacts.CUSTOM_RINGTONE,
                    ContactsContract.Contacts.STARRED,
                    ContactsContract.Contacts.DISPLAY_NAME), intArrayOf(
                    R.id.row_ringtone,
                    R.id.row_starred,
                    R.id.row_display_name),
                    0)
            mAdapter!!.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, columnIndex ->
                val name = cursor.getColumnName(columnIndex)
                val value = cursor.getString(columnIndex)
                if (name == ContactsContract.Contacts.CUSTOM_RINGTONE) {
                    if (value != null && value.length > 0) {
                        view.visibility = View.VISIBLE
                    } else {
                        view.visibility = View.INVISIBLE
                    }
                    return@ViewBinder true
                }
                if (name == ContactsContract.Contacts.STARRED) {
                    if (value != null && value == "1") {
                        view.visibility = View.VISIBLE
                    } else {
                        view.visibility = View.INVISIBLE
                    }
                    return@ViewBinder true
                }
                false
            }
            listAdapter = mAdapter
            // On click, assign ringtone to contact
            listView.onItemClickListener = OnItemClickListener { parent, view, position, id -> assignRingtoneToContact() }
            loaderManager.initLoader(0, null, this)
        } catch (e: SecurityException) { // No permission to retrieve contacts?
            Log.e("Ringdroid", e.toString())
        }
        mFilter = findViewById<View>(R.id.search_filter) as TextView
        if (mFilter != null) {
            mFilter!!.addTextChangedListener(this)
        }
    }

    private fun assignRingtoneToContact() {
        val c = mAdapter!!.cursor
        var dataIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
        val contactId = c.getString(dataIndex)
        dataIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
        val displayName = c.getString(dataIndex)
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        val values = ContentValues()
        values.put(ContactsContract.Contacts.CUSTOM_RINGTONE, mRingtoneUri.toString())
        contentResolver.update(uri, values, null, null)
        val message = resources.getText(R.string.success_contact_ringtone).toString() +
                " " +
                displayName
        Toast.makeText(this, message, Toast.LENGTH_SHORT)
                .show()
        finish()
        return
    }

    /* Implementation of TextWatcher.beforeTextChanged */
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    /* Implementation of TextWatcher.onTextChanged */
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    /* Implementation of TextWatcher.afterTextChanged */
    override fun afterTextChanged(s: Editable) { //String filterStr = mFilter.getText().toString();
//mAdapter.changeCursor(createCursor(filterStr));
        val args = Bundle()
        args.putString("filter", mFilter!!.text.toString())
        loaderManager.restartLoader(0, args, this)
    }

    /* Implementation of LoaderCallbacks.onCreateLoader */
    override fun onCreateLoader(id: Int, args: Bundle): Loader<Cursor> {
        var selection: String? = null
        val filter = args?.getString("filter")
        if (filter != null && filter.length > 0) {
            selection = "(DISPLAY_NAME LIKE \"%$filter%\")"
        }
        return CursorLoader(
                this,
                ContactsContract.Contacts.CONTENT_URI, arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.CUSTOM_RINGTONE,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.LAST_TIME_CONTACTED,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.TIMES_CONTACTED),
                selection,
                null,
                "STARRED DESC, " +
                        "TIMES_CONTACTED DESC, " +
                        "LAST_TIME_CONTACTED DESC, " +
                        "DISPLAY_NAME ASC"
        )
    }

    /* Implementation of LoaderCallbacks.onLoadFinished */
    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        Log.v("Ringdroid", data.count.toString() + " contacts")
        mAdapter!!.swapCursor(data)
    }

    /* Implementation of LoaderCallbacks.onLoaderReset */
    override fun onLoaderReset(loader: Loader<Cursor>) { // This is called when the last Cursor provided to onLoadFinished()
// above is about to be closed.  We need to make sure we are no
// longer using it.
        mAdapter!!.swapCursor(null)
    }
}