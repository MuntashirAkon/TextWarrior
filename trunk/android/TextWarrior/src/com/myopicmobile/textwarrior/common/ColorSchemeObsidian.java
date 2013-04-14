package com.myopicmobile.textwarrior.common;


public class ColorSchemeObsidian extends ColorScheme {

	public ColorSchemeObsidian(){
		setColor(Colorable.FOREGROUND, OFF_WHITE);
		setColor(Colorable.BACKGROUND, OFF_BLACK);
		setColor(Colorable.SELECTION_FOREGROUND, OFF_WHITE);
		setColor(Colorable.SELECTION_BACKGROUND, TAN);
		setColor(Colorable.CARET_FOREGROUND, OFF_BLACK);
		setColor(Colorable.CARET_BACKGROUND, SKY_BLUE);
		setColor(Colorable.CARET_DISABLED, GREY);
		setColor(Colorable.LINE_HIGHLIGHT, PASTEL_RED);
		setColor(Colorable.NON_PRINTING_GLYPH, DARK_GREY);
		setColor(Colorable.COMMENT, SLATE);
		setColor(Colorable.KEYWORD, PASTEL_GREEN);
		setColor(Colorable.LITERAL, ORANGE);
		setColor(Colorable.SECONDARY, PURPLE);
	}

	private static final int DARK_GREY = 0xFF616161;
	private static final int GREY = 0xFF808080;
	private static final int OFF_BLACK = 0xFF293134;
	private static final int OFF_WHITE = 0xFFE0E2E4;
	private static final int ORANGE = 0xFFEC7600;
	private static final int PASTEL_GREEN = 0xFF93C763;
	private static final int PASTEL_RED = 0xFFFF6262;
	private static final int PURPLE = 0xFFA082BD;
	private static final int SKY_BLUE = 0xFF8AD1ED;
	private static final int SLATE = 0xFF7D8C93;
	private static final int TAN = 0xFF804000;

	@Override
	public boolean isDark() {
		return true;
	}
}
