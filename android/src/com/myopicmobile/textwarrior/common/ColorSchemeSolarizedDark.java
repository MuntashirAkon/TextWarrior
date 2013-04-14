/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

package com.myopicmobile.textwarrior.common;


/*
 * Dark color scheme. Palettes devised for readability by Ethan Schoonover.
 * http://ethanschoonover.com/solarized
 */
public class ColorSchemeSolarizedDark extends ColorSchemeSolarizedLight {

	public ColorSchemeSolarizedDark(){
		setColor(Colorable.FOREGROUND, BASE0);
		setColor(Colorable.BACKGROUND, BASE03);
		setColor(Colorable.SELECTION_FOREGROUND, BASE03);
		setColor(Colorable.SELECTION_BACKGROUND, BLUE);
		setColor(Colorable.CARET_FOREGROUND, BASE03);
		setColor(Colorable.CARET_BACKGROUND, RED);
		setColor(Colorable.CARET_DISABLED, BASE01);
		setColor(Colorable.LINE_HIGHLIGHT, MAGENTA);
		setColor(Colorable.NON_PRINTING_GLYPH, BASE01);
		setColor(Colorable.COMMENT, BASE01);
		setColor(Colorable.KEYWORD, GREEN);
		setColor(Colorable.LITERAL, VIOLET);
		setColor(Colorable.SECONDARY, YELLOW);
	}

	@Override
	public boolean isDark() {
		return true;
	}
}
