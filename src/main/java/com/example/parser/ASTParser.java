package com.example.parser;

import com.example.pojo.ParsedFile;
import org.antlr.v4.runtime.tree.ParseTree;

public class ASTParser {
    public static ParsedFile parseFile(String filePath) {
        LanguageParser parser = ParserFactory.getParser(filePath);
        if (parser == null) {
            System.err.println("Unsupported file type: " + filePath);
            return null;
        }

        parser.disableErrorListeners();
        ParseTree tree = parser.parse(filePath);
        if (tree == null) {
            System.err.println("Error parsing file: " + filePath);
            return null;
        }

        return new ParsedFile(tree, parser.getTokens(), parser);
    }
}

