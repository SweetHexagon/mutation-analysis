package com.example.parser;

import com.example.cparser.CLexer;
import com.example.cparser.CParser;
import com.example.javaparser.Java20Lexer;
import com.example.javaparser.Java20Parser;
import lombok.Getter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Getter
public class CParserImpl implements LanguageParser {
    private CLexer lexer;
    private CParser parser;
    private CommonTokenStream tokens;

    @Override
    public ParseTree parse(String filePath) {
        try {
            String code = new String(Files.readAllBytes(Paths.get(filePath)));
            CharStream input = CharStreams.fromString(code);
            lexer = new CLexer(input);
            tokens = new CommonTokenStream(lexer);
            parser = new CParser(tokens);

            disableErrorListeners();

            parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
            parser.setErrorHandler(new BailErrorStrategy());

            try {
                return parser.compilationUnit();
            } catch (ParseCancellationException ex) {
                parser.reset();
                parser.getInterpreter().setPredictionMode(PredictionMode.LL);
                parser.setErrorHandler(new DefaultErrorStrategy());
                return parser.compilationUnit();
            }

        } catch (IOException e) {
            System.err.println("Błąd odczytu pliku: " + filePath);
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

    @Override
    public Parser getAntlrParser() {
        return parser;
    }
}

