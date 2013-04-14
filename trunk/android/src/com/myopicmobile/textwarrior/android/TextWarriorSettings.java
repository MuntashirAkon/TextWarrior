/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;

import com.myopicmobile.textwarrior.common.EncodingScheme;
import com.myopicmobile.textwarrior.common.TextWarriorException;

public class TextWarriorSettings extends PreferenceActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preference_screen_layout);

		ListPreference inputEncodingPref =
			(ListPreference) findPreference(getString(R.string.settings_key_file_input_format));
		ListPreference outputEncodingPref =
			(ListPreference) findPreference(getString(R.string.settings_key_file_output_format));
		ListPreference lineTerminatorPref =
			(ListPreference) findPreference(getString(R.string.settings_key_line_terminator_style));

		inputEncodingPref.setEntryValues(EncodingScheme.encodingSchemes);
		inputEncodingPref.setEntries(EncodingScheme.encodingSchemesAliases);
		outputEncodingPref.setEntryValues(EncodingScheme.encodingSchemes);
		outputEncodingPref.setEntries(EncodingScheme.encodingSchemesAliases);
		lineTerminatorPref.setEntryValues(EncodingScheme.lineTerminators);
		lineTerminatorPref.setEntries(EncodingScheme.lineTerminatorsAliases);
		
		if(!hasOrientationSensor()){
			removeLiquidNavigationMenuOption();
		}

		if(!hasHardwareKeyboard()){
			removeLongPressCapsOption();
		}
	}

	private boolean hasOrientationSensor() {
		SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		return (sm.getDefaultSensor(Sensor.TYPE_ORIENTATION) != null);
	}

	private void removeLiquidNavigationMenuOption() {
		ListPreference navigationMethodPref =
			(ListPreference) findPreference(getString(R.string.settings_key_navigation_method));
		CharSequence[] navMethods = navigationMethodPref.getEntryValues();
		CharSequence[] navMethodsNames = navigationMethodPref.getEntries();
		
		String liquidNavKey = getString(R.string.settings_navigation_method_liquid);
		CharSequence[] trimmedEntries = new CharSequence[navMethods.length - 1];
		CharSequence[] trimmedEntryValues = new CharSequence[navMethods.length - 1];
		for(int i = 0, j = 0; i < navMethods.length; ++i){
			if(navMethods[i].equals(liquidNavKey)){
				continue;
			}
			trimmedEntryValues[j] = navMethods[i];
			trimmedEntries[j] = navMethodsNames[i];
			++j;
		}
		
		navigationMethodPref.setEntryValues(trimmedEntryValues);
		navigationMethodPref.setEntries(trimmedEntries);
	}
	
	private boolean hasHardwareKeyboard() {
		return getResources().getConfiguration().keyboard == Configuration.KEYBOARD_QWERTY;
	}
	
	private void removeLongPressCapsOption() {
		Preference p = findPreference(getString(R.string.settings_key_long_press_capitalize));
		PreferenceGroup g = (PreferenceGroup) findPreference(getString(R.string.settings_group_formatting));
		boolean success = g.removePreference(p);

		TextWarriorException.assertVerbose(success,
			"Did you remove the Long-press caps pref from the Formatting group?");
	}
}
