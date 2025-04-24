package com.example.parser;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

public interface LanguageParser {
    ParseTree parse(String filePath);
    void disableErrorListeners();
    CommonTokenStream getTokens();
    Parser getAntlrParser();
}
