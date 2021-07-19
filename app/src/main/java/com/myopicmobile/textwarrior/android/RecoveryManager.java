/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

package com.myopicmobile.textwarrior.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.myopicmobile.textwarrior.common.CharEncodingUtils;
import com.myopicmobile.textwarrior.common.Document;
import com.myopicmobile.textwarrior.common.Document.TextFieldMetrics;
import com.myopicmobile.textwarrior.common.DocumentProvider;
import com.myopicmobile.textwarrior.common.EncodingScheme;
import com.myopicmobile.textwarrior.common.Flag;
import com.myopicmobile.textwarrior.common.Pair;
import com.myopicmobile.textwarrior.common.TextBuffer;
import com.myopicmobile.textwarrior.common.TextWarriorException;

/**
 *
 * When there are unsaved changes to a file and the app is forced to close by
 * the system, RecoveryManager can save a copy of the changed file.
 * When the app starts again, the user can recover the unsaved changes.
 *
 * The copy is called the recovery file. The original file before the edits
 * is called the head file. Recovery files are named backup0.txt, backup1.txt,...
 * The numbering scheme wraps around after MAX_BACKUP_FILES.
 *
 * If there is a IO error when restoring the recovery file, a copy of it is
 * saved to external storage. This copy is called the safekeeping file.
 * Only one safekeeping file is allowed. It will be overwritten if there is
 * another occurrence of an IO error when recovering a file. The user should
 * save the safekeeping file as soon as possible.
 *
 */
public class RecoveryManager {
	private final static int MAX_BACKUP_FILES = 10;
	static {
		TextWarriorException.assertVerbose(MAX_BACKUP_FILES > 0,
				"MAX_BACKUP_FILES should be at least 1");
	}

	private final TextWarriorApplication _app;
	private final SharedPreferences _persistence;
	private int _recoveryErrorCode = ERROR_NONE; // the error code of the latest recovery action

	public RecoveryManager(TextWarriorApplication app) {
		_app = app;
		_persistence = _app.getSharedPreferences(PREFS_RECOVERY, 0);
	}

	/**
	 * Writes a backup of the working file.
	 *
	 * Attempts to backup to internal storage first. If unsuccessful, tries
	 * backing up to external storage. If still unsuccessful, the error will be
	 * recorded in PREFS_RECOVERY by setting STATE_TYPE to TYPE_FAILED.
	 *
	 * A backup will only be made if there are unsaved changes. If the working
	 * file has not been edited yet, STATE_TYPE will be set to TYPE_NO_CHANGES.
	 */
	public void backup(FreeScrollingTextField editField, String headFile) {
		int resultCode;
		if (editField.isEdited()) {
			//TODO check if headFile refers to an existing backup because it might get overwritten
			if (backupToInternalStorage(editField) || backupToExternalStorage(editField)) {
				resultCode = BACKUP_SUCCESS;
			}
			else {
				Log.e(this.toString(), "Could not create backup in local or external storage. Unsaved changes are lost");
				resultCode = BACKUP_FAILED;
			}
		}
		else {
			resultCode = BACKUP_NO_CHANGES;
		}

		String filename = headFile;
		if (filename == null) {
			filename = "";
		}

		SharedPreferences.Editor editor = _persistence.edit();
		editor.putInt(STATE_BACKUP_RESULT, resultCode);
		editor.putString(STATE_HEAD_PATH, filename);

		DocumentProvider doc = editField.createDocumentProvider();
		editor.putString(STATE_ENCODING, doc.getEncodingScheme());
		editor.putString(STATE_EOL, doc.getEOLType());

		editor.commit();
	}

	private boolean backupToInternalStorage(FreeScrollingTextField editField){
		boolean saveSuccess = false;

    	try{
    		String filename = getNextFilename();
    		FileOutputStream fs = _app.openFileOutput(filename, Context.MODE_PRIVATE);

    		try{
    			DocumentProvider doc = editField.createDocumentProvider();
        		CharEncodingUtils converter = new CharEncodingUtils();
    	        converter.writeAndConvert(fs, doc,
    	        		doc.getEncodingScheme(), doc.getEOLType(), new Flag());
    	        setRecoveryFilePath(combinePath(getInternalRecoveryDir(), filename));
    	        incrementRecoveryId();
    	        saveSuccess = true;
    		}
    		finally{
    			fs.close();
    		}
    	}
    	catch (IOException ex) {
    		//do nothing
    	}

		return saveSuccess;
	}

	private boolean backupToExternalStorage(FreeScrollingTextField editField) {
		boolean saveSuccess = false;

		String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	    	createExternalStorageDirectory();
	    	String filename = combinePath(getExternalRecoveryDir(), getNextFilename());
    		File f = new File(filename);

	    	try{
	    		FileOutputStream fs = new FileOutputStream(f);

	    		try{
	    			DocumentProvider doc = editField.createDocumentProvider();
	        		CharEncodingUtils converter = new CharEncodingUtils();
	    	        converter.writeAndConvert(fs, doc,
	    	        		doc.getEncodingScheme(), doc.getEOLType(), new Flag());
	    	        setRecoveryFilePath(filename);
	    	        incrementRecoveryId();
	    	        saveSuccess = true;
	    		}
	    		finally{
	    			fs.close();
	    		}
	    	}
	    	catch (IOException ex) {
	    		//do nothing
	    	}
	    }

		return saveSuccess;
	}

	/**
	 * Attempts to read a previously backup file into editField.
	 *
	 * The backup file has to be created by a prior call to backup().
	 *
	 * @return Whether the recover was successful
	 */
	public boolean recover(FreeScrollingTextField editField){
		String headPath = _persistence.getString(STATE_HEAD_PATH, "");
		int latestBackupResult = _persistence.getInt(STATE_BACKUP_RESULT, BACKUP_NONE);

		String recoveryPath;
		switch (latestBackupResult) {
		case BACKUP_NO_CHANGES:
			recoveryPath = headPath;
			break;
		case BACKUP_SUCCESS:
			recoveryPath = _persistence.getString(STATE_PATH, "");
			break;
		default:
			recoveryPath = "";
			break;
		}

		if (recoveryPath.length() == 0) {
			if (latestBackupResult == BACKUP_NO_CHANGES) {
				// Unsaved file was unnamed and blank. No recovery needed.
				_recoveryErrorCode = ERROR_NONE;
				return true;
			}

			if (latestBackupResult == BACKUP_NONE){
				// No attempt at creating a recovery file
				_recoveryErrorCode = ERROR_RECOVERY_DISABLED;
			}
			else if (latestBackupResult == BACKUP_FAILED){
				// Error in writing recovery file
				_recoveryErrorCode = ERROR_WRITE;
			}
			else {
				TextWarriorException.fail("Recovery file created but filename not recorded");
				_recoveryErrorCode = ERROR_RECOVERY_DISABLED;
			}
			return false;
		}

    	File recoveryFile = new File(recoveryPath);
    	FileInputStream fs = null;
    	try {
    		fs = new FileInputStream(recoveryFile);
    		Document doc = readRecoveryFile(fs, recoveryFile.length(), editField);

    		doc.setWordWrap(_app.isWordWrap());
    		editField.setDocumentProvider(new DocumentProvider(doc));
    		if (latestBackupResult == BACKUP_SUCCESS) {
    			editField.setEdited(true);
    		}

    		_app.updateFilename(headPath);
    		_recoveryErrorCode = ERROR_NONE;
    	}
    	catch (FileNotFoundException ex) {
    		_recoveryErrorCode = ERROR_FILE_NOT_FOUND;
    	}
    	catch (IOException ex) {
    		_recoveryErrorCode = ERROR_READ;
    		copyToExternalStorage(recoveryFile);
    	}
    	finally {
    		if (fs != null) {
    			try { fs.close(); } catch (IOException ex) { /* do nothing */ }
    		}
    	}

		return (_recoveryErrorCode == ERROR_NONE);
	}

	private Document readRecoveryFile(FileInputStream recoveryStream, long textLength, TextFieldMetrics metrics)
	throws IOException {
		CharEncodingUtils converter = new CharEncodingUtils();

		//determine encoding and other read options
		String encoding = _persistence.getString(STATE_ENCODING,
				EncodingScheme.TEXT_ENCODING_UTF8);
		String eolChar = _persistence.getString(STATE_EOL,
				EncodingScheme.LINE_BREAK_LF);

		//allocate buffer
		if (encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16BE) ||
				encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16LE)){
			textLength >>>= 1; // 2 bytes in the file == 1 char
		}
		if(textLength > Integer.MAX_VALUE){
			throw new OutOfMemoryError();
		}
		int implSize = TextBuffer.memoryNeeded((int) textLength);
		if(implSize == -1){
			throw new OutOfMemoryError();
		}
		char[] newBuffer = new char[implSize];

		//read
		//TODO add uncancelable progress bar
		Pair statistics = converter.readAndConvert(recoveryStream, newBuffer,
				encoding, eolChar, new Flag());

		Document doc = new Document(metrics);
		doc.setBuffer(newBuffer,
    			encoding,
    			eolChar,
    			statistics.getFirst(),
    			statistics.getSecond());
		return doc;
	}

	// no further action is taken if this method fails
	private void copyToExternalStorage(File srcFile) {
		String state = Environment.getExternalStorageState();
	    if (!Environment.MEDIA_MOUNTED.equals(state)) {
			Log.e(this.toString(), "Could not copy recovery file to external storage for user to access.");
			return;
	    }

    	createExternalStorageDirectory();
    	FileInputStream src = null;
    	FileOutputStream dest = null;
    	try {
    		src = new FileInputStream(srcFile);
    		dest = new FileOutputStream(getSafekeepingFileAbsolutePath());

    		byte[] buf = new byte[1024];
    		int len;
    		while((len = src.read(buf)) > 0) {
    			dest.write(buf, 0, len);
    		}
    	}
    	catch (IOException ex) {
    		/* do nothing */
    	}
    	finally {
    		if (src != null) {
    			try { src.close(); } catch (IOException ex) { /* do nothing */ }
    		}
    		if (dest != null) {
    			try { dest.close(); } catch (IOException ex) { /* do nothing */ }
    		}
    	}
	}

	// Does nothing if the directory is already created
	private void createExternalStorageDirectory() {
		String dirName = getExternalRecoveryDir();
		File dir = new File(dirName);

		if(!dir.exists()){
			dir.mkdirs();
		}
	}

	private String combinePath(String dirName, String filename) {
		return dirName + File.separator + filename;
	}

	/*
	private void deleteAllBackupFiles() {
		final String[] backupDirectories = {getInternalRecoveryDir(), getExternalRecoveryDir()};
		for (String dirPath : backupDirectories) {
			File dir = new File(dirPath);

			if (dir.exists() && dir.isDirectory()) {
				for (File backupFile : dir.listFiles()) {
					backupFile.delete();
				}
			}
		}
	}
	*/

	/**
	 * Clears metadata associated with file recovery.
	 * Leaves the recovery files on disk. Note that these files will eventually
	 * get overwritten when backup() is called repeatedly.
	 */
	public void clearRecoveryState() {
		SharedPreferences.Editor editor = _persistence.edit();
		editor.putString(STATE_PATH, "");
		editor.putString(STATE_HEAD_PATH, "");
		editor.putInt(STATE_BACKUP_RESULT, BACKUP_NONE);
		editor.commit();
	}

	public int getRecoveryErrorCode() {
		return _recoveryErrorCode;
	}

	private String getNextFilename(){
		int currentId = _persistence.getInt(STATE_ID_COUNTER, 0);
		return RECOVERY_BASE_FILENAME + currentId + RECOVERY_FILENAME_EXTENSION;
	}

	private void setRecoveryFilePath(String absPath){
		SharedPreferences.Editor editor = _persistence.edit();
		editor.putString(STATE_PATH, absPath);
		editor.commit();
	}

	private void incrementRecoveryId(){
		int currentId = _persistence.getInt(STATE_ID_COUNTER, 0);

		SharedPreferences.Editor editor = _persistence.edit();
		editor.putInt(STATE_ID_COUNTER, (currentId+1) % MAX_BACKUP_FILES);
		editor.commit();
	}

	private String getInternalRecoveryDir() {
		return _app.getFilesDir().getAbsolutePath();
	}

	public String getExternalRecoveryDir() {
		return Environment.getExternalStorageDirectory().getPath() + RECOVERY_EXT_STORAGE_DIR;
	}

	public String getSafekeepingFileAbsolutePath() {
		return getExternalRecoveryDir() + File.separator + SAFEKEEPING_FILENAME;
	}

	public boolean isInRecoveryPath(File file) {
		String dir = file.getParent();
		if(dir == null){
			dir = "";
		}
		// no need to check getInternalRecoveryPath() because
		// regular users can't access it
		return dir.equals(getExternalRecoveryDir());
	}

	// keys for saving and restoring application state
	private final static String PREFS_RECOVERY = "recoveryPrefs";
	private final static String STATE_ID_COUNTER = "recoveryIdCounter";
	private final static String STATE_PATH = "recoveryPath";
	private final static String STATE_HEAD_PATH = "recoveryHeadPath";
	private final static String STATE_ENCODING = "recoveryEncoding";
	private final static String STATE_EOL = "recoveryEOL";
	private final static String STATE_BACKUP_RESULT = "recoveryBackupResult";

	private static final int BACKUP_SUCCESS = 0; /** recovery file saved successfully */
	private static final int BACKUP_NONE = 1; /** no attempt to create a recovery file */
	private static final int BACKUP_FAILED = 2; /** attempted to create a recovery file but failed */
	private static final int BACKUP_NO_CHANGES = 3; /** no recovery file needed because the head file was unchanged */

	public static final int ERROR_NONE = 0;
	public static final int ERROR_RECOVERY_DISABLED = 1;
	public static final int ERROR_FILE_NOT_FOUND = 2;
	public static final int ERROR_READ = 3;
	public static final int ERROR_WRITE = 4;

	private final static String RECOVERY_BASE_FILENAME = "backup";
	private final static String RECOVERY_FILENAME_EXTENSION = ".txt";
	private final static String SAFEKEEPING_FILENAME = "recovered.txt";
	private final static String RECOVERY_EXT_STORAGE_DIR = "/Android/data/com.myopicmobile.textwarrior.android/files";
}
