/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

package com.myopicmobile.textwarrior.common;

import java.util.HashMap;

public abstract class ColorScheme {
	public enum Colorable {
		FOREGROUND, BACKGROUND, SELECTION_FOREGROUND, SELECTION_BACKGROUND,
		CARET_FOREGROUND, CARET_BACKGROUND, CARET_DISABLED, LINE_HIGHLIGHT,
		NON_PRINTING_GLYPH, COMMENT, KEYWORD, LITERAL,
		SECONDARY
	}

	protected HashMap<Colorable, Integer> _colors = generateDefaultColors();

	protected void setColor(Colorable colorable, int color) {
		_colors.put(colorable, color);
	}

	public int getColor(Colorable colorable) {
		Integer color = _colors.get(colorable);
		if(color == null){
			TextWarriorException.fail("Color not specified for " + colorable);
			return 0;
		}
		return color.intValue();
	}

	// Currently, color scheme is tightly coupled with semantics of the token types
	public int getTokenColor(int tokenType){
		Colorable element;
		switch(tokenType){
		case Lexer.NORMAL:
			element = Colorable.FOREGROUND;
			break;
		case Lexer.KEYWORD:
			element = Colorable.KEYWORD;
			break;
		case Lexer.DOUBLE_SYMBOL_LINE: //fall-through
		case Lexer.DOUBLE_SYMBOL_DELIMITED_MULTILINE:
		case Lexer.SINGLE_SYMBOL_LINE_B:
			element = Colorable.COMMENT;
			break;
		case Lexer.SINGLE_SYMBOL_DELIMITED_A: //fall-through
		case Lexer.SINGLE_SYMBOL_DELIMITED_B:
			element = Colorable.LITERAL;
			break;
		case Lexer.SINGLE_SYMBOL_LINE_A: //fall-through
		case Lexer.SINGLE_SYMBOL_WORD:
			element = Colorable.SECONDARY;
			break;
		 default:
			TextWarriorException.fail("Invalid token type");
			element = Colorable.FOREGROUND;
			break;
		}
		return getColor(element);
	}

	/**
	 * Whether this color scheme uses a dark background, like black or dark grey.
	 */
	public abstract boolean isDark();

	private HashMap<Colorable, Integer> generateDefaultColors() {
		// High-contrast, black-on-white color scheme
		HashMap<Colorable, Integer> colors = new HashMap<Colorable, Integer>(Colorable.values().length);
		colors.put(Colorable.FOREGROUND, BLACK);
		colors.put(Colorable.BACKGROUND, WHITE);
		colors.put(Colorable.SELECTION_FOREGROUND, WHITE);
		colors.put(Colorable.SELECTION_BACKGROUND, MAROON);
		colors.put(Colorable.CARET_FOREGROUND, WHITE);
		colors.put(Colorable.CARET_BACKGROUND, BLUE);
		colors.put(Colorable.CARET_DISABLED, GREY);
		colors.put(Colorable.LINE_HIGHLIGHT, RED);
		colors.put(Colorable.NON_PRINTING_GLYPH, LIGHT_GREY);
		colors.put(Colorable.COMMENT, OLIVE_GREEN); //  Eclipse default color
		colors.put(Colorable.KEYWORD, PURPLE); // Eclipse default color
		colors.put(Colorable.LITERAL, INDIGO); // Eclipse default color
		colors.put(Colorable.SECONDARY, DARK_RED);
		return colors;
	}

	// In ARGB format: 0xAARRGGBB
	private static final int BLACK = 0xFF000000;
	private static final int BLUE = 0xFF0000FF;
	private static final int DARK_RED = 0xFF8B0000;
	private static final int GREY = 0xFF808080;
	private static final int LIGHT_GREY = 0xFFAAAAAA;
	private static final int MAROON = 0xFF800000;
	private static final int INDIGO = 0xFF2A00FF;
	private static final int OLIVE_GREEN = 0xFF3F7F5F;
	private static final int PURPLE = 0xFF7F0055;
	private static final int RED = 0xFFFF0000;
	private static final int WHITE = 0xFFFFFFFF;
}
