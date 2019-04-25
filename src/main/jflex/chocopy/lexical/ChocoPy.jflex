package chocopy.lexical;
import java_cup.runtime.*;
import java.util.Stack;

%%

/*** Do not change the flags below unless you know what you are doing. ***/

%unicode
%line
%column

%class ChocoPyLexer
%public

%cupsym ChocoPyTokens
%cup
%cupdebug

%eofclose false

/*** Do not change the flags above unless you know what you are doing. ***/

/* The following code section is copied verbatim to the
 * generated lexer class. */
%{
    /* The code below includes some convenience methods to create tokens
     * of a given type and optionally a value that the CUP parser can
     * understand. Specifically, a lot of the logic below deals with
     * embedded information about where in the source code a given token
     * was recognized, so that the parser can report errors accurately.
     * (It need not be modified for this project.) */

    /** Producer of token-related values for the parser. */
    final ComplexSymbolFactory symbolFactory = new ComplexSymbolFactory();

    /** Return a terminal symbol of syntactic category TYPE and no
     *  semantic value at the current source location. */
    private Symbol symbol(int type) {
        return symbol(type, yytext());
    }

    /** Return a terminal symbol of syntactic category TYPE and semantic
     *  value VALUE at the current source location. */
    private Symbol symbol(int type, Object value) {
        return symbolFactory.newSymbol(ChocoPyTokens.terminalNames[type], type,
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + 1),
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + yylength()),
            value);
    }


    /** Return a terminal symbol of syntactic category TYPE and semantic
     *  value VALUE at the current source location, but with custom
     *  starting column */
    private Symbol symbol(int type, Object value, int start_column) {
        return symbolFactory.newSymbol(ChocoPyTokens.terminalNames[type], type,
            new ComplexSymbolFactory.Location(yyline + 1, start_column + 1),
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + yylength()),
            value);
    }

    /* Pushes on character back into yytext() and be prepared to
     * return to the PROGRAM state later. */
    private void prepareForProgramState() {
        yypushback(1);
        yybegin(PROGRAM);
    }

    /* Tab is replaced by spaces so total number of characters up to the tab
     * is a multiple of  INDENTATION_MULTIPLE. */
    private static final int INDENTATION_MULTIPLE = 8;

    /* Active indentations so far. */
    Stack<Integer> indentStack = new Stack<>();

    /* String Literals */
    private StringBuffer sbuff = new StringBuffer();
    private boolean isIDString = false;
    private int startingColumn = 0;
    private String firstErrOfString = null;

    /**
     * Quoting from ChocoPy and Python Reference (page 12):
     * ====================================================
     * "Leading whitespace (spaces and tabs) at the beginning of a logical line is used to
     * compute the indentation level of the line, which in turn is used to determine the
     * grouping of statements. Tabs are replaced (from left to right) by one to eight
     * spaces such that the total number of characters up to and including the replacement
     * is a multiple of eight (this is intended to be the same rule as used by Unix).
     * The total number of spaces preceding the first non-blank character then determines
     * the line’s indentation"
     */
    private int countIndentation(String whitespace) {
      int spaceCount = 0;
      for (char c: whitespace.toCharArray()) {
        if (c == ' ') {
          spaceCount += 1;
        } else if (c == '\t') {
          spaceCount += (INDENTATION_MULTIPLE - spaceCount % INDENTATION_MULTIPLE);
        }
      }
      return spaceCount;
    }

%}

/* Macros (regexes used in rules below) */

WhiteSpace = [ \t]
LineBreak  = \r|\n|\r\n
InputCharacter = [^\r\n]

Identifier = [a-zA-Z_][a-zA-Z0-9_]*
IntegerLiteral = 0 | [1-9][0-9]*

// we do not have LinBreak at the end, because we still want to return NEWLINE
// token, if a comment is after a logical statement (e.g. "x = 1 # hi")
Comment = "#"{InputCharacter}*

// This is ASCII character range 32-126, excluding the
// " and \ characters which are handled separately.
StringNormalCharacter = [\x20\x21\x23-\x5b\x5d-\x7e]
StringLiterals = {StringNormalCharacter}*

Slash = \\\\
Tab = \\t
Newline = \\n
Quote = \\\"

// Some facially valid string. Only in the STRING
// state do we inspect the escapes more closely.
StringClosing = ({StringNormalCharacter}|{Slash}|{Tab}|{Newline}|{Quote})*\"


/* Custom State Definitions */
%state STRING
%state PROGRAM

%%

<PROGRAM> {
  /* Delimiters. */
  {LineBreak}                 { yybegin(YYINITIAL);
                                return symbol(ChocoPyTokens.NEWLINE); }

  /* Literals. */
  {IntegerLiteral}            {
                                  try {
                                      int integer = Integer.parseInt(yytext());
                                      return symbol(ChocoPyTokens.INTEGER, integer);
                                  } catch (Exception e) {
                                      return symbol(ChocoPyTokens.UNRECOGNIZED);
                                  }
                              }

  \"{StringClosing}           { // Before moving into the string parsing state,
                                // check that we have a valid string.
                                isIDString = false;
                                firstErrOfString = null;
                                startingColumn = yycolumn;
                                sbuff.setLength(0);
                                // Return the string (except the starting quote)
                                // for processing by the STRING state.
                                yypushback(yylength() - 1);
                                yybegin(STRING); }

  \"                          { return symbol(ChocoPyTokens.UNRECOGNIZED); }

  /* Operators. */
  "+"                         { return symbol(ChocoPyTokens.PLUS); }
  "-"                         { return symbol(ChocoPyTokens.MINUS); }
  "*"                         { return symbol(ChocoPyTokens.TIMES); }
  "//"                        { return symbol(ChocoPyTokens.IDIV); }
  "%"                         { return symbol(ChocoPyTokens.MOD); }
  "<"                         { return symbol(ChocoPyTokens.LT); }
  ">"                         { return symbol(ChocoPyTokens.GT); }
  "<="                        { return symbol(ChocoPyTokens.LEQ); }
  ">="                        { return symbol(ChocoPyTokens.GEQ); }
  "=="                        { return symbol(ChocoPyTokens.EQEQ); }
  "!="                        { return symbol(ChocoPyTokens.NEQ); }
  "="                         { return symbol(ChocoPyTokens.EQ); }
  "("                         { return symbol(ChocoPyTokens.LPAREN); }
  ")"                         { return symbol(ChocoPyTokens.RPAREN); }
  "["                         { return symbol(ChocoPyTokens.LINDEX); }
  "]"                         { return symbol(ChocoPyTokens.RINDEX); }
  ","                         { return symbol(ChocoPyTokens.COMMA); }
  ":"                         { return symbol(ChocoPyTokens.COLON); }
  "."                         { return symbol(ChocoPyTokens.DOT); }
  "->"                        { return symbol(ChocoPyTokens.ARROW); }

  /* Keywords. */
  "and"                       { return symbol(ChocoPyTokens.AND); }
  "as"                        { return symbol(ChocoPyTokens.AS); }
  "assert"                    { return symbol(ChocoPyTokens.ASSERT); }
  "async"                     { return symbol(ChocoPyTokens.ASYNC); }
  "await"                     { return symbol(ChocoPyTokens.AWAIT); }
  "break"                     { return symbol(ChocoPyTokens.BREAK); }
  "class"                     { return symbol(ChocoPyTokens.CLASS); }
  "continue"                  { return symbol(ChocoPyTokens.CONTINUE); }
  "def"                       { return symbol(ChocoPyTokens.DEF); }
  "del"                       { return symbol(ChocoPyTokens.DEL); }
  "elif"                      { return symbol(ChocoPyTokens.ELIF); }
  "else"                      { return symbol(ChocoPyTokens.ELSE); }
  "except"                    { return symbol(ChocoPyTokens.EXCEPT); }
  "False"                     { return symbol(ChocoPyTokens.FALSE); }
  "finally"                   { return symbol(ChocoPyTokens.FINALLY); }
  "for"                       { return symbol(ChocoPyTokens.FOR); }
  "from"                      { return symbol(ChocoPyTokens.FROM); }
  "global"                    { return symbol(ChocoPyTokens.GLOBAL); }
  "if"                        { return symbol(ChocoPyTokens.IF); }
  "import"                    { return symbol(ChocoPyTokens.IMPORT); }
  "in"                        { return symbol(ChocoPyTokens.IN); }
  "is"                        { return symbol(ChocoPyTokens.IS); }
  "lambda"                    { return symbol(ChocoPyTokens.LAMBDA); }
  "None"                      { return symbol(ChocoPyTokens.NONE); }
  "nonlocal"                  { return symbol(ChocoPyTokens.NONLOCAL); }
  "not"                       { return symbol(ChocoPyTokens.NOT); }
  "or"                        { return symbol(ChocoPyTokens.OR); }
  "pass"                      { return symbol(ChocoPyTokens.PASS); }
  "raise"                     { return symbol(ChocoPyTokens.RAISE); }
  "return"                    { return symbol(ChocoPyTokens.RETURN); }
  "True"                      { return symbol(ChocoPyTokens.TRUE); }
  "try"                       { return symbol(ChocoPyTokens.TRY); }
  "while"                     { return symbol(ChocoPyTokens.WHILE); }
  "with"                      { return symbol(ChocoPyTokens.WITH); }
  "yield"                     { return symbol(ChocoPyTokens.YIELD); }

  /* Identifiers */
  {Identifier}                { return symbol(ChocoPyTokens.IDENTIFIER, yytext()); }

  /* Whitespace. */
  {WhiteSpace}                { /* ignore */ }
  {Comment}                   { /* ignore */ }

  /* Error fallback. */
  [^]                         { return symbol(ChocoPyTokens.UNRECOGNIZED); }
}

<STRING> {
  \"                          { yybegin(PROGRAM);

                                if (firstErrOfString != null) {
                                  // Known issue: the parser won't return the right string
                                  // for display for some reason, upon constructing the symbol
                                  // This is because the lexer prints yytext out directly and not
                                  // the second-argument value passed in here.
                                  //System.out.println(sbuff.toString() + firstErrOfString);

                                  return symbol(ChocoPyTokens.UNRECOGNIZED,
                                                sbuff.toString() + firstErrOfString,
                                                startingColumn );
                                } else if (isIDString) {
                                  return symbol(ChocoPyTokens.IDSTRING,
                                                sbuff.toString(),
                                                startingColumn );
                                } else {
                                  return symbol(ChocoPyTokens.STRING,
                                                sbuff.toString(),
                                                startingColumn );
                                }
                              }

  {Identifier}                { isIDString = true; sbuff.append( yytext() ); }

  {StringLiterals}            { isIDString = false; sbuff.append( yytext() ); }

  {Tab}                       { isIDString = false; sbuff.append("\t"); }

  {Newline}                   { isIDString = false; sbuff.append("\n"); }

  {Quote}                     { isIDString = false; sbuff.append("\""); }

  {Slash}                     { isIDString = false; sbuff.append("\\"); }

  \\[^]                       { isIDString = false;
                                if (firstErrOfString == null) {
                                  firstErrOfString = " <bad escape> ";
                                }
                                sbuff.append( "\\" + yytext().substring(1, yylength()) );
                              }

  [^]                         { isIDString = false;
                                if (firstErrOfString == null) {
                                  firstErrOfString = " <character(s) not in range> ";
                                }
                                sbuff.append( yytext() );
                              }
}

<YYINITIAL> {
  {WhiteSpace}*[^\r\n#]       {
                                // initialize stack with 0
                                if (indentStack.empty()) {
                                  indentStack.push(0);
                                }

                                String leadingText = yytext();

                                // calculate indentation level
                                String whitespace = leadingText.substring(0, yylength()-1);
                                int curIndent = countIndentation(whitespace);

                                if (curIndent > indentStack.peek()) {
                                  // we have an INDENT
                                  indentStack.push(curIndent);
                                  prepareForProgramState();
                                  return symbol(ChocoPyTokens.INDENT);
                                } else if (curIndent < indentStack.peek()) {
                                  // we possibly have multiple DEDENTs
                                  yypushback(yylength());
                                  indentStack.pop();

                                  // if we get here, then
                                  // indentStack.pop().peek() < curIndent < indentStack.peek()
                                  // which means we are at an unrecognized
                                  // indentation level.
                                  if (indentStack.peek() < curIndent) {
                                      return symbol(ChocoPyTokens.UNRECOGNIZED, "<bad indentation>" );
                                  }
                                  return symbol(ChocoPyTokens.DEDENT);
                                } else {
                                  // same indentation as previous line, so no INDENT or DEDENT
                                  prepareForProgramState();
                                }
                              }

  {WhiteSpace}*{Comment}      { /* ignore */ }
  {WhiteSpace}*{LineBreak}    { /* ignore */ }

}

<<EOF>>                       {
                                // we generate a DEDENT for each number > 0 remaining on the
                                // stack, if we reach EOF
                                if (!indentStack.empty() && indentStack.peek() > 0) {
                                  indentStack.pop();
                                  zzAtEOF = false;
                                  return symbol(ChocoPyTokens.DEDENT);
                                }
                                return symbol(ChocoPyTokens.EOF);
                              }

