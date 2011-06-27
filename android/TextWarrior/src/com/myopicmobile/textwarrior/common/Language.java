/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common;

import com.myopicmobile.textwarrior.common.Lexer;

import java.util.Hashtable;

/**
 * Singleton class containing default symbols and no operators or keywords
 */
public abstract class Language {
	public final static char EOF = '\uFFFF';
	public final static char NULL_CHAR = '\u0000';
	public final static char NEWLINE = '\n';
	public final static char BACKSPACE = '\b';
	public final static char TAB = '\t';

	protected Hashtable<String, Integer> _keywords;
	protected Hashtable<Character, Integer> _operators;
	
	
	protected void registerKeywords(String[] keywords){
		_keywords = new Hashtable<String, Integer>(keywords.length);
		for(int i = 0; i < keywords.length; ++i){
			_keywords.put(keywords[i], new Integer(Lexer.KEYWORD));
		}
	}
	
	protected void registerOperators(char[] operators){
		_operators = new Hashtable<Character, Integer>(operators.length);
		for(int i = 0; i < operators.length; ++i){
			_operators.put(new Character(operators[i]), new Integer(Lexer.OPERATOR));
		}
	}
	
	public final boolean isOperator(char c){
		return _operators.containsKey(new Character(c));
	}
	
	public final boolean isKeyword(String s){
		return _keywords.containsKey(s);
	}

	public boolean isWhitespace(char c){
		return (c == ' ' || c == '\n'|| c == '\t' ||
			c == '\r' || c == '\f' || c == EOF);
	}
	
	public boolean isCharQuote(char c){
		return (c == '\'');
	}

	public boolean isStringQuote(char c){
		return (c == '"');
	}

	public boolean isPreprocessor(char c){
		return (c == '#');
	}
	
	public boolean isComment(char c){
		return false;
	}

	public boolean isComment(char c0, char c1){
		return (c0 == '/' && c1 == '/');
	}
	
	public boolean isMultiLineCommentStart(char c0, char c1){
		return (c0 == '/' && c1 == '*');
	}
	
	public boolean isMultiLineCommentEnd(char c0, char c1){
		return (c0 == '*' && c1 == '/');
	}
	
	/**
	 * Derived classes that do not do represent C-like programming languages
	 * should return false; otherwise return true
	 */
	public boolean isProgLang(){
		return true;
	}
}
