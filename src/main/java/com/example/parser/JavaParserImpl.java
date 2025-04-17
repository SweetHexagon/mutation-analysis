package com.example.parser;

import com.example.javaparser.Java20Lexer;
import com.example.javaparser.Java20Parser;
import lombok.Getter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Getter
public class JavaParserImpl implements LanguageParser {
    private Java20Lexer lexer;
    private Java20Parser parser;
    private CommonTokenStream tokens;

    @Override
    public ParseTree parse(String filePath) {
        try {
            String code = new String(Files.readAllBytes(Paths.get(filePath)));
            CharStream input = CharStreams.fromString(code);
            lexer = new Java20Lexer(input);
            tokens = new CommonTokenStream(lexer);
            parser = new Java20Parser(tokens);

            disableErrorListeners();

            return parser.compilationUnit();
        } catch (Exception e) {
            System.err.println("Error reading file: " + filePath);
            return null;
        }
    }

    @Override
    public void disableErrorListeners() {
        if (lexer != null) {
            lexer.removeErrorListeners();
            lexer.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer,
                                        Object offendingSymbol,
                                        int line, int charPositionInLine,
                                        String msg, RecognitionException e) {
                    // silence lexer errors
                }
            });
        }

        if (parser != null) {
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer,
                                        Object offendingSymbol,
                                        int line, int charPositionInLine,
                                        String msg, RecognitionException e) {
                    // silence parser errors
                }
            });
        }
    }
}

