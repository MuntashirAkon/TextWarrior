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
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.openintents.filemanager.IconifiedText;
import org.openintents.filemanager.IconifiedTextListAdapter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.myopicmobile.textwarrior.common.AnalyzeStatisticsThread;
import com.myopicmobile.textwarrior.common.CharEncodingUtils;
import com.myopicmobile.textwarrior.common.ColorScheme;
import com.myopicmobile.textwarrior.common.ColorSchemeDark;
import com.myopicmobile.textwarrior.common.ColorSchemeLight;
import com.myopicmobile.textwarrior.common.ColorSchemeObsidian;
import com.myopicmobile.textwarrior.common.ColorSchemeSolarizedDark;
import com.myopicmobile.textwarrior.common.ColorSchemeSolarizedLight;
import com.myopicmobile.textwarrior.common.Document;
import com.myopicmobile.textwarrior.common.DocumentProvider;
import com.myopicmobile.textwarrior.common.FindThread;
import com.myopicmobile.textwarrior.common.FindThread.FindResults;
import com.myopicmobile.textwarrior.common.LanguageC;
import com.myopicmobile.textwarrior.common.LanguageCpp;
import com.myopicmobile.textwarrior.common.LanguageCsharp;
import com.myopicmobile.textwarrior.common.LanguageJava;
import com.myopicmobile.textwarrior.common.LanguageJavascript;
import com.myopicmobile.textwarrior.common.LanguageNonProg;
import com.myopicmobile.textwarrior.common.LanguageObjectiveC;
import com.myopicmobile.textwarrior.common.LanguagePHP;
import com.myopicmobile.textwarrior.common.LanguagePython;
import com.myopicmobile.textwarrior.common.LanguageRuby;
import com.myopicmobile.textwarrior.common.Lexer;
import com.myopicmobile.textwarrior.common.ProgressObserver;
import com.myopicmobile.textwarrior.common.ProgressSource;
import com.myopicmobile.textwarrior.common.ReadThread;
import com.myopicmobile.textwarrior.common.RowListener;
import com.myopicmobile.textwarrior.common.TextWarriorException;
import com.myopicmobile.textwarrior.common.WriteThread;

public class TextWarriorApplication extends Activity
implements ProgressObserver, RowListener, SelectionModeListener,
SharedPreferences.OnSharedPreferenceChangeListener{

	protected FreeScrollingTextField _editField;
	protected FindPanel _findPanel;
	protected ClipboardPanel _clipboardPanel;
	protected String _filename = null;
	private String _lastSelectedFile = null; // latest result from FilePicker; may not refer to a valid file
	private Document _inputingDoc; // used as a temp holder when reading in a file TODO investigate using local variable
	private Bundle _initBundle = null; // the bundle that was passed in onCreate
	protected RecentFiles _recentFiles;
	private RecoveryManager _recoveryManager;


	//-----------------------------------------------------------------------
	//------------------- Creational and init methods -----------------------

	@Override
	/*
	 * 3 different scenarios are distinguished here.
	 * 1. Fresh start of an activity.
	 * 2. Activity re-created immediately after a configuration change
	 * 3. Activity re-created after the system force-killed its process,
	 * 	for example, to reclaim resources
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		_initBundle = savedInstanceState;
		setContentView(R.layout.main);

		createTextField();
		createFindPanel();
		createClipboardPanel();
		restorePersistentOptions();

		_recentFiles = new RecentFiles(this);
		_recoveryManager = new RecoveryManager(this);

		NonConfigurationState ncs = (NonConfigurationState) getLastNonConfigurationInstance();
		if(savedInstanceState == null){
			/* Scenario 1 */
			Intent i = getIntent();
			String action = i.getAction();
			if(action.equals(Intent.ACTION_VIEW)
					|| action.equals(Intent.ACTION_EDIT)){
				open(i.getData().getPath());
			}
		}
		else if (ncs != null){
			/* Scenario 2 */
			restoreNonConfigurationState(ncs);
			restoreUiState(savedInstanceState);
		}
		else{
			/* Scenario 3 */
			//TODO check if newer file on system
			boolean isRecovered = _recoveryManager.recover(_editField);
			Log.d(LOG_TAG, isRecovered
					? "Backup restored"
					: "Recovery failed with error code " + _recoveryManager.getRecoveryErrorCode());

			// Workaround to dismiss system-managed dialogs that were at the
			// foreground when the process was force-killed
			Handler h = new Handler();
			h.post(new Runnable(){
				@Override
				public void run() {
					dismissAllDialogs();
					//TODO if worker threads were interrupted, display info box
				}
			});

			if (isRecovered){
				restoreUiState(savedInstanceState);
			}
			else {
				h.post(new Runnable(){
					@Override
					public void run() {
						prepareRecoveryFailedMessage();
						showDialog(DIALOG_RECOVERY_FAILED);
					}
				});
			}
		}

		updateTitle();
		updateClipboardButtons();
	}



	@Override
	protected void onNewIntent(Intent intent) {
		String action = intent.getAction();
		if(action.equals(Intent.ACTION_VIEW) || action.equals(Intent.ACTION_EDIT)){
			setIntent(intent);

			if(_editField.isEdited()){
				_saveFinishedCallback = SAVE_CALLBACK_SINGLE_TASK_OPEN;
				showDialog(DIALOG_PROMPT_SAVE);
			}
			else{
				open(intent.getData().getPath());
			}
		}
	}



	private void createClipboardPanel() {
		_clipboardPanel = (ClipboardPanel) findViewById(R.id.clipboard_drawer);
		_clipboardPanel.setInterpolator(new LinearInterpolator());

		_clipboardPanel.setCutListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				cut();
			}
		});

		_clipboardPanel.setCopyListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				copy();
			}
		});

		_clipboardPanel.setPasteListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				paste();
			}
		});
	}

	/**
	 * Enable/disable cut/copy/paste buttons based on text selection state
	 */
	private void updateClipboardButtons(){
		boolean isSelecting = _editField.isSelectText();
		ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		_clipboardPanel.setClipboardButtonState(
				isSelecting, isSelecting, cb.hasText());
	}


	private void createFindPanel() {
		_findPanel = (FindPanel) findViewById(R.id.find_panel);
		_findPanel.setCallback(this);
	}


	private void createTextField() {
		_editField = (FreeScrollingTextField) findViewById(R.id.work_area);
		_editField.setRowListener(this);
		_editField.setSelModeListener(this);
	}

	private void restorePersistentOptions() {
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
		final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

		setAutoIndent(pref);
		setLongPressCaps(pref);
		setWordWrap(pref);
		setSyntaxColor(pref);
		setColorScheme(pref);
		setHighlightCurrentRow(pref);
		setNavigationMethod(pref);
		setTabSpaces(pref);
		setFont(pref);
		setZoom(pref);
		setNonPrintingCharVisibility(pref);
	}

	/*
	 * Dirty bit of _editField is cleared as a side-effect
	 */
	private void changeModel(Document doc){
		_editField.setDocumentProvider(new DocumentProvider(doc));
		_editField.setEdited(false);
	}

	/*
	 * Not private to allow access to RecoveryManager
	 */
	void updateFilename(String filename){
		if (filename.length() == 0){
			_filename = _lastSelectedFile = null;
		}
		else{
			_filename = _lastSelectedFile = filename;
		}
	}

	private void updateTitle(){
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		boolean showLineNumbers = pref.getBoolean(
				getString(R.string.settings_key_show_row_number),
				getResources().getBoolean(R.bool.settings_show_row_number_default));

		String title;
		if(showLineNumbers){
			title = createTitle(_editField.getCaretRow()+1);
		}
		else{
			title = createTitle();
		}

		setTitle(title);
	}

	private String createTitle(){
		String title;
		if(_filename != null){
			title = (new File(_filename)).getName();
		}
		else{
			title = getString(R.string.title);
		}
		return title;
	}

	private String createTitle(int rowIndex){
		String title = "(" + rowIndex + ") " + createTitle();
		return title;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);

		onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu){
		//cut and paste options
		menu.setGroupVisible(R.id.menu_group_selection_actions,
				_editField.isSelectText());

		//save option
		MenuItem saveMenuItem = menu.findItem(R.id.save);
		saveMenuItem.setEnabled(_editField.isEdited());

		//paste option
		MenuItem pasteMenuItem = menu.findItem(R.id.paste);
		ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		pasteMenuItem.setVisible(cb.hasText());

		//undo and redo options
		DocumentProvider doc = _editField.createDocumentProvider();
		MenuItem undoMenuItem = menu.findItem(R.id.undo);
		undoMenuItem.setEnabled(doc.canUndo());
		MenuItem redoMenuItem = menu.findItem(R.id.redo);
		redoMenuItem.setVisible(doc.canRedo());

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.new_file:
			if(_editField.isEdited()){
				_saveFinishedCallback = SAVE_CALLBACK_NEW;
				showDialog(DIALOG_PROMPT_SAVE);
			}
			else{
				onNew();
			}
			break;

		case R.id.recent_files:
			if(_editField.isEdited()){
				_saveFinishedCallback = SAVE_CALLBACK_OPEN_RECENT;
				showDialog(DIALOG_PROMPT_SAVE);
			}
			else{
				onOpenRecent();
			}
			break;

		case R.id.open:
			if(_editField.isEdited()){
				_saveFinishedCallback = SAVE_CALLBACK_OPEN;
				showDialog(DIALOG_PROMPT_SAVE);
			}
			else{
				onOpen();
			}
			break;

		case R.id.save:
			onSave();
			break;

		case R.id.save_as:
			onSaveAs();
			break;

		case R.id.undo:
			onUndo(true);
			break;

		case R.id.redo:
			onUndo(false);
			break;

		case R.id.find_panel_toggle:
			toggleFindPanel();
			break;

		case R.id.go_to_line:
			showDialog(DIALOG_GOTO_LINE);
			break;

		case R.id.cut:
			cut();
			break;

		case R.id.copy:
			copy();
			break;

		case R.id.paste:
			paste();
			break;

		case R.id.select_all:
			_editField.selectAll();
			break;

		case R.id.statistics:
			analyzeTextProperties();
			break;

		case R.id.change_input_method:
			InputMethodManager im =
				(InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			im.showInputMethodPicker();
			break;

		case R.id.settings:
			Intent i = new Intent(this, TextWarriorSettings.class);
			startActivity(i);
			break;

		case R.id.help:
			openHelp();
			break;

		case R.id.about:
			showDialog(DIALOG_ABOUT);
			break;

		default:
			return false;
		}
		return true;
	}





	//-----------------------------------------------------------------------
	//------------------------- Menu item callbacks -------------------------

	private void onNew() {
		Document doc = new Document(_editField);
		doc.setWordWrap(isWordWrap());
		changeModel(doc);
		_filename = null;
		_lastSelectedFile = null;
		updateTitle();
	}

	// API 7 Eclair does not support a Bundle argument for showDialog(), which makes
	// maintaining session state across chained dialogs more tedious.
	// When the user selects New or Open from a menu and is prompted to save the
	// current dirty file, a callback will be set to the appropriate action after
	// the save is completed.
	// Several alternative flow of events might occur: file is read-only, file does
	// not have a name, ran out of space for saving... These will display yet another
	// dialog to the user on how to proceed, which might trigger even more dialogs.
	// We have to make sure the callback is cancelled if the user aborts from any of
	// these cases.
	protected int _saveFinishedCallback = CALLBACK_NONE;
	protected String _dialogErrorMsg = "";
	private final OnCancelListener cancelSaveCallback = new OnCancelListener(){
		@Override
		public void onCancel(DialogInterface dialog) {
			//cancel pending callback
			_saveFinishedCallback = CALLBACK_NONE;
		}
	};

	private void onOpen() {
		Intent i = new Intent(this, com.myopicmobile.textwarrior.android.FilePicker.class);
		i.setAction(TextWarriorIntents.ACTION_PICK_FILE);
		i.putExtra(TextWarriorIntents.EXTRA_TITLE, getString(R.string.file_picker_title_pick_file));
		i.setData(getLastSelectedUri());
		startActivityForResult(i, TextWarriorIntents.REQUEST_PICK_FILE);
	}

	private void onOpenRecent(){
		showDialog(DIALOG_OPEN_RECENT);
	}

	private void onSave() {
		if (_filename == null){
			onSaveAs(); // ask for a name for the untitled file
		}
		else{
			save(_filename, true);
		}
	}

	private void onSaveAs() {
		Intent i = new Intent(this, com.myopicmobile.textwarrior.android.FilePicker.class);
		i.setAction(TextWarriorIntents.ACTION_PICK_FILENAME_FOR_SAVE);
		i.putExtra(TextWarriorIntents.EXTRA_TITLE, getString(R.string.file_picker_title_enter_filename));
		i.putExtra(TextWarriorIntents.EXTRA_BUTTON_TEXT, getString(R.string.file_picker_label_save));
		i.setData(getLastSelectedUri());
		startActivityForResult(i, TextWarriorIntents.REQUEST_PICK_FILENAME_FOR_SAVE);
	}

	public void cut() {
		_editField.cut((ClipboardManager) getSystemService(CLIPBOARD_SERVICE));
		updateClipboardButtons();
	}

	public void copy() {
		_editField.copy((ClipboardManager) getSystemService(CLIPBOARD_SERVICE));
		updateClipboardButtons();
	}

	public void paste() {
		ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		_editField.paste(cb.getText().toString());
	}

	//if undo is false, a redo is done instead
	private void onUndo(boolean undo) {
		DocumentProvider doc = _editField.createDocumentProvider();
		int newPosition = undo ? doc.undo() : doc.redo();

		if(newPosition >= 0){
			//TODO _editField.setEdited(false); if reached original condition of file
			_editField.setEdited(true);

			_editField.respan();
			_editField.selectText(false);
			_editField.moveCaret(newPosition);
			_editField.invalidate();
		}
	}

	public void goToRow(int rowIndex){
		DocumentProvider src = _editField.createDocumentProvider();

		if(rowIndex < 0){
			rowIndex = 0;
		}
		else if(rowIndex >= src.getRowCount()){
			// clamp to last row
			rowIndex = src.getRowCount()-1;
		}

		int charOffset = src.getRowOffset(rowIndex);
		_editField.moveCaret(charOffset);
	}

	private void analyzeTextProperties() {
		DocumentProvider doc = _editField.createDocumentProvider();
		int start, end;
		if(_editField.isSelectText()){
			start = _editField.getSelectionStart();
			end = _editField.getSelectionEnd();
		}
		else{
			start = 0;
			end = doc.docLength();
		}
		_taskAnalyze = new AnalyzeStatisticsThread(doc, start, end);
		_taskAnalyze.registerObserver(this);

		PollingProgressDialog dialog =
			new PollingProgressDialog(this, _taskAnalyze,
					getString(R.string.progress_dialog_analyze), true, true);
		dialog.startDelayedPollingDialog();
		_taskAnalyze.start();
	}

	private void toggleFindPanel() {
		if(_findPanel.getVisibility() == View.VISIBLE){
			_findPanel.setVisibility(View.GONE);
		}
		else{
			_findPanel.setVisibility(View.VISIBLE);
			_findPanel.requestFocus();
		}
	}

	private void openHelp() {
		Intent h = new Intent(this, TextWarriorHelp.class);
		startActivity(h);
	}

	//-----------------------------------------------------------------------
	//--------------------------- Dialog methods ----------------------------

	@Override
	protected Dialog onCreateDialog(int id){
		Dialog dialog = null;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch(id) {
		case DIALOG_OPEN_RECENT:
			dialog = createOpenRecentFileDialog(builder);
			break;

		case DIALOG_PROMPT_SAVE:
			dialog = createPromptSaveDialog(builder);
			break;

		case DIALOG_CONFIRM_OVERWRITE:
			dialog = createConfirmOverwriteDialog(builder);
			break;

		case DIALOG_GOTO_LINE:
			dialog = createGotoLineDialog(builder);
			break;

		case DIALOG_STATISTICS:
			dialog = createStatisticsDialog(builder);
			break;

		case DIALOG_OPEN_AGAIN:
			dialog = createOpenAgainDialog(builder);
			break;

		case DIALOG_SAVE_AGAIN:
			dialog = createSaveAgainDialog(builder);
			break;

		case DIALOG_RECOVERY_FAILED:
			dialog = createRecoveryFailedDialog(builder);
			break;

		case DIALOG_ABOUT:
			dialog = createAboutDialog(builder);
			break;

		default:
			TextWarriorException.fail("Invalid dialog ID");
			break;
		}
		return dialog;
	}


	private Dialog createOpenAgainDialog(AlertDialog.Builder builder) {
		builder.setTitle(_dialogErrorMsg);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setNeutralButton(R.string.dialog_button_go_back,
				new DialogInterface.OnClickListener() {
				@Override
			public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					TextWarriorApplication.this.onOpen();
				}
			});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		return builder.create();
	}

	private Dialog createSaveAgainDialog(AlertDialog.Builder builder) {
		builder.setTitle(_dialogErrorMsg);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setNeutralButton(R.string.dialog_button_save_different_name,
				new DialogInterface.OnClickListener() {
				@Override
			public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					TextWarriorApplication.this.onSaveAs();
				}
			});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		Dialog dialog = builder.create();
		dialog.setOnCancelListener(cancelSaveCallback);
		return dialog;
	}


	private Dialog createConfirmOverwriteDialog(AlertDialog.Builder builder) {
		builder.setMessage(R.string.dialog_confirm_overwrite);
		builder.setPositiveButton(R.string.dialog_button_overwrite,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				TextWarriorApplication.this.save(_lastSelectedFile, true);
			}
		});
		builder.setNeutralButton(R.string.dialog_button_go_back,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				TextWarriorApplication.this.onSaveAs();
			}
		});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		Dialog dialog = builder.create();
		dialog.setOnCancelListener(cancelSaveCallback);
		return dialog;
	}

	private Dialog createPromptSaveDialog(AlertDialog.Builder builder) {
		builder.setTitle(R.string.dialog_prompt_save);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setPositiveButton(R.string.dialog_button_save,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				TextWarriorApplication.this.onSave();
			}
		});
		builder.setNeutralButton(R.string.dialog_button_discard,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				TextWarriorApplication.this.saveFinishedCallback();
			}
		});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		Dialog dialog = builder.create();
		dialog.setOnCancelListener(cancelSaveCallback);
		return dialog;
	}

	private Dialog createGotoLineDialog(AlertDialog.Builder builder) {
		builder.setTitle(R.string.dialog_go_to_line);
		LinearLayout content = (LinearLayout)
				getLayoutInflater().inflate(R.layout.goto_line_dialog_content, null);
		final EditText lineNumberField = (EditText) content.findViewById(
				R.id.dialog_go_to_line_text);
		/* TODO implement imeAction
		lineNumberField.setOnEditorActionListener(new OnEditorActionListener(){
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if(actionId == EditorInfo.IME_ACTION_GO ||
						actionId == EditorInfo.IME_NULL){
					String intStr = lineNumberField.getText().toString();
					if(intStr.length() > 0){
						TextWarriorApplication.this.goToRow(
						Integer.parseInt(intStr) - 1);
					}
					dialog.dismiss();
					return true;
				}
				return false;
			}
		});
		*/

		builder.setView(content);
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				String intStr = lineNumberField.getText().toString();
				if(intStr.length() > 0){
					TextWarriorApplication.this.goToRow(
							Integer.parseInt(intStr) - 1);
				}
				dialog.dismiss();
			}
		});
		builder.setNeutralButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});

		return builder.create();
	}

	// Too bad there is no way to retrieve a Dialog's content view, so it has
	// to be stored as a member variable
	private ViewGroup _recentFilesLayout;
	private ViewGroup _statisticsLayout;

	private Dialog createOpenRecentFileDialog(AlertDialog.Builder builder) {
		builder.setTitle(R.string.dialog_recent_files);
		_recentFilesLayout = (FrameLayout)
			getLayoutInflater().inflate(R.layout.recent_filelist, null);

		builder.setView(_recentFilesLayout);
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		final Dialog dialog = builder.create();

		ListView fileListView =
				(ListView) _recentFilesLayout.findViewById(R.id.recent_files_list);
		IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this);
		fileListView.setAdapter(itla);
		populateRecentFilesDialog(dialog);

		fileListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				IconifiedTextListAdapter adapter =
					(IconifiedTextListAdapter) parent.getAdapter();

				if (adapter == null) {
					return;
				}
				IconifiedText selEntry = (IconifiedText) adapter.getItem(position);
				String selFilename = selEntry.getInfo();
				open(selFilename);
				dialog.dismiss();
			}
		});
		return dialog;
	}

	private void populateRecentFilesDialog(Dialog dialog) {
		List<RecentFiles.RecentFile> fileList = _recentFiles.getRecentFiles();

		ListView fileListView = (ListView)
				_recentFilesLayout.findViewById(R.id.recent_files_list);
		TextView emptyText = (TextView)
				_recentFilesLayout.findViewById(R.id.recent_files_empty);

		if(fileList.size() > 0){
			fileListView.setVisibility(View.VISIBLE);
			emptyText.setVisibility(View.GONE);

			ArrayList<IconifiedText> iconifiedEntries = new ArrayList<IconifiedText>();
			ListIterator<RecentFiles.RecentFile> filesIter = fileList.listIterator();

			Drawable iconFile = getResources().getDrawable(R.drawable.icon_file);
			Drawable iconMissing = getResources().getDrawable(R.drawable.icon_file_missing);
			while(filesIter.hasNext()){
				RecentFiles.RecentFile recentFile = filesIter.next();
				String fullFilename = recentFile.getFileName();
				File file = new File(fullFilename);
				String filename = file.getName();
				Drawable icon = iconFile;

				IconifiedText entry = new IconifiedText(
						filename,
						fullFilename,
						icon);

				if(!file.exists()){
					entry.setSelectable(false);
					entry.setText(filename + " ("
						+ getString(R.string.file_picker_file_not_found)
						+ ")");
					entry.setIcon(iconMissing);
				}
				iconifiedEntries.add(entry);
			}
			IconifiedTextListAdapter adapter =
				(IconifiedTextListAdapter) fileListView.getAdapter();
			adapter.setListItems(iconifiedEntries, false);
			adapter.notifyDataSetChanged();
		}
		else{
			emptyText.setVisibility(View.VISIBLE);
			fileListView.setVisibility(View.GONE);
		}
	}

	private Dialog createStatisticsDialog(AlertDialog.Builder builder) {
		_statisticsLayout = (ScrollView)
			getLayoutInflater().inflate(R.layout.statistics, null);

		builder.setView(_statisticsLayout);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setTitle(R.string.dialog_statistics);
		builder.setNeutralButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		updateStatisticsDialog();
		return builder.create();
	}

	// API 7 Eclair does not support a Bundle argument for showDialog(), so
	// the contents of the statistics dialog has to be saved in a member variable
	// before showDialog() is called, and then accessed here when the dialog
	// is being prepared to be shown.
	/** word count, line count etc. Calculated on demand */
	private CharEncodingUtils.Statistics _statistics = new CharEncodingUtils.Statistics();
	private void updateStatisticsDialog() {
		TextView t = (TextView) _statisticsLayout.findViewById(R.id.statistics_word_count);
		t.setText(Integer.toString(_statistics.wordCount));

		t = (TextView) _statisticsLayout.findViewById(R.id.statistics_char_count);
		t.setText(Integer.toString(_statistics.charCount));

		t = (TextView) _statisticsLayout.findViewById(R.id.statistics_char_no_whitespace_count);
		t.setText(Integer.toString(_statistics.charCount - _statistics.whitespaceCount));

		t = (TextView) _statisticsLayout.findViewById(R.id.statistics_row_count);
		t.setText(Integer.toString(_statistics.lineCount));

		DocumentProvider doc = _editField.createDocumentProvider();

		t = (TextView) _statisticsLayout.findViewById(R.id.statistics_format);
		t.setText(doc.getEncodingScheme());

		t = (TextView) _statisticsLayout.findViewById(R.id.statistics_line_terminator_style);
		t.setText(doc.getEOLType());
	}

	private Dialog createRecoveryFailedDialog(AlertDialog.Builder builder) {
		builder.setTitle(R.string.dialog_sorry);
		builder.setIcon(android.R.drawable.ic_dialog_alert);

		builder.setMessage(_dialogErrorMsg);

		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int id) {
				// this dialog is only needed once in the app lifecycle;
				// removeDialog frees resources while dismiss() merely hides the dialog
				removeDialog(DIALOG_RECOVERY_FAILED);
			}
		});

		builder.setNeutralButton(R.string.menu_help,
				new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int id) {
				removeDialog(DIALOG_RECOVERY_FAILED);
				TextWarriorApplication.this.openHelp();
			}
		});

		Dialog dialog = builder.create();
		return dialog;
	}

	private void prepareRecoveryFailedMessage() {
		String messageSummary = getString(R.string.dialog_sorry_force_resume);
		String messageDetails;

		int errorCode = _recoveryManager.getRecoveryErrorCode();
		switch (errorCode){
		case RecoveryManager.ERROR_RECOVERY_DISABLED:
			messageDetails = getString(R.string.dialog_sorry_backup_disabled);
			break;
		case RecoveryManager.ERROR_FILE_NOT_FOUND:
			messageDetails = getString(R.string.dialog_sorry_missing_recovery,
					_recoveryManager.getExternalRecoveryDir());
			break;
		case RecoveryManager.ERROR_WRITE:
			messageDetails = getString(R.string.dialog_sorry_write_error);
			break;
		case RecoveryManager.ERROR_READ:
			messageDetails = getString(R.string.dialog_sorry_read_error,
					_recoveryManager.getSafekeepingFileAbsolutePath());
			break;
		default:
			TextWarriorException.fail("Unrecognized recovery error code " + errorCode);
			messageDetails = "";
			break;
		}

		_dialogErrorMsg = messageSummary + "\n\n" + messageDetails;
	}


	private Dialog createAboutDialog(AlertDialog.Builder builder) {
		builder.setTitle(R.string.dialog_about);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		ScrollView aboutText = (ScrollView)
			getLayoutInflater().inflate(R.layout.about_dialog_content, null);
		builder.setView(aboutText);
		builder.setNeutralButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
			@Override
		public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		return builder.create();
	}


	@Override
	protected void onPrepareDialog (int id, Dialog dialog){
		switch(id) {
		case DIALOG_OPEN_RECENT:
			populateRecentFilesDialog(dialog);
			break;

		case DIALOG_OPEN_AGAIN: //fall-through
		case DIALOG_SAVE_AGAIN:
			((AlertDialog) dialog).setTitle(_dialogErrorMsg);
			break;

		case DIALOG_STATISTICS:
			updateStatisticsDialog();
			break;

		default:
			//do nothing
			break;
		}
	}

	protected void dismissAllDialogs(){
		for (int dialogId : dialogIds) {
			removeDialog(dialogId);
		}
	}

	//-----------------------------------------------------------------------
	//--------------------- Find and replace methods ------------------------

	/**
	 * Switch focus between the find panel and the main editing area
	 *
	 * @return If the focus was switched successfully between the
	 * find panel and main editing area
	 */
	private boolean togglePanelFocus() {
		if(_findPanel.getVisibility() == View.VISIBLE){
			if(_editField.isFocused()){
				_findPanel.requestFocus();
			}
			else{
				_editField.requestFocus();
			}

			return true;
		}
		return false;
	}

	public void find(String what, boolean isCaseSensitive, boolean isWholeWord){
		if(what.length() > 0){
			int startingPosition =  _editField.isSelectText()
					? _editField.getSelectionStart() + 1
					: _editField.getCaretPosition() + 1;

			_taskFind = FindThread.createFindThread(
					_editField.createDocumentProvider(),
					what,
					startingPosition,
					true,
					isCaseSensitive,
					isWholeWord);
			_taskFind.registerObserver(this);

			PollingProgressDialog dialog = new PollingProgressDialog(this, _taskFind,
					getString(R.string.progress_dialog_find), false, false);
			dialog.startDelayedPollingDialog();
			_taskFind.start();
		}
	}

	public void findBackwards(String what, boolean isCaseSensitive, boolean isWholeWord){
		if(what.length() > 0){
			int startingPosition = _editField.isSelectText()
					? _editField.getSelectionStart() - 1
					: _editField.getCaretPosition() - 1;

			_taskFind = FindThread.createFindThread(
					_editField.createDocumentProvider(),
					what,
					startingPosition,
					false,
					isCaseSensitive,
					isWholeWord);
			_taskFind.registerObserver(this);

			PollingProgressDialog dialog = new PollingProgressDialog(this, _taskFind,
					getString(R.string.progress_dialog_find), false, false);
			dialog.startDelayedPollingDialog();
			_taskFind.start();
		}
	}

	public void replaceSelection(String replacementText){
		if(_editField.isSelectText()){
			_editField.paste(replacementText);
		}
		else{
			Toast.makeText(TextWarriorApplication.this,
					R.string.dialog_replace_no_selection,
					Toast.LENGTH_SHORT).show();
		}
	}

	public void replaceAll(String what, String replacementText,
			boolean isCaseSensitive, boolean isWholeWord){
		if(what.length() > 0){
			int startingPosition = _editField.getCaretPosition();
			_taskFind = FindThread.createReplaceAllThread(
					_editField.createDocumentProvider(),
					what,
					replacementText,
					startingPosition,
					isCaseSensitive,
					isWholeWord);
			_taskFind.registerObserver(this);

			PollingProgressDialog dialog = new PollingProgressDialog(this, _taskFind,
					getString(R.string.progress_dialog_replace), true, false);
			dialog.startDelayedPollingDialog();
			_taskFind.start();
		}
	}

	//-----------------------------------------------------------------------
	//----------------------- Open and save methods -------------------------

	private Uri getLastSelectedUri(){
		if(_lastSelectedFile == null){
			return null;
		}

		Uri.Builder ub = new Uri.Builder();
		ub.scheme("file://");
		ub.appendPath(_lastSelectedFile);
		return ub.build();
	}

	public void open(String filename){
		_lastSelectedFile = filename;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String encoding = prefs.getString(
				getString(R.string.settings_key_file_input_format),
				getString(R.string.settings_file_input_format_default));
		String eolChar = prefs.getString(
				getString(R.string.settings_key_line_terminator_style),
				getString(R.string.settings_line_terminator_style_default));

		File inputFile = new File(filename);
		_inputingDoc = new Document(_editField);
		_inputingDoc.setWordWrap(isWordWrap());
		_taskRead = new ReadThread(inputFile, _inputingDoc, encoding, eolChar);
		_taskRead.registerObserver(this); // so that readTask can notify TextWarriorApplication when done

		PollingProgressDialog dialog = new PollingProgressDialog(this, _taskRead,
				getString(R.string.progress_dialog_open), true, true);
		dialog.startDelayedPollingDialog();
		_taskRead.start();
	}


	/**
	 * Preconditions:
	 * 1. filename is not a directory
	 * 2. filename does not contain illegal symbols used by the file system
	 *	(For example, in FAT systems, <>!* are not allowed in filenames)
	 * 3. if filename refers to a file that has not been created yet,
	 *	the user has write access to the containing directory
	 *
	 */
	public void save(String filename, boolean overwrite){
		_lastSelectedFile = filename;
		File outputFile = new File(filename);

		if(_recoveryManager.isInRecoveryPath(outputFile)){
			_dialogErrorMsg = getString(R.string.dialog_error_attempt_overwrite_recovery);
			showDialog(DIALOG_SAVE_AGAIN);
			return;
		}

		if(outputFile.exists()){
			if(!outputFile.canWrite()){
				_dialogErrorMsg = getString(
						R.string.dialog_error_attempt_overwrite_read_only);
				showDialog(DIALOG_SAVE_AGAIN);
				return;
			}

			if(!overwrite){
				showDialog(DIALOG_CONFIRM_OVERWRITE);
				return;
			}
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String encoding = prefs.getString(
				getString(R.string.settings_key_file_output_format),
				getString(R.string.settings_file_output_format_default));
		String eolChar = prefs.getString(
				getString(R.string.settings_key_line_terminator_style),
				getString(R.string.settings_line_terminator_style_default));

		_taskWrite = new WriteThread(outputFile,
				_editField.createDocumentProvider(), encoding, eolChar);
		_taskWrite.registerObserver(this);

		PollingProgressDialog dialog = new PollingProgressDialog(this, _taskWrite,
				getString(R.string.progress_dialog_save), true, true);
		dialog.startDelayedPollingDialog();
		_taskWrite.start();
	}

	private void displayOpenError(String msg) {
		Log.e(LOG_TAG, msg);
		_dialogErrorMsg = getString(R.string.dialog_error_file_open) + msg;
		showDialog(DIALOG_OPEN_AGAIN);
	}

	private void displaySaveError(String msg){
		Log.e(LOG_TAG, msg);
		_dialogErrorMsg = getString(R.string.dialog_error_file_save) + msg;
		//TODO add hints on how how to resolve error.
		//see R.string.dialog_msg_save_file_error_recovery_tips
		showDialog(DIALOG_SAVE_AGAIN);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode){
		case TextWarriorIntents.REQUEST_PICK_FILE:
			if(resultCode == RESULT_OK){
				open(data.getData().getPath());
			}
			break;
		case TextWarriorIntents.REQUEST_PICK_FILENAME_FOR_SAVE:
			if(resultCode == RESULT_OK){
				save(data.getData().getPath(), false);
			}
			else{
				//save cancelled by user; remove pending callback
				_saveFinishedCallback = CALLBACK_NONE;
			}
			break;
		default:
			super.onActivityResult(requestCode, resultCode, data);
		}
	}


	@Override
	//This method is called by various worker threads
	public void onComplete(final int requestCode, final Object result) {
		runOnUiThread(new Runnable(){
		@Override
		public void run(){
			if(requestCode == ProgressSource.READ){
				//TODO save view settings of previous file if it was clean
				changeModel(_inputingDoc);
				_filename = _lastSelectedFile;
				_recentFiles.addRecentFile(_lastSelectedFile);
				//TODO restore view settings of new file
				updateTitle();
				_inputingDoc = null;
				_taskRead = null;
			}

			else if(requestCode == ProgressSource.WRITE){
				//TODO restore view settings of previous file

				_filename = _lastSelectedFile;
				updateTitle();
				_recentFiles.addRecentFile(_filename);
				_editField.setEdited(false);
				Toast.makeText(TextWarriorApplication.this,
						R.string.dialog_file_save_success,
						Toast.LENGTH_SHORT).show();
				saveFinishedCallback();
				_taskWrite = null;
			}

			else if(requestCode == ProgressSource.FIND
					|| requestCode == ProgressSource.FIND_BACKWARDS){
				final int foundIndex = ((FindResults) result).foundOffset;
				final int length = ((FindResults) result).searchTextLength;

				if (foundIndex != -1){
					_editField.setSelectionRange(foundIndex, length);
				}
				else{
					Toast.makeText(TextWarriorApplication.this,
							R.string.dialog_find_no_results,
							Toast.LENGTH_SHORT).show();
				}
				_taskFind = null;
			}

			else if(requestCode == ProgressSource.REPLACE_ALL){
				final int replacementCount = ((FindResults) result).replacementCount;
				final int newCaretPosition = ((FindResults) result).newStartPosition;
				if(replacementCount > 0){
					_editField.setEdited(true);
					_editField.selectText(false);
					_editField.moveCaret(newCaretPosition);
					_editField.respan();
					_editField.invalidate(); //TODO reduce invalidate calls
				}
				Toast.makeText(TextWarriorApplication.this,
						getString(R.string.dialog_replace_all_result) + replacementCount,
						Toast.LENGTH_SHORT).show();
				_taskFind = null;
			}
			else if(requestCode == ProgressSource.ANALYZE_TEXT){
				_statistics = (CharEncodingUtils.Statistics) result;
				_taskAnalyze = null;
				showDialog(DIALOG_STATISTICS);
			}
		}
		});
	}

	@Override
	public void onError(final int requestCode, final int errorCode,
			final String message) {
		runOnUiThread(new Runnable(){
		@Override
		public void run(){
			if(requestCode == ProgressSource.READ){
				_taskRead = null;
				displayOpenError(message);
			}
			else if(requestCode == ProgressSource.WRITE){
				_taskWrite = null;
				displaySaveError(message);
			}
			else if(requestCode == ProgressSource.FIND ||
					requestCode == ProgressSource.FIND_BACKWARDS ||
					requestCode == ProgressSource.REPLACE_ALL){
				_taskFind = null;
			}
			else if(requestCode == ProgressSource.ANALYZE_TEXT){
				_taskAnalyze = null;
			}
		}
		});
	}

	@Override
	public void onCancel(int requestCode) {
		if(requestCode == ProgressSource.READ){
			_taskRead = null;
		}
		else if(requestCode == ProgressSource.WRITE){
			_taskWrite = null;
		}
		else if(requestCode == ProgressSource.FIND ||
				requestCode == ProgressSource.FIND_BACKWARDS ||
				requestCode == ProgressSource.REPLACE_ALL){
			_taskFind = null;
		}
		else if(requestCode == ProgressSource.ANALYZE_TEXT){
			_taskAnalyze = null;
		}
	}


	protected void saveFinishedCallback() {
		if(_saveFinishedCallback == SAVE_CALLBACK_NEW){
			onNew();
		}
		else if (_saveFinishedCallback == SAVE_CALLBACK_OPEN_RECENT){
			onOpenRecent();
		}
		else if (_saveFinishedCallback == SAVE_CALLBACK_OPEN){
			onOpen();
		}
		else if (_saveFinishedCallback == SAVE_CALLBACK_EXIT){
			finish();
		}
		else if (_saveFinishedCallback == SAVE_CALLBACK_SINGLE_TASK_OPEN){
			open(getIntent().getData().getPath());
		}
		_saveFinishedCallback = CALLBACK_NONE;
	}


	//-----------------------------------------------------------------------
	//------------------------- UI event handlers ---------------------------

	@Override
	public void onRowChange(int newRowIndex) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		boolean showLineNumbers = pref.getBoolean(
				getString(R.string.settings_key_show_row_number),
				getResources().getBoolean(R.bool.settings_show_row_number_default));
		if(showLineNumbers){
			// change from 0-based to 1-based indexing
			setTitle(createTitle(newRowIndex + 1));
		}
	}

	@Override
	public void onSelectionModeChanged(boolean active) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if(active && _editField.isFocused() && !_clipboardPanel.isOpen()){
			boolean autoOpen = pref.getBoolean(
					getString(R.string.settings_key_auto_open_clipboard),
					getResources().getBoolean(R.bool.settings_auto_open_clipboard_default));
			if(autoOpen){
				_clipboardPanel.setOpen(true, true);
			}
		}
		else if(!active && _editField.isFocused() && _clipboardPanel.isOpen()){
			boolean autoClose = pref.getBoolean(
					getString(R.string.settings_key_auto_close_clipboard),
					getResources().getBoolean(R.bool.settings_auto_close_clipboard_default));
			if(autoClose){
				_clipboardPanel.setOpen(false, true);
			}
		}
		updateClipboardButtons();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		boolean handled = false;

		// Intercept keystroke shortcuts
		if (KeysInterpreter.isSwitchPanel(event) &&
				event.getAction() == KeyEvent.ACTION_DOWN){
			//the view gaining focus must be able to ignore the corresponding
			//key up event sent to it when the key is released
			handled = togglePanelFocus();
		}

		if(!handled){
			handled = super.dispatchKeyEvent(event);
		}
		return handled;
	}


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_SEARCH){
			// handle accidental touching of virtual hard keys as described in
			// http://android-developers.blogspot.com/2009/12/back-and-other
			// -hard-keys-three-stories.html
			event.startTracking();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event){
		if (keyCode == KeyEvent.KEYCODE_SEARCH &&
				!event.isCanceled() &&
				event.isTracking()){
			toggleFindPanel();
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		if(_editField.isEdited()){
			_saveFinishedCallback = SAVE_CALLBACK_EXIT;
			showDialog(DIALOG_PROMPT_SAVE);
		}
		else{
			finish();
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences pref,
			String key) {

		if(key.equals(getString(R.string.settings_key_zoom_size))){
			setZoom(pref);
		}
		else if(key.equals(getString(R.string.settings_key_word_wrap))){
			setWordWrap(pref);
		}
		else if(key.equals(getString(R.string.settings_key_font))){
			setFont(pref);
		}
		else if(key.equals(getString(R.string.settings_key_navigation_method))){
			setNavigationMethod(pref);
		}
		else if(key.equals(getString(R.string.settings_key_color_scheme))){
			setColorScheme(pref);
		}
		else if(key.equals(getString(R.string.settings_key_syntax_color))){
			setSyntaxColor(pref);
		}
		else if(key.equals(getString(R.string.settings_key_tab_spaces))){
			setTabSpaces(pref);
		}
		else if(key.equals(getString(R.string.settings_key_show_row_number))){
			updateTitle();
		}
		else if(key.equals(getString(R.string.settings_key_highlight_current_row))){
			setHighlightCurrentRow(pref);
		}
		else if(key.equals(getString(R.string.settings_key_auto_indent))){
			setAutoIndent(pref);
		}
		else if(key.equals(getString(R.string.settings_key_long_press_capitalize))){
			setLongPressCaps(pref);
		}
		else if(key.equals(getString(R.string.settings_key_show_nonprinting))){
			setNonPrintingCharVisibility(pref);
		}
		else if(key.equals(getString(R.string.settings_key_auto_backup))){
			onAutoBackupChanged(pref);
		}
		else if(key.equals(getString(R.string.settings_key_chirality))){
			setChirality(pref);
		}
	}



	//-----------------------------------------------------------------------
	//----------------------- Preference settings ---------------------------

	private void setColorScheme(SharedPreferences pref){
		String colorSchemeKey = getString(R.string.settings_key_color_scheme);
		String colorSchemeName = pref.getString(colorSchemeKey, getString(R.string.settings_color_scheme_default));
		ColorScheme colorScheme;

		if (colorSchemeName.equals(getString(R.string.settings_color_scheme_light))){
			colorScheme = new ColorSchemeLight();
		}
		else if (colorSchemeName.equals(getString(R.string.settings_color_scheme_dark))){
			colorScheme = new ColorSchemeDark();
		}
		else if (colorSchemeName.equals(getString(R.string.settings_color_scheme_solarized_light))){
			colorScheme = new ColorSchemeSolarizedLight();
		}
		else if (colorSchemeName.equals(getString(R.string.settings_color_scheme_solarized_dark))){
			colorScheme = new ColorSchemeSolarizedDark();
		}
		else if (colorSchemeName.equals(getString(R.string.settings_color_scheme_obsidian))){
			colorScheme = new ColorSchemeObsidian();
		}
		else{
			TextWarriorException.fail("Unsupported color scheme");
			return;
		}

		_editField.setColorScheme(colorScheme);
		_clipboardPanel.setColorScheme(colorScheme);
	}

	private void setSyntaxColor(SharedPreferences pref){
		String syntaxKey = getString(R.string.settings_key_syntax_color);
		String lang = pref.getString(syntaxKey, getString(R.string.settings_syntax_color_default));

		if (lang.equals(getString(R.string.settings_syntax_c))){
			Lexer.setLanguage(LanguageC.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_cpp))){
			Lexer.setLanguage(LanguageCpp.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_csharp))){
			Lexer.setLanguage(LanguageCsharp.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_java))){
			Lexer.setLanguage(LanguageJava.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_javascript))){
			Lexer.setLanguage(LanguageJavascript.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_objc))){
			Lexer.setLanguage(LanguageObjectiveC.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_php))){
			Lexer.setLanguage(LanguagePHP.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_python))){
			Lexer.setLanguage(LanguagePython.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_ruby))){
			Lexer.setLanguage(LanguageRuby.getInstance());
			_editField.respan();
		}
		else if (lang.equals(getString(R.string.settings_syntax_none))){
			Lexer.setLanguage(LanguageNonProg.getInstance());
			_editField.cancelSpanning();
			_editField.createDocumentProvider().clearSpans();
		}
		else{
			TextWarriorException.fail("Unsupported language for syntax highlighting");
		}
	}

	private void setNavigationMethod(SharedPreferences pref){
		String navKey = getString(R.string.settings_key_navigation_method);
		String nav = pref.getString(navKey, getString(R.string.settings_navigation_method_default));

		if (nav.equals(getString(R.string.settings_navigation_method_basic))){
			_editField.setNavigationMethod(new TouchNavigationMethod(_editField));
		}
		else if (nav.equals(getString(R.string.settings_navigation_method_trackpad))){
			_editField.setNavigationMethod(new TrackpadNavigationMethod(_editField));
		}
		else if (nav.equals(getString(R.string.settings_navigation_method_vol_keys))){
			_editField.setNavigationMethod(new VolumeKeysNavigationMethod(_editField));
		}
		else if (nav.equals(getString(R.string.settings_navigation_method_yoyo))){
			_editField.setNavigationMethod(new YoyoNavigationMethod(_editField));
		}
		else if (nav.equals(getString(R.string.settings_navigation_method_liquid))){
			_editField.setNavigationMethod(new LiquidNavigationMethod(_editField));
		}
		else{
			TextWarriorException.fail("Unsupported navigation method");
		}
	}

	private void setChirality(SharedPreferences pref) {
		String chiralityKey = getString(R.string.settings_key_chirality);
		String chirality = pref.getString(chiralityKey, getString(R.string.settings_chirality_default));

		_editField.setChirality(chirality.equals(getString(R.string.settings_chirality_right)));
	}

	private final static String FONT_PATH_BITSTREAM_VERA = "typefaces/VeraMono.ttf";
	private final static String FONT_PATH_PROGGY_CLEAN = "typefaces/ProggyCleanSZ.ttf";
	private void setFont(SharedPreferences pref){
		String fontKey = getString(R.string.settings_key_font);
		String font = pref.getString(fontKey, getString(R.string.settings_font_default));

		if (font.equals(getString(R.string.settings_font_sans_serif))){
			_editField.setTypeface(Typeface.SANS_SERIF);
		}
		else if (font.equals(getString(R.string.settings_font_serif))){
			_editField.setTypeface(Typeface.SERIF);
		}
		else if (font.equals(getString(R.string.settings_font_monospace))){
			_editField.setTypeface(Typeface.MONOSPACE);
		}
		else if (font.equals(getString(R.string.settings_font_bitstream_vera))){
			Typeface t = Typeface.createFromAsset(getAssets(), FONT_PATH_BITSTREAM_VERA);
			_editField.setTypeface(t);
		}
		else if (font.equals(getString(R.string.settings_font_proggy_clean))){
			Typeface t = Typeface.createFromAsset(getAssets(), FONT_PATH_PROGGY_CLEAN);
			_editField.setTypeface(t);
		}
		else{
			TextWarriorException.fail("Unsupported font");
		}
	}

	private void setZoom(SharedPreferences pref){
		String zoomKey = getString(R.string.settings_key_zoom_size);
		String zoomStr = pref.getString(zoomKey,
				getString(R.string.settings_zoom_size_default));
		_editField.setZoom(Integer.parseInt(zoomStr) / 100.0f);
	}

	private void setWordWrap(SharedPreferences pref){
		_editField.setWordWrap(isWordWrap());
	}

	public boolean isWordWrap(){
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		String wordWrapKey = getString(R.string.settings_key_word_wrap);
		return pref.getBoolean(wordWrapKey,
				getResources().getBoolean(R.bool.settings_word_wrap_default));
	}

	private void setTabSpaces(SharedPreferences pref){
		String tabSpacesKey = getString(R.string.settings_key_tab_spaces);
		String tabSpacesStr = pref.getString(tabSpacesKey,
				getString(R.string.settings_tab_spaces_default));
		_editField.setTabSpaces(Integer.parseInt(tabSpacesStr));
	}

	private void setHighlightCurrentRow(SharedPreferences pref){
		String highlightKey = getString(R.string.settings_key_highlight_current_row);
		boolean isHighlight = pref.getBoolean(highlightKey,
				getResources().getBoolean(R.bool.settings_highlight_current_row_default));
		_editField.setHighlightCurrentRow(isHighlight);
	}

	private void setNonPrintingCharVisibility(SharedPreferences pref) {
		String showKey = getString(R.string.settings_key_show_nonprinting);
		boolean isShow = pref.getBoolean(showKey,
				getResources().getBoolean(R.bool.settings_show_nonprinting_default));
		_editField.setNonPrintingCharVisibility(isShow);
	}

	private void setAutoIndent(SharedPreferences pref){
		String autoIndentKey = getString(R.string.settings_key_auto_indent);
		boolean isAutoIndent = pref.getBoolean(autoIndentKey,
				getResources().getBoolean(R.bool.settings_auto_indent_default));
		_editField.setAutoIndent(isAutoIndent);
	}

	private void setLongPressCaps(SharedPreferences pref){
		String longPressCapsKey = getString(R.string.settings_key_long_press_capitalize);
		boolean isLongPressCaps = pref.getBoolean(longPressCapsKey,
				getResources().getBoolean(R.bool.settings_long_press_capitalize_default));
		_editField.setLongPressCaps(isLongPressCaps);
	}

	private void onAutoBackupChanged(SharedPreferences pref){
		_recoveryManager.clearRecoveryState();
	}

	private boolean isAutoBackup() {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		return pref.getBoolean(getString(R.string.settings_key_auto_backup),
				getResources().getBoolean(R.bool.settings_auto_backup_default));
	}


	//-----------------------------------------------------------------------
	//------------------- Android lifecycle methods -------------------------

	@Override
	protected void onPause() {
		super.onPause();
		_recentFiles.save();
		_editField.onPause();
	}


	@Override
	protected void onResume() {
		super.onResume();
		_editField.onResume();
	}


	@Override
	protected void onDestroy() {
		_editField.onDestroy();

		if(isFinishing()){
			// Scenario 1: killed normally by user
			stopAllWorkerThreads();

			//TODO if(!dirty){ save view settings; }
		}
		else{
			// Scenario 2: killed by system because device configuration changed

			// leave existing worker threads running, to be re-attached to
			// observer UI elements when the activity is re-created
			if(_taskRead != null) { _taskRead.removeObservers(); }
			if(_taskWrite != null) { _taskWrite.removeObservers(); }
			if(_taskFind != null) { _taskFind.removeObservers(); }
			if(_taskAnalyze != null) { _taskAnalyze.removeObservers(); }
		}

		super.onDestroy();
	}

	private void stopAllWorkerThreads(){
		if(_taskRead != null) {
			_taskRead.removeObservers();
			_taskRead.forceStop();
		}
		if(_taskWrite != null) {
			_taskWrite.removeObservers();
			_taskWrite.forceStop();
		}
		if(_taskFind != null) {
			_taskFind.removeObservers();
			_taskFind.forceStop();
		}
		if(_taskAnalyze != null) {
			_taskAnalyze.removeObservers();
			_taskAnalyze.forceStop();
		}
	}

	/**
	 * Contains application state that is expensive to reconstruct after a
	 * configuration change. Also contains non-UI state.
	 */
	private static class NonConfigurationState{
		DocumentProvider mDocProvider;
		Document mTempDoc;
		ReadThread mReadTask;
		WriteThread mWriteTask;
		FindThread mFindTask;
		AnalyzeStatisticsThread mAnalyzeTask;
		CharEncodingUtils.Statistics mStatistics;

		boolean mDirty;
		int mSaveCallback;
		String mFilename;
		String mPrevFilename;
		String mDialogErrMsg;
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		NonConfigurationState s = new NonConfigurationState();
		s.mDocProvider = _editField.createDocumentProvider();
		s.mTempDoc = _inputingDoc;

		//remove ref to FreeScrollingTextField from Document to prevent context leak
		s.mDocProvider.setMetrics(null);
		if(_inputingDoc != null){
			s.mTempDoc.setMetrics(null);
		}

		s.mReadTask = _taskRead;
		s.mWriteTask = _taskWrite;
		s.mFindTask = _taskFind;
		s.mAnalyzeTask = _taskAnalyze;
		s.mStatistics = _statistics;

		s.mDirty = _editField.isEdited();
		s.mSaveCallback = _saveFinishedCallback;
		s.mFilename = _filename;
		s.mPrevFilename = _lastSelectedFile;
		s.mDialogErrMsg = _dialogErrorMsg;
		return s;
	}

	private void restoreNonConfigurationState(NonConfigurationState ncState) {
		if(ncState != null){
			//attach newly configured FreeScrollingTextField
			ncState.mDocProvider.setMetrics(_editField);
			if(ncState.mTempDoc != null){
				ncState.mTempDoc.setMetrics(_editField);
			}

			_editField.setDocumentProvider(ncState.mDocProvider);
			_editField.setEdited(ncState.mDirty);
			_filename = ncState.mFilename;
			_lastSelectedFile = ncState.mPrevFilename;
			_dialogErrorMsg = ncState.mDialogErrMsg;
			_saveFinishedCallback = ncState.mSaveCallback;
			_inputingDoc = ncState.mTempDoc;
			_statistics = ncState.mStatistics;

			restoreDisplayedDialogs(ncState);
		}
	}


	// The only sure way of saving edited text to a temp (rescue) file is in
	// onPause() or onSaveInstanceState(). However, this is problematic because
	// saving of files can take an arbitrarily long time, and these methods are
	// supposed to be lightweight. Therefore, the user gets to decide through
	// an app setting if auto-backup should take place every time the app is paused.

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(STATE_APP_UI, new AppUiState(this));

		if(!_editField.hasLayout() && _initBundle != null){
			// _editField is still being initialized and is in an inconsistent state
			// Use the previous UI state instead
			// Log.d(LOG_TAG, "Text field is still being created. Saving previous UI state.");
			outState.putParcelable(STATE_TEXT_UI, _initBundle.getParcelable(STATE_TEXT_UI));
		}
		else {
			outState.putParcelable(STATE_TEXT_UI, _editField.getUiState());
		}

		if(isAutoBackup()){
			_recoveryManager.backup(_editField, _filename);
		}
	}



	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		_recoveryManager.clearRecoveryState();
	}

	private void restoreUiState(Bundle uiState) {
		_editField.restoreUiState(uiState.getParcelable(STATE_TEXT_UI));

		AppUiState appUiState = (AppUiState) uiState.getParcelable(STATE_APP_UI);
		if(appUiState.clipboardOpen){
			_clipboardPanel.setOpen(true, false); //closed by default
		}
		_findPanel.setVisibility(appUiState.findPanelVisibility);
	}

	private void restoreDisplayedDialogs(NonConfigurationState ss) {
		_taskRead = ss.mReadTask;
		_taskWrite = ss.mWriteTask;
		_taskFind = ss.mFindTask;
		_taskAnalyze = ss.mAnalyzeTask;

		// Observers previously attached to worker threads are invalid after
		// the activity restarts.
		// Reassign listeners and show progress dialogs if worker threads are active

		//FIXME There is a small possibility that worker tasks might signal an
		// error or cancel event in the interval between onSaveInstanceState() and
		// onRestoreInstanceState()/onCreate(). Hence, the error/cancel message
		// is lost. To solve this, worker tasks have to implement additional
		// error/cancel states that can be queried here.
		if(_taskRead != null){
			if(_taskRead.isDone()){
				onComplete(ProgressSource.READ, null);
			}
			else{
				_taskRead.registerObserver(this);
				PollingProgressDialog dialog = new PollingProgressDialog(this, _taskRead,
						getString(R.string.progress_dialog_open), true, true);
				dialog.startPollingDialog();
			}
		}

		else if(_taskWrite != null){
			if(_taskRead.isDone()){
				onComplete(ProgressSource.WRITE, null);
			}
			else{
				_taskWrite.registerObserver(this);
				PollingProgressDialog dialog = new PollingProgressDialog(this, _taskWrite,
						getString(R.string.progress_dialog_save), true, true);
				dialog.startPollingDialog();
			}
		}

		else if(_taskFind != null){
			if(_taskAnalyze.isDone()){
				onComplete(_taskFind.getRequestCode(), _taskFind.getResults());
			}
			else{
				_taskFind.registerObserver(this);
				PollingProgressDialog dialog;

				if(_taskFind.getRequestCode() == ProgressSource.REPLACE_ALL){
					dialog = new PollingProgressDialog(this, _taskFind, null, true, false);
				}
				else{
					dialog = new PollingProgressDialog(this, _taskFind,
							getString(R.string.progress_dialog_find), false, false);
				}
				dialog.startPollingDialog();
			}
		}

		else if(_taskAnalyze != null){
			if(_taskAnalyze.isDone()){
				onComplete(ProgressSource.ANALYZE_TEXT, _taskAnalyze.getResults());
			}
			else{
				_taskAnalyze.registerObserver(this);
				PollingProgressDialog dialog = new PollingProgressDialog(this, _taskAnalyze,
						getString(R.string.progress_dialog_analyze), true, true);
				dialog.startPollingDialog();
			}
		}
	}

	private static class AppUiState implements Parcelable {
		final int findPanelVisibility;
		final boolean clipboardOpen;

		@Override
		public int describeContents() {
			return 0;
		}

		public AppUiState(TextWarriorApplication app) {
			findPanelVisibility = app._findPanel.getVisibility();
			clipboardOpen = app._clipboardPanel.isOpen();

		}

		private AppUiState(Parcel in) {
			findPanelVisibility = in.readInt();
			clipboardOpen = in.readInt() != 0;
		}

		@Override
		public void writeToParcel(Parcel out, int flags){
			out.writeInt(findPanelVisibility);
			out.writeInt(clipboardOpen ? 1 : 0);
		}

		public static final Parcelable.Creator<AppUiState> CREATOR
				 = new Parcelable.Creator<AppUiState>() {
			@Override
			public AppUiState createFromParcel(Parcel in) {
				return new AppUiState(in);
			}

			@Override
			public AppUiState[] newArray(int size) {
				return new AppUiState[size];
			}
		};
	}

	private ReadThread _taskRead = null;
	private WriteThread _taskWrite = null;
	private FindThread _taskFind = null;
	private AnalyzeStatisticsThread _taskAnalyze = null;

	static final String LOG_TAG = "TextWarrior";

	private static final int DIALOG_OPEN_AGAIN = 0;
	private static final int DIALOG_SAVE_AGAIN = 1;
	private static final int DIALOG_CONFIRM_OVERWRITE = 2;
	private static final int DIALOG_PROMPT_SAVE = 3;
	private static final int DIALOG_OPEN_RECENT = 4;
	private static final int DIALOG_GOTO_LINE = 5;
	private static final int DIALOG_STATISTICS = 6;
	private static final int DIALOG_ABOUT = 7;
	private static final int DIALOG_RECOVERY_FAILED = 8;
	private static final int[] dialogIds = {
		DIALOG_OPEN_AGAIN, DIALOG_SAVE_AGAIN, DIALOG_CONFIRM_OVERWRITE,
		DIALOG_PROMPT_SAVE, DIALOG_OPEN_RECENT, DIALOG_GOTO_LINE,
		DIALOG_STATISTICS, DIALOG_ABOUT, DIALOG_RECOVERY_FAILED
	}; // remember to modify this when dialog IDs are added/dropped

	private static final int SAVE_CALLBACK_NEW = 1;
	private static final int SAVE_CALLBACK_OPEN = 2;
	private static final int SAVE_CALLBACK_OPEN_RECENT = 3;
	private static final int SAVE_CALLBACK_EXIT = 4;

	/** used when TextWarrior is already active and another task wants to open a file with it */
	private static final int SAVE_CALLBACK_SINGLE_TASK_OPEN = 5;
	private static final int CALLBACK_NONE = -1;

	private final static String STATE_APP_UI = "appState";
	private final static String STATE_TEXT_UI = "textUiState";
}
