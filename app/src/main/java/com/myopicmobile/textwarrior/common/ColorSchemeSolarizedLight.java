// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package com.myopicmobile.textwarrior.common;


/*
 * Light color scheme. Palettes devised for readability by Ethan Schoonover.
 * http://ethanschoonover.com/solarized
 */
public class ColorSchemeSolarizedLight extends ColorScheme {

    public ColorSchemeSolarizedLight() {
        setColor(Colorable.FOREGROUND, BASE00);
        setColor(Colorable.BACKGROUND, BASE3);
        setColor(Colorable.SELECTION_FOREGROUND, BASE3);
        setColor(Colorable.SELECTION_BACKGROUND, BASE00);
        setColor(Colorable.CARET_FOREGROUND, BASE3);
        setColor(Colorable.CARET_BACKGROUND, RED);
        setColor(Colorable.CARET_DISABLED, BASE1);
        setColor(Colorable.LINE_NUMBER, BASE1);
        setColor(Colorable.LINE_DIVIDER, BASE1);
        setColor(Colorable.LINE_HIGHLIGHT_0, BASE2);
        setColor(Colorable.LINE_HIGHLIGHT, BASE2);
        setColor(Colorable.NON_PRINTING_GLYPH, BASE1);
        setColor(Colorable.COMMENT, BASE1);
        setColor(Colorable.KEYWORD, BLUE);
        setColor(Colorable.LITERAL, CYAN);
        setColor(Colorable.SECONDARY, BASE1);
    }

    protected static final int BASE00 = 0xFF657B83; // bryellow
    protected static final int BASE01 = 0xFF586E75; // brgreen
    protected static final int BASE02 = 0xFF073642; // black
    protected static final int BASE03 = 0xFF002B36; // brblack

    protected static final int BASE0 = 0xFF839496; // brblue
    protected static final int BASE1 = 0xFF93A1A1; // brcyan
    protected static final int BASE2 = 0xFFEEE8D5; // white
    protected static final int BASE3 = 0xFFFDF6E3; // brwhite

    protected static final int YELLOW = 0xFFB58900;
    protected static final int ORANGE = 0xFFCB4B16;
    protected static final int RED = 0xFFDC322F;
    protected static final int MAGENTA = 0xFFD33682;
    protected static final int VIOLET = 0xFF6C71C4;
    protected static final int BLUE = 0xFF268BD2;
    protected static final int CYAN = 0xFF2AA198;
    protected static final int GREEN = 0xFF859900;

    @Override
    public boolean isDark() {
        return false;
    }
}
