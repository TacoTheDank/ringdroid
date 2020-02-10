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
package com.ringdroid.data.player

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.ringdroid.extensions.TAG
import java.util.*

class SongMetadataReader internal constructor(val context: Context, val filename: String) {

    var mTitle: String? = ""
    var mArtist: String? = ""
    var mAlbum: String? = ""
    var mGenre = ""
    var mYear = -1

    private fun readMetaData() {
        readGenre()
        readArtistAlbumAndYear()
    }

    private fun readGenre() {
        val genreIdMap = HashMap<String, String>()
        var cursor = context.contentResolver.query(
                GENRES_URI, arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME),
                null, null, null)
        cursor.apply {
            this!!.moveToFirst()
            while (!isAfterLast) {
                genreIdMap[getString(0)] = getString(1)
                moveToNext()
            }
            close()
        }

        mGenre = ""

        for (genreId in genreIdMap.keys) {
            cursor = context.contentResolver.query(
                    makeGenreUri(genreId), arrayOf(MediaStore.Audio.Media.DATA),
                    MediaStore.Audio.Media.DATA + " LIKE \"" + filename + "\"",
                    null, null)!!
            if (cursor.count != 0) {
                mGenre = genreIdMap[genreId]!!
                break
            }
            cursor.close()
            cursor = null
        }
    }

    private fun readArtistAlbumAndYear() {
        val uri = MediaStore.Audio.Media.getContentUriForPath(filename)

        val cursor = context.contentResolver.query(
                uri, arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATA),
                MediaStore.Audio.Media.DATA + " LIKE \"" + filename + "\"",
                null, null)!!
        if (cursor.count == 0) {
            mTitle = getBasename(filename)
            mArtist = ""
            mAlbum = ""
            mYear = -1
            return
        }
        cursor.moveToFirst()
        mTitle = getStringFromColumn(cursor, MediaStore.Audio.Media.TITLE)
        if (mTitle == null || mTitle!!.length == 0) {
            mTitle = getBasename(filename)
        }
        mArtist = getStringFromColumn(cursor, MediaStore.Audio.Media.ARTIST)
        mAlbum = getStringFromColumn(cursor, MediaStore.Audio.Media.ALBUM)
        mYear = getIntegerFromColumn(cursor, MediaStore.Audio.Media.YEAR)
        cursor.close()
    }

    private fun makeGenreUri(genreId: String): Uri {
        val CONTENTDIR = MediaStore.Audio.Genres.Members.CONTENT_DIRECTORY
        return Uri.parse(
                StringBuilder()
                        .append(GENRES_URI.toString())
                        .append("/")
                        .append(genreId)
                        .append("/")
                        .append(CONTENTDIR)
                        .toString())
    }

    private fun getStringFromColumn(c: Cursor?, columnName: String): String? {
        val index = c!!.getColumnIndexOrThrow(columnName)
        val value = c.getString(index)
        return when {
            value != null && value.length > 0 -> {
                value
            }
            else -> {
                null
            }
        }
    }

    private fun getIntegerFromColumn(c: Cursor?, columnName: String): Int {
        val index = c!!.getColumnIndexOrThrow(columnName)
        val value = c.getInt(index)
        return value ?: -1
    }

    private fun getBasename(filename: String): String {
        return filename.substring(filename.lastIndexOf('/') + 1,
                filename.lastIndexOf('.'))
    }

    init {
        mTitle = getBasename(filename)
        try {
            readMetaData()
        } catch (e: Exception) {
            Log.e(TAG, e.message)
        }
    }

    companion object {
        val GENRES_URI = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
    }
}