/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android;

import java.util.LinkedList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class RecentFiles {
	/** Maximum number of files stored in file history */
	public final static int MAX_HISTORY_SIZE = 10;
	private final LinkedList<RecentFile> _recentFiles = new LinkedList<RecentFile>();
	private final Context _ctx;

	public RecentFiles(Context ctx) {
		this._ctx = ctx;
		loadFromPersistentStore();
	}

	public List<RecentFile> getRecentFiles(){
		return _recentFiles;
	}

	/**
	 * Adds filename to the top of the recent history list.
	 * If filename was previously in the list, it will be promoted to
	 * the top of the list.
	 *
	 * @param filename Full path of file to add to the recent history list
	 */
	public void addRecentFile(String filename){
		RecentFile recentFile = getRecentFile(filename);

		if(recentFile == null){
			recentFile = new RecentFile(filename);
		}
		else{
			//remove existing entry
			_recentFiles.remove(recentFile);
		}

		//promote to head of list
		_recentFiles.addFirst(recentFile);

		//trim list
		if(_recentFiles.size() > MAX_HISTORY_SIZE){
			_recentFiles.removeLast();
		}
	}

	private RecentFile getRecentFile(String filename){
		for (int i = 0;  i< _recentFiles.size(); ++i){
			if (_recentFiles.get(i).getFileName().equals(filename)){
				return _recentFiles.get(i);
			}
		}
		return null;
	}

	/**
	 * Open the recent files database. If it cannot be opened, try to create a
	 * new instance. If it cannot be created, throw an exception.
	 *
	 * @throws SQLException if the database could be neither opened or created
	 */
	private void loadFromPersistentStore() throws SQLException {
		RecentFilesDbHelper dbHelper = new RecentFilesDbHelper(_ctx);
		SQLiteDatabase db = dbHelper.getWritableDatabase();

		Cursor c = db.query(DATABASE_TABLE,
				new String[] {KEY_FILENAME, KEY_TIMESTAMP, KEY_SCROLL_X, KEY_SCROLL_Y, KEY_CARET_POSITION},
				null, null, null, null, KEY_RECENT_RANK);
		int filenameColumn = c.getColumnIndexOrThrow(KEY_FILENAME);
		int timestampColumn = c.getColumnIndexOrThrow(KEY_TIMESTAMP);
		int scrollXColumn = c.getColumnIndexOrThrow(KEY_SCROLL_X);
		int scrollYColumn = c.getColumnIndexOrThrow(KEY_SCROLL_Y);
		int caretPositionColumn = c.getColumnIndexOrThrow(KEY_CARET_POSITION);
		while(c.moveToNext()){
			RecentFile recentFile = new RecentFile(
					c.getString(filenameColumn),
					c.getLong(timestampColumn),
					c.getInt(scrollXColumn),
					c.getInt(scrollYColumn),
					c.getInt(caretPositionColumn));
			_recentFiles.add(recentFile);
		}
		c.close();
		dbHelper.close();
	}

	public void save(){
		RecentFilesDbHelper dbHelper = new RecentFilesDbHelper(_ctx);
		SQLiteDatabase db = dbHelper.getWritableDatabase();

		// Delete all rows and add the contents of _recentFiles.
		// This is simpler than checking the db if a filename exists and then
		// calculating and updating the appropriate rank.
		db.delete(DATABASE_TABLE, null, null);

		ContentValues initialValues = new ContentValues();
		for(int i = 0; i < _recentFiles.size(); ++i){
			initialValues.put(KEY_FILENAME, _recentFiles.get(i).getFileName());
			initialValues.put(KEY_TIMESTAMP, _recentFiles.get(i).getTimestamp());
			initialValues.put(KEY_SCROLL_X, _recentFiles.get(i).getScrollX());
			initialValues.put(KEY_SCROLL_Y, _recentFiles.get(i).getScrollY());
			initialValues.put(KEY_CARET_POSITION, _recentFiles.get(i).getCaretPosition());
			initialValues.put(KEY_RECENT_RANK, i);
			db.insert(DATABASE_TABLE, null, initialValues);
		}
		dbHelper.close();
	}

	private static final String TAG = "RecentFiles";

	private static final String DATABASE_NAME = "textwarrior";
	private static final String DATABASE_TABLE = "recent";
	private static final int DATABASE_VERSION = 2;

	private static final String KEY_FILENAME = "filename";
	private static final String KEY_RECENT_RANK = "rank";
	private static final String KEY_TIMESTAMP = "timestamp";
	private static final String KEY_SCROLL_X = "scrollX";
	private static final String KEY_SCROLL_Y = "scrollY";
	private static final String KEY_CARET_POSITION = "caret";
	private static final String KEY_ROW_ID = "_id";

	private static final String DATABASE_CREATE =
		"create table " + DATABASE_TABLE + " ("
		+ KEY_ROW_ID + " integer primary key autoincrement, "
		+ KEY_FILENAME + " text unique not null, "
		+ KEY_TIMESTAMP + " timestamp not null, "
		+ KEY_SCROLL_X + " integer not null, "
		+ KEY_SCROLL_Y + " integer not null, "
		+ KEY_CARET_POSITION + " integer not null, "
		+ KEY_RECENT_RANK + " integer unique not null);";

	private static final String DATABASE_DELETE =
		"DROP TABLE IF EXISTS " + DATABASE_TABLE;


	private static class RecentFilesDbHelper extends SQLiteOpenHelper {
		RecentFilesDbHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL(DATABASE_DELETE);
			onCreate(db);
		}
	}

	public static class RecentFile implements Comparable<RecentFile>{
		private final String _fileName;
		private int _scrollX = 0;
		private int _scrollY = 0;
		private int _caretPosition = 0;
		private long _timestamp = 0; // UNIX timestamp of when viewport settings were saved

		public RecentFile(String name){
			_fileName = name;
		}

		public RecentFile(String name, long timestamp, int scrollX, int scrollY, int caretPosition){
			_fileName = name;
			saveViewportSettings(timestamp, scrollX, scrollY, caretPosition);
		}

		public boolean equals(RecentFile right){
			return true;
		}

		@Override
		public int compareTo(RecentFile another) {
			if (_timestamp < another.getTimestamp()) {
				return -1;
			}
			if (_timestamp > another.getTimestamp()) {
				return 1;
			}
			return 0;
		}

		/**
		 * Sets the last-known viewport settings of a file
		 * @param scrollX X-offset of view
		 * @param scrollY Y-offset of view
		 * @param caretPosition The character position the caret is on
		 */
		public void saveViewportSettings(long timestamp, int scrollX, int scrollY, int caretPosition){
			_timestamp = timestamp;
			_scrollX = scrollX;
			_scrollY = scrollY;
			_caretPosition = caretPosition;
		}

		public long getTimestamp() {
			return _timestamp;
		}

		public String getFileName() {
			return _fileName;
		}

		public int getCaretPosition() {
			return _caretPosition;
		}

		public int getScrollX() {
			return _scrollX;
		}

		public int getScrollY() {
			return _scrollY;
		}
	}

}
