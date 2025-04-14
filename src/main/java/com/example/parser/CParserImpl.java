package com.example.parser;

import com.example.cparser.CLexer;
import com.example.cparser.CParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CParserImpl implements LanguageParser {

    @Override
    public ParseTree parse(String filePath) {
        try {
            String code = new String(Files.readAllBytes(Paths.get(filePath)));
            CharStream input = CharStreams.fromString(code);
            CLexer lexer = new CLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CParser parser = new CParser(tokens);

            return parser.compilationUnit();
        } catch (IOException e) {
            System.err.println("Błąd odczytu pliku: " + filePath);
            return null;
        }
    }
}

