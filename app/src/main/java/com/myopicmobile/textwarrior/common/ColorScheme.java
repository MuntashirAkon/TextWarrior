// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package com.myopicmobile.textwarrior.common;

import androidx.annotation.NonNull;

import java.util.HashMap;

public abstract class ColorScheme {
    public enum Colorable {
        FOREGROUND, BACKGROUND, SELECTION_FOREGROUND, SELECTION_BACKGROUND,
        CARET_FOREGROUND, CARET_BACKGROUND, CARET_DISABLED,
        LINE_NUMBER, LINE_DIVIDER, LINE_HIGHLIGHT_0, LINE_HIGHLIGHT,
        NON_PRINTING_GLYPH, COMMENT, KEYWORD, LITERAL, SECONDARY
    }

    protected HashMap<Colorable, Integer> _colors = generateDefaultColors();

    protected void setColor(Colorable colorable, int color) {
        _colors.put(colorable, color);
    }

    public int getColor(Colorable colorable) {
        Integer color = _colors.get(colorable);
        if (color == null) {
            TextWarriorException.fail("Color not specified for " + colorable);
            return 0;
        }
        return color.intValue();
    }

    // Currently, color scheme is tightly coupled with semantics of the token types
    public int getTokenColor(int tokenType) {
        Colorable element;
        switch (tokenType) {
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

    @NonNull
    private HashMap<Colorable, Integer> generateDefaultColors() {
        // High-contrast, black-on-white color scheme
        return new HashMap<Colorable, Integer>(Colorable.values().length) {{
            put(Colorable.FOREGROUND, BLACK);
            put(Colorable.BACKGROUND, WHITE);
            put(Colorable.SELECTION_FOREGROUND, WHITE);
            put(Colorable.SELECTION_BACKGROUND, MAROON);
            put(Colorable.CARET_FOREGROUND, WHITE);
            put(Colorable.CARET_BACKGROUND, BLUE);
            put(Colorable.CARET_DISABLED, GREY);
            put(Colorable.LINE_NUMBER, OLIVE_GREEN);
            put(Colorable.LINE_DIVIDER, OLIVE_GREEN);
            put(Colorable.LINE_HIGHLIGHT_0, WHITE_HIGHLIGHT);
            put(Colorable.LINE_HIGHLIGHT, WHITE_HIGHLIGHT);
            put(Colorable.NON_PRINTING_GLYPH, LIGHT_GREY);
            put(Colorable.COMMENT, OLIVE_GREEN); //  Eclipse default color
            put(Colorable.KEYWORD, PURPLE); // Eclipse default color
            put(Colorable.LITERAL, INDIGO); // Eclipse default color
            put(Colorable.SECONDARY, DARK_RED);
        }};
    }

    // In ARGB format: 0xAARRGGBB
    public static final int BLACK_HIGHLIGHT = 0x10FFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int BLUE = 0xFF0000FF;
    private static final int DARK_RED = 0xFF8B0000;
    private static final int GREY = 0xFF808080;
    private static final int LIGHT_GREY = 0xFFAAAAAA;
    private static final int MAROON = 0xFF800000;
    private static final int INDIGO = 0xFF2A00FF;
    private static final int OLIVE_GREEN = 0xFF3F7F5F;
    private static final int PURPLE = 0xFF7F0055;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int WHITE_HIGHLIGHT = 0x10000000;
}
