/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common;

import com.myopicmobile.textwarrior.common.Language;
import com.myopicmobile.textwarrior.common.DocumentProvider;
import com.myopicmobile.textwarrior.common.Pair;
import com.myopicmobile.textwarrior.common.TextWarriorException;

import java.util.List;
import java.util.Vector;

/**
 * Does lexical analysis of a text. The programming language syntax used is
 * a static variable.
 */
public class Lexer{
	public final static int NORMAL = 0;
	public final static int KEYWORD = 1;
	public final static int OPERATOR = 2;
	public final static int PREPROCESSOR = 3;
	public final static int COMMENT = 4;
	public final static int MULTILINE_COMMENT = 5;
	public final static int CHAR_LITERAL = 6;
	public final static int STRING_LITERAL = 7;
	public final static int UNKNOWN = 8;
	private final static int MAX_KEYWORD_LENGTH = 31;

	private static Language _globalLanguage = LanguageNonProg.getCharacterEncodings();
	synchronized public static void setLanguage(Language c){
		_globalLanguage = c;
	}

	synchronized public static Language getLanguage(){
		return _globalLanguage;
	}


	private DocumentProvider _hDoc;
	private LexThread _workerThread = null;
	LexCallback _callback = null;

	public Lexer(LexCallback callback){
		_callback = callback;
	}
	
	public void tokenize(DocumentProvider hDoc){
		if(!Lexer.getLanguage().isProgLang()){
			if(_workerThread != null){
				_workerThread.abort();
				_workerThread = null;
			}
			return;
		}


		//tokenize will modify the state of hDoc; make a copy
		setDocument(new DocumentProvider(hDoc));
		if(_workerThread == null){
			_workerThread = new LexThread(this);
			_workerThread.start();
		}
		else{
			_workerThread.restart();
		}
	}

	//TODO remove callback when activity configuration changes
	void tokenizeDone(List<Pair> result){
		if(_callback != null){
			_callback.lexDone(result);
		}
		_workerThread = null;
	}

	public synchronized void setDocument(DocumentProvider hDoc){
		_hDoc = hDoc;
	}
	
	public synchronized DocumentProvider getDocument(){
		return _hDoc;
	}
	
	
	
	
	
	private class LexThread extends Thread{
		private boolean rescan = false;
		private Lexer _lexManager;
		/** can be set by another thread to stop the scan immediately */
		private Flag _abort;
		/** A collection of Pairs, where Pair.first is the start 
		 * position of the token, and Pair.second is the type of the token.*/
		private Vector<Pair> _tokens;

		public LexThread(Lexer p){
			_lexManager = p;
			_abort = new Flag();
		}

		public void run(){
			do{
				rescan = false;
				_abort.clear();
				tokenize();
			}
			while(rescan);

			if(!_abort.isSet()){
				// lex complete
				_lexManager.tokenizeDone(_tokens);
			}
		}

		public void restart() {
			rescan = true;
			_abort.set();
		}
		
		public void abort() {
			_abort.set();
		}

		/**
		 * Scans the document referenced by _lexManager for tokens.
		 * The result is stored internally.
		 */
		public void tokenize(){
			DocumentProvider hDoc = getDocument();
			Language language = Lexer.getLanguage();
			Vector<Pair> tokens = new Vector<Pair>();

			if(!language.isProgLang()){
				tokens.addElement(new Pair(0, NORMAL));
				_tokens = tokens;
				return;
			}

			char[] candidateWord = new char[MAX_KEYWORD_LENGTH];
			int currentCharInWord = 0;

			int spanStartPosition = 0;
			int workingPosition = 0;
			int state = UNKNOWN;
			char prevChar = 0;

		    hDoc.seekChar(0);
			while (hDoc.hasNext() && !_abort.isSet()){
				char currentChar = hDoc.next();

				switch(state){
				case UNKNOWN: //fall-through
				case NORMAL: //fall-through
				case KEYWORD:
					int pendingState = state;
					if (language.isComment(prevChar, currentChar)){
						pendingState = COMMENT;
					}
					else if (language.isMultiLineCommentStart(prevChar, currentChar)){
						pendingState = MULTILINE_COMMENT;
					}
					else if (language.isStringQuote(currentChar)){
						pendingState = STRING_LITERAL;	
					}
					else if (language.isCharQuote(currentChar)){
						pendingState = CHAR_LITERAL;	
					}
					else if (language.isPreprocessor(currentChar)){
						pendingState = PREPROCESSOR;
					}
					
					if (pendingState == COMMENT ||
							pendingState == MULTILINE_COMMENT ||
							pendingState == STRING_LITERAL ||
							pendingState == CHAR_LITERAL ||
							pendingState == PREPROCESSOR){

						if (pendingState == COMMENT ||
								pendingState == MULTILINE_COMMENT){
							// account for previous '/' char
							spanStartPosition = workingPosition - 1;
//TODO consider less greedy approach and avoid adding token for previous operator '/'
							if(tokens.lastElement().getFirst() == spanStartPosition){
								tokens.removeElementAt(tokens.size() - 1);
							}
						}
						else{
							spanStartPosition = workingPosition;
						}

						// If a quotation, preprocessor directive or comment appears mid-word,
						// mark the chars preceding it as NORMAL, if the previous span isn't already NORMAL
						if(currentCharInWord > 0 && state != NORMAL){
							tokens.addElement(new Pair(workingPosition - currentCharInWord, NORMAL));
						}

						state = pendingState;
						tokens.addElement(new Pair(spanStartPosition, state));
						currentCharInWord = 0;
					}
					else if (language.isWhitespace(currentChar) ||
							language.isOperator(currentChar)){
						// full word obtained; check if it is a keyword
						if (currentCharInWord > 0 &&
								currentCharInWord <= MAX_KEYWORD_LENGTH){
							String lookupWord = new String(candidateWord, 0, currentCharInWord);
							boolean foundKeyword = language.isKeyword(lookupWord);
							
							if( foundKeyword && (state == NORMAL || state == UNKNOWN)){
								spanStartPosition = workingPosition - currentCharInWord;
								state = KEYWORD;
								tokens.addElement(new Pair(spanStartPosition, state));
							}
							else if ( !foundKeyword && (state == KEYWORD || state == UNKNOWN)){
								spanStartPosition = workingPosition - currentCharInWord;
								state = NORMAL;
								tokens.addElement(new Pair(spanStartPosition, state));
							}
							currentCharInWord = 0;
						}

						// set state to NORMAL if encountered an operator
						// and previous state was not NORMAL
						if (language.isOperator(currentChar) &&
								(state == KEYWORD || state == UNKNOWN)){
							spanStartPosition = workingPosition;
							state = NORMAL;
							tokens.addElement(new Pair(spanStartPosition, state));
						}
					}
					else {
						// collect non-whitespace chars up to MAX_KEYWORD_LENGTH
						if (currentCharInWord < MAX_KEYWORD_LENGTH){
							candidateWord[currentCharInWord] = currentChar;
						}
						currentCharInWord++;
					}
					break;
					
				
				case COMMENT: // fall-through
				case PREPROCESSOR:
					if (currentChar == '\n'){
						state = UNKNOWN;
					}
					break;
					

				case CHAR_LITERAL:
					if ((currentChar == '\'' && prevChar != '\\') ||
						currentChar == '\n'){
						state = UNKNOWN;
					}
					// consume \\ by assigning currentChar as something else
					// so that it would not be treated as an escape char in the
					// next iteration
					else if (currentChar == '\\' && prevChar == '\\'){
						currentChar = ' ';
					}
					break;
					
					
				case STRING_LITERAL:
					if ((currentChar == '"' && prevChar != '\\') ||
						currentChar == '\n'){
						state = UNKNOWN;
					}
					// consume \\ by assigning currentChar as something else
					// so that it would not be treated as an escape char in the
					// next iteration
					else if (currentChar == '\\' && prevChar == '\\'){
						currentChar = ' ';
					}
					break;
					
				case MULTILINE_COMMENT:
					if (language.isMultiLineCommentEnd(prevChar, currentChar)){
						state = UNKNOWN;
					}
					break;
					
				default:
					TextWarriorException.assertVerbose(false, "Invalid state in TokenScanner");
					break;
				}
				++workingPosition;
				prevChar = currentChar;
			}
			// end state machine


			if (tokens.isEmpty()){
				// return value cannot be empty
				tokens.addElement(new Pair(0, NORMAL));
			}

			_tokens = tokens;
		}
		
		
	}//end inner class
	

	public interface LexCallback {
		public void lexDone(List<Pair> results);
	}
}
