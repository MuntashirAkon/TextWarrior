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

/**
 * Base class for programming language syntax.
 * By default, C-like symbols and operators are included, but not keywords.
 */
public abstract class Language {
	public final static char EOF = '\uFFFF';
	public final static char NULL_CHAR = '\u0000';
	public final static char NEWLINE = '\n';
	public final static char BACKSPACE = '\b';
	public final static char TAB = '\t';
	public final static String GLYPH_NEWLINE = "\u21b5";
	public final static String GLYPH_SPACE = "\u00b7";
	public final static String GLYPH_TAB = "\u00bb";


	private final static char[] BASIC_C_OPERATORS = {
		'(', ')', '{', '}', '.', ',', ';', '=', '+', '-',
		'/', '*', '&', '!', '|', ':', '[', ']', '<', '>',
		'?', '~', '%', '^'
	};


	protected HashMap<String, Integer> _keywords = new HashMap<String, Integer>(0);
	protected HashMap<Character, Integer> _operators = generateOperators(BASIC_C_OPERATORS);

	protected void setKeywords(String[] keywords){
		_keywords = new HashMap<String, Integer>(keywords.length);
		for(int i = 0; i < keywords.length; ++i){
			_keywords.put(keywords[i], Lexer.KEYWORD);
		}
	}

	protected void setOperators(char[] operators){
		_operators = generateOperators(operators);
	}

	private HashMap<Character, Integer> generateOperators(char[] operators){
		HashMap<Character, Integer> operatorsMap = new HashMap<Character, Integer>(operators.length);
		for(int i = 0; i < operators.length; ++i){
			operatorsMap.put(operators[i], Lexer.OPERATOR);
		}
		return operatorsMap;
	}

	public final boolean isOperator(char c){
		return _operators.containsKey(Character.valueOf(c));
	}

	public final boolean isKeyword(String s){
		return _keywords.containsKey(s);
	}

	public boolean isWhitespace(char c){
		return (c == ' ' || c == '\n'|| c == '\t' ||
			c == '\r' || c == '\f' || c == EOF);
	}

	public boolean isSentenceTerminator(char c){
		return (c == '.');
	}

	public boolean isEscapeChar(char c){
		return (c == '\\');
	}

	/**
	 * Derived classes that do not do represent C-like programming languages
	 * should return false; otherwise return true
	 */
	public boolean isProgLang(){
		return true;
	}

	/**
	 * Whether the word after c is a token
	 */
	public boolean isWordStart(char c){
		return false;
	}

	/**
	 * Whether cSc is a token, where S is a sequence of characters that are on the same line
	 */
	public boolean isDelimiterA(char c){
		return (c == '"');
	}

	/**
	 * Same concept as isDelimiterA(char), but Language and its subclasses can
	 * specify a second type of symbol to use here
	 */
	public boolean isDelimiterB(char c){
		return (c == '\'');
	}

	/**
	 * Whether cL is a token, where L is a sequence of characters until the end of the line
	 */
	public boolean isLineAStart(char c){
		return (c == '#');
	}

	/**
	 * Same concept as isLineAStart(char), but Language and its subclasses can
	 * specify a second type of symbol to use here
	 */
	public boolean isLineBStart(char c){
		return false;
	}

	/**
	 * Whether c0c1L is a token, where L is a sequence of characters until the end of the line
	 */
	public boolean isLineStart(char c0, char c1){
		return (c0 == '/' && c1 == '/');
	}

	/**
	 * Whether c0c1 signifies the start of a multi-line token
	 */
	public boolean isMultilineStartDelimiter(char c0, char c1){
		return (c0 == '/' && c1 == '*');
	}

	/**
	 * Whether c0c1 signifies the end of a multi-line token
	 */
	public boolean isMultilineEndDelimiter(char c0, char c1){
		return (c0 == '*' && c1 == '/');
	}
}
