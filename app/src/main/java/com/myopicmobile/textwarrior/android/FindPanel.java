/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;

public class FindPanel extends TableLayout {
    private TextWarriorApplication _callback;
    private final EditText _findText;
    private final ImageButton _find;
    private final ImageButton _findBackwards;

    private final ImageButton _toggleReplaceBar;
    private final TableRow _replaceBar;
    private final EditText _replaceText;
    private final ImageButton _replace;
    private final ImageButton _replaceAll;

    private final ImageButton _displayOptions;

    private AlertDialog _optionsDialog;
    private CheckBox _caseSensitive;
    private CheckBox _matchWholeWord;

    public FindPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.find_panel, this);

        _findText = findViewById(R.id.find_panel_search_text);
        _find = findViewById(R.id.find_panel_find_next);
        _findBackwards = findViewById(R.id.find_panel_find_prev);
        _toggleReplaceBar = findViewById(R.id.find_panel_replace_bar_toggle);
        _replaceBar = findViewById(R.id.find_panel_replace_bar);
        _replaceText = findViewById(R.id.find_panel_replace_text);
        _displayOptions = findViewById(R.id.find_panel_settings);
        _replace = findViewById(R.id.find_panel_replace);
        _replaceAll = findViewById(R.id.find_panel_replace_all);

        createOptionsDialog(context);

        _toggleReplaceBar.setImageResource(R.drawable.arrow_down);
        _replaceBar.setVisibility(GONE);
        _toggleReplaceBar.setOnClickListener(v -> {
            if (_replaceBar.getVisibility() == VISIBLE) {
                _replaceBar.setVisibility(GONE);
                _toggleReplaceBar.setImageResource(R.drawable.arrow_down);
            } else {
                _replaceBar.setVisibility(VISIBLE);
                _toggleReplaceBar.setImageResource(R.drawable.arrow_up);
            }
        });

        _displayOptions.setOnClickListener(v -> displaySettings());

        _findText.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    _callback.find(_findText.getText().toString(),
                            _caseSensitive.isChecked(),
                            _matchWholeWord.isChecked());
                }
                return true;
            }
            return false;
        });

        _replaceText.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    _callback.replaceSelection(_replaceText.getText().toString());
                }
                return true;
            }
            return false;
        });

        _find.setOnClickListener(v -> _callback.find(_findText.getText().toString(),
                _caseSensitive.isChecked(),
                _matchWholeWord.isChecked()));

        _findBackwards.setOnClickListener(v -> _callback.findBackwards(_findText.getText().toString(),
                _caseSensitive.isChecked(),
                _matchWholeWord.isChecked()));

        _replace.setOnClickListener(v -> _callback.replaceSelection(_replaceText.getText().toString()));

        _replaceAll.setOnClickListener(v -> _callback.replaceAll(_findText.getText().toString(),
                _replaceText.getText().toString(),
                _caseSensitive.isChecked(),
                _matchWholeWord.isChecked()));

        // EditText views pad the entire row
        setColumnShrinkable(1, true);
        setColumnStretchable(1, true);
    }

    private void createOptionsDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View settingsLayout = inflater.inflate(R.layout.find_options, null);

        builder.setView(settingsLayout);
        builder.setTitle(context.getString(R.string.find_panel_options));
        builder.setPositiveButton(android.R.string.ok, (dialog, id) -> dialog.dismiss());
        _optionsDialog = builder.create();

        _caseSensitive = settingsLayout.findViewById(R.id.find_panel_case_sensitive);
        _matchWholeWord = settingsLayout.findViewById(R.id.find_panel_match_whole_word);
    }

    public void displaySettings() {
        _optionsDialog.show();
    }

    public void setCallback(TextWarriorApplication c) {
        _callback = c;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction,
                                                  Rect previouslyFocusedRect) {
        _findText.requestFocus();
        return true;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        FindPanelSavedState ss = new FindPanelSavedState(superState);
        ss.replaceBarVisibility = _replaceBar.getVisibility();
        ss.optionsCaseSensitive = _caseSensitive.isChecked();
        ss.optionsWholeWord = _matchWholeWord.isChecked();
        ss.optionsDialogShown = _optionsDialog.isShowing();
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof FindPanelSavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        FindPanelSavedState ss = (FindPanelSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        //default visibility of _replaceBar is GONE
        if (ss.replaceBarVisibility == VISIBLE) {
            _replaceBar.setVisibility(VISIBLE);
            _toggleReplaceBar.setImageResource(R.drawable.arrow_up);
        }
        _caseSensitive.setChecked(ss.optionsCaseSensitive);
        _matchWholeWord.setChecked(ss.optionsWholeWord);
        if (ss.optionsDialogShown) {
            _optionsDialog.show();
        }
    }

    static class FindPanelSavedState extends View.BaseSavedState {
        int replaceBarVisibility;
        boolean optionsCaseSensitive;
        boolean optionsWholeWord;
        boolean optionsDialogShown;

        /**
         * Constructor called from FindPanel.onSaveInstanceState()
         */
        FindPanelSavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from CREATOR
         */
        private FindPanelSavedState(Parcel in) {
            super(in);
            this.replaceBarVisibility = in.readInt();
            this.optionsCaseSensitive = in.readInt() != 0;
            this.optionsWholeWord = in.readInt() != 0;
            this.optionsDialogShown = in.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(this.replaceBarVisibility);
            out.writeInt(this.optionsCaseSensitive ? 1 : 0);
            out.writeInt(this.optionsWholeWord ? 1 : 0);
            out.writeInt(this.optionsDialogShown ? 1 : 0);
        }

        public static final Parcelable.Creator<FindPanelSavedState> CREATOR =
                new Parcelable.Creator<FindPanelSavedState>() {
                    public FindPanelSavedState createFromParcel(Parcel in) {
                        return new FindPanelSavedState(in);
                    }

                    public FindPanelSavedState[] newArray(int size) {
                        return new FindPanelSavedState[size];
                    }
                };
    }

}
