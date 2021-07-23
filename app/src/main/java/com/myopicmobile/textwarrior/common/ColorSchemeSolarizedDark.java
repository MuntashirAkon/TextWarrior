// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package com.myopicmobile.textwarrior.common;


/*
 * Dark color scheme. Palettes devised for readability by Ethan Schoonover.
 * http://ethanschoonover.com/solarized
 */
public class ColorSchemeSolarizedDark extends ColorSchemeSolarizedLight {

    public ColorSchemeSolarizedDark() {
        setColor(Colorable.FOREGROUND, BASE0);
        setColor(Colorable.BACKGROUND, BASE03);
        setColor(Colorable.SELECTION_FOREGROUND, BASE03);
        setColor(Colorable.SELECTION_BACKGROUND, BASE0);
        setColor(Colorable.CARET_FOREGROUND, BASE03);
        setColor(Colorable.CARET_BACKGROUND, RED);
        setColor(Colorable.CARET_DISABLED, BASE01);
        setColor(Colorable.LINE_NUMBER, BASE01);
        setColor(Colorable.LINE_DIVIDER, BASE01);
        setColor(Colorable.LINE_HIGHLIGHT_0, BASE02);
        setColor(Colorable.LINE_HIGHLIGHT, BASE02);
        setColor(Colorable.NON_PRINTING_GLYPH, BASE01);
        setColor(Colorable.COMMENT, BASE01);
        setColor(Colorable.KEYWORD, BLUE);
        setColor(Colorable.LITERAL, CYAN);
        setColor(Colorable.SECONDARY, BASE01);
    }

    @Override
    public boolean isDark() {
        return true;
    }
}
