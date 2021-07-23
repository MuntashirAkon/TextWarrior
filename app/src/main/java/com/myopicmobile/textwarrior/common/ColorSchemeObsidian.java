// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package com.myopicmobile.textwarrior.common;

public class ColorSchemeObsidian extends ColorScheme {

    public ColorSchemeObsidian() {
        setColor(Colorable.FOREGROUND, 0xFFE0E2E4);
        setColor(Colorable.BACKGROUND, 0xFF293134);
        setColor(Colorable.SELECTION_FOREGROUND, 0xFFE0E2E4);
        setColor(Colorable.SELECTION_BACKGROUND, 0xFF804000);
        setColor(Colorable.CARET_FOREGROUND, 0xFF293134);
        setColor(Colorable.CARET_BACKGROUND, 0xFFE0E2E4);
        setColor(Colorable.CARET_DISABLED, 0xFF7D8C93);
        setColor(Colorable.LINE_NUMBER, 0xFF81969A);
        setColor(Colorable.LINE_DIVIDER, 0xFF81969A);
        setColor(Colorable.LINE_HIGHLIGHT_0, 0xFF2F393C);
        setColor(Colorable.LINE_HIGHLIGHT, 0xFF2F393C);
        setColor(Colorable.NON_PRINTING_GLYPH, 0xFF7D8C93);
        setColor(Colorable.COMMENT, 0xFF7D8C93);
        setColor(Colorable.KEYWORD, 0xFF678CB1);
        setColor(Colorable.LITERAL, 0xFFEC7600);
        setColor(Colorable.SECONDARY, 0xFF7D8C93);
    }

    @Override
    public boolean isDark() {
        return true;
    }
}
