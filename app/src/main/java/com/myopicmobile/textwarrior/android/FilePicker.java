/*
 * Copyright (C) 2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Based on AndDev.org's file browser V 2.0.
 */

/*
 * @author Tah Wei Hoon
 * @date 30 Dec 2010
 *
 * Main Changes
 * - Back button exits activity instead of going up the directory tree
 *
 * - Removed file management functions, dialogs and menu items for renaming, copying, etc.
 *
 * - Removed option to show the directory line as input box instead of button row
 *
 * - Removed EulaActivity, ThumbnailLoader, context menu, mime type handling,
 * 		STATE_BROWSE, STATE_PICK_DIRECTORY, handling changed icons, jumpTo(File)
 *
 * - Added ACTION_PICK_FILENAME_FOR_SAVE: Browse to a directory and select an
 * 		existing filename or enter a new filename
 *
 * - In STATE_PICK_FILE, selecting a file exits FilePicker and returns the
 * 		filepath to the calling activity, instead of populating mEditFilename
 *
 * - In STATE_ENTER_FILENAME, selecting a file populates mEditFilename.
 *
 * - Selected files will never launch an intent to open them.
 *
 * - Grouped mEditFilename and mButtonPick into a LinearLayout
 *
 * - Changed icons and made them bigger. See res/
 */

package com.myopicmobile.textwarrior.android;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.openintents.filemanager.DirectoryContents;
import org.openintents.filemanager.IconifiedText;
import org.openintents.filemanager.IconifiedTextListAdapter;
import org.openintents.filemanager.util.FileUtils;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class FilePicker extends ListActivity {
    static final public int MESSAGE_SHOW_DIRECTORY_CONTENTS = 500;    // List of contents is ready, obj = DirectoryContents
    static final public int MESSAGE_SET_PROGRESS = 501; // Set progress bar, arg1 = current value, arg2 = max value

    private static final String ROOT_DIR = "/";
    private static final String PATH_SEPARATOR = "/";

    private int mState;

    /**
     * Browse a dir tree and select a file by clicking on it
     */
    private static final int STATE_PICK_FILE = 1;
    /**
     * Browse to the dir where the file is to be saved in.
     * An EditText box is available for the user to type in the filename
     */
    private static final int STATE_ENTER_FILENAME = 2;

    private static final String BUNDLE_CURRENT_DIRECTORY = "current_directory";
    private static final String BUNDLE_TEXTBOX_CONTENTS = "context_file";

    /**
     * Contains directories and files together
     */
    private final ArrayList<IconifiedText> directoryEntries = new ArrayList<>();

    /**
     * Dir separate for sorting
     */
    List<IconifiedText> mListDir = new ArrayList<>();

    /**
     * Files separate for sorting
     */
    List<IconifiedText> mListFile = new ArrayList<>();

    /**
     * SD card separate for sorting
     */
    List<IconifiedText> mListSdCard = new ArrayList<>();

    private File mCurrentDirectory = new File("");

    private String mSdCardPath = "";

    private LinearLayout mFilenameBar;
    private EditText mEditFilename;
    private Button mButtonPick;
    private LinearLayout mDirectoryButtons;

    private TextView mEmptyText;
    private ProgressBar mProgressBar;

    private DirectoryScanner mDirectoryScanner;
    private File mPreviousDirectory;

    private FilePickerHandler mCurrentHandler;

    private static class FilePickerHandler extends Handler {
        private final WeakReference<FilePicker> _filePickerRef;

        public FilePickerHandler(FilePicker h) {
            _filePickerRef = new WeakReference<>(h);
        }

        @Override
        public void handleMessage(Message msg) {
            _filePickerRef.get().handleMessage(msg);
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mCurrentHandler = new FilePickerHandler(this);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.filelist);

        mEmptyText = findViewById(R.id.empty_text);
        mProgressBar = findViewById(R.id.scan_progress);

        getListView().setEmptyView(findViewById(R.id.empty));
        getListView().setTextFilterEnabled(true);
        getListView().requestFocus();
        getListView().requestFocusFromTouch();

        mDirectoryButtons = findViewById(R.id.directory_buttons);

        mEditFilename = findViewById(R.id.filename);
        mButtonPick = findViewById(R.id.button_pick);
        mFilenameBar = findViewById(R.id.enter_filename_bar);
        mFilenameBar.setVisibility(View.GONE); // display only when dir contents are loaded

        mButtonPick.setOnClickListener(view -> pickEnteredFilename());

        getSdCardPath();

        Intent intent = getIntent();
        String action = intent.getAction();

        File browseto = new File(ROOT_DIR);

        if (!TextUtils.isEmpty(mSdCardPath)) {
            browseto = new File(mSdCardPath);
        }

        // Default state
        mState = STATE_ENTER_FILENAME;

        if (action.equals(TextWarriorIntents.ACTION_PICK_FILE)) {
            mState = STATE_PICK_FILE;

        } else if (action.equals(TextWarriorIntents.ACTION_PICK_FILENAME_FOR_SAVE)) {
            mState = STATE_ENTER_FILENAME;
        } else {
            Log.w(this.toString(), "No action specified. Default to name file mode");
        }

        // Set current directory based on intent data.
        File file = FileUtils.getFile(intent.getData());
        if (file != null) {
            File displayedDir = FileUtils.getPathWithoutFilename(file);
            if (displayedDir.isDirectory()) {
                browseto = displayedDir;
            }
            if (!file.isDirectory()) {
                mEditFilename.setText(file.getName());
            }
        }

        String title = intent.getStringExtra(TextWarriorIntents.EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }

        String buttontext = intent.getStringExtra(TextWarriorIntents.EXTRA_BUTTON_TEXT);
        if (buttontext != null) {
            mButtonPick.setText(buttontext);
        }

        if (icicle != null) {
            browseto = new File(icicle.getString(BUNDLE_CURRENT_DIRECTORY));
            mEditFilename.setText(icicle.getString(BUNDLE_TEXTBOX_CONTENTS));
            refreshDirectoryPanel();
        }

        browseTo(browseto);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop the scanner.
        DirectoryScanner scanner = mDirectoryScanner;

        if (scanner != null) {
            scanner.cancel = true;
        }

        mDirectoryScanner = null;
    }

    private void handleMessage(Message message) {
        switch (message.what) {
            case MESSAGE_SHOW_DIRECTORY_CONTENTS:
                showDirectoryContents((DirectoryContents) message.obj);
                break;

            case MESSAGE_SET_PROGRESS:
                setProgress(message.arg1, message.arg2);
                break;
        }
    }

    private void setProgress(int progress, int maxProgress) {
        mProgressBar.setMax(maxProgress);
        mProgressBar.setProgress(progress);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void showDirectoryContents(DirectoryContents contents) {
        mDirectoryScanner = null;

        mListSdCard = contents.getListSdCard();
        mListDir = contents.getListDir();
        mListFile = contents.getListFile();

        directoryEntries.ensureCapacity(mListSdCard.size() + mListDir.size() + mListFile.size());

        addAllElements(directoryEntries, mListSdCard);
        addAllElements(directoryEntries, mListDir);
        addAllElements(directoryEntries, mListFile);

        IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this);
        itla.setListItems(directoryEntries, getListView().hasTextFilter());
        setListAdapter(itla);
        getListView().setTextFilterEnabled(true);

        selectInList(mPreviousDirectory);
        refreshDirectoryPanel();
        setProgressBarIndeterminateVisibility(false);

        if (mState == STATE_ENTER_FILENAME) {
            mFilenameBar.setVisibility(View.VISIBLE);
        }
        mProgressBar.setVisibility(View.GONE);
        mEmptyText.setVisibility(View.VISIBLE);
    }

    /**
     *
     */
    private void refreshDirectoryPanel() {
        setDirectoryButtons();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BUNDLE_CURRENT_DIRECTORY, mCurrentDirectory.getAbsolutePath());
        outState.putString(BUNDLE_TEXTBOX_CONTENTS, mEditFilename.getText().toString());
    }

    private void pickEnteredFilename() {
        String filename = mEditFilename.getText().toString();
        if (filename.length() == 0) {
            Toast.makeText(this, R.string.dialog_error_no_file_name, Toast.LENGTH_LONG).show();
            return;
        }
        // No easy way to check filename for illegal characters like |?<>*+ because
        // this is dependent on the file system type - FAT32, ext3, NTFS...
        // The main application will catch such errors when it tries
        // to create a file with the illegal name.

        File file = FileUtils.getFile(mCurrentDirectory.getAbsolutePath(), filename);
        if (file.isDirectory()) {
            Toast.makeText(this, R.string.dialog_error_not_a_file, Toast.LENGTH_LONG).show();
            return;
        }
        boolean exists = file.exists();
        File parent = file.getParentFile();
        if (!exists && !parent.canWrite()) {
            // misc errors
            Toast.makeText(this, R.string.dialog_error_file_write_denied, Toast.LENGTH_LONG).show();
            return;
        }

        pickFile(file);
    }

    private void pickFile(File file) {
        Intent intent = getIntent();
        intent.setData(FileUtils.getUri(file));
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     * This function browses up one level
     * according to the field: currentDirectory
     */
    private void upOneLevel() {
        if (mCurrentDirectory.getParent() != null) {
            browseTo(mCurrentDirectory.getParentFile());
        }
    }

    /**
     * Browse to some location by clicking on a list item.
     */
    private void browseTo(final File aDirectory) {
        if (aDirectory.isDirectory()) {
            mPreviousDirectory = mCurrentDirectory;
            mCurrentDirectory = aDirectory;
            refreshList();
        } else {
            if (mState == STATE_PICK_FILE) {
                pickFile(aDirectory);
            } else if (mState == STATE_ENTER_FILENAME) {
                // Fill in the text box for the filename
                mEditFilename.setText(aDirectory.getName());
            }
        }
    }

    private void refreshList() {
        // Cancel an existing scanner, if applicable.
        DirectoryScanner scanner = mDirectoryScanner;
        if (scanner != null) {
            scanner.cancel = true;
        }

        directoryEntries.clear();
        mListDir.clear();
        mListFile.clear();
        mListSdCard.clear();

        setProgressBarIndeterminateVisibility(true);

        // Don't show the "folder empty" text since we're scanning.
        mEmptyText.setVisibility(View.GONE);

        // Also DON'T show the progress bar - it's kind of lame to show that
        // for less than a second.
        mProgressBar.setVisibility(View.GONE);
        setListAdapter(null);

        mDirectoryScanner = new DirectoryScanner(mCurrentDirectory, this, mCurrentHandler, mSdCardPath);
        mDirectoryScanner.start();
    }

    private void selectInList(File selectFile) {
        String filename = selectFile.getName();
        IconifiedTextListAdapter la = (IconifiedTextListAdapter) getListAdapter();
        int count = la.getCount();
        for (int i = 0; i < count; i++) {
            IconifiedText it = (IconifiedText) la.getItem(i);
            if (it.getText().equals(filename)) {
                getListView().setSelection(i);
                break;
            }
        }
    }

    private void addAllElements(List<IconifiedText> addTo, List<IconifiedText> addFrom) {
        addTo.addAll(addFrom);
    }

    private void setDirectoryButtons() {
        String[] parts = mCurrentDirectory.getAbsolutePath().split("/");

        mDirectoryButtons.removeAllViews();

        int WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT;

        // Add home button separately
        ImageButton ib = new ImageButton(this);
        ib.setImageResource(R.drawable.ic_launcher_home);
        ib.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        ib.setOnClickListener(view -> browseTo(new File(ROOT_DIR)));
        mDirectoryButtons.addView(ib);

        // Add other buttons

        String dir = "";

        for (int i = 1; i < parts.length; i++) {
            dir += PATH_SEPARATOR + parts[i];
            if (dir.equals(mSdCardPath)) {
                // Add SD card button
                ib = new ImageButton(this);
                ib.setImageResource(R.drawable.icon_sdcard);
                ib.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                ib.setOnClickListener(view -> browseTo(new File(mSdCardPath)));
                mDirectoryButtons.addView(ib);
            } else {
                Button b = new Button(this);
                b.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                b.setText(parts[i]);
                b.setTag(dir);
                b.setOnClickListener(view -> {
                    String dir1 = (String) view.getTag();
                    browseTo(new File(dir1));
                });
                mDirectoryButtons.addView(b);
            }
        }

        checkButtonLayout();
    }

    private void checkButtonLayout() {
        // Let's measure how much space we need:
        int spec = View.MeasureSpec.UNSPECIFIED;
        mDirectoryButtons.measure(spec, spec);

        int requiredwidth = mDirectoryButtons.getMeasuredWidth();
        int width = getWindowManager().getDefaultDisplay().getWidth();

        if (requiredwidth > width) {
            int WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT;

            // Create a new button that shows that there is more to the left:
            ImageButton ib = new ImageButton(this);
            ib.setImageResource(R.drawable.ic_menu_back_small);
            ib.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            ib.setOnClickListener(view -> {
                // Up one directory.
                upOneLevel();
            });
            mDirectoryButtons.addView(ib, 0);

            // New button needs even more space
            ib.measure(spec, spec);
            requiredwidth += ib.getMeasuredWidth();

            // Need to take away some buttons
            // but leave at least "back" button and one directory button.
            while (requiredwidth > width && mDirectoryButtons.getChildCount() > 2) {
                View view = mDirectoryButtons.getChildAt(1);
                requiredwidth -= view.getMeasuredWidth();

                mDirectoryButtons.removeViewAt(1);
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        IconifiedTextListAdapter adapter = (IconifiedTextListAdapter) getListAdapter();

        if (adapter == null) {
            return;
        }

        IconifiedText text = (IconifiedText) adapter.getItem(position);
        String file = text.getText();

        String curdir = mCurrentDirectory.getAbsolutePath();
        File clickedFile = FileUtils.getFile(curdir, file);
        browseTo(clickedFile);
    }

    private void getSdCardPath() {
        mSdCardPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    }
}