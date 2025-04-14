package com.example.parser;

import com.example.javaparser.Java8Lexer;
import com.example.javaparser.Java8Parser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JavaParserImpl implements LanguageParser {

    @Override
    public ParseTree parse(String filePath) {
        try {
            String code = new String(Files.readAllBytes(Paths.get(filePath)));
            CharStream input = CharStreams.fromString(code);
            Java8Lexer lexer = new Java8Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            Java8Parser parser = new Java8Parser(tokens);

            return parser.compilationUnit();
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath);
            return null;
        }
    }
}

