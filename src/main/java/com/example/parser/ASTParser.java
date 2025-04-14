package com.example.parser;

import org.antlr.v4.runtime.tree.ParseTree;

public class ASTParser {

    public static ParseTree parseFile(String filePath) {
        LanguageParser parser = ParserFactory.getParser(filePath);
        if (parser == null) {
            System.err.println("Unsupported file type: " + filePath);
            return null;
        }

        ParseTree tree = parser.parse(filePath);
        if (tree == null) {
            System.err.println("Failed to parse file: " + filePath);
        }

        return tree;
    }

}

