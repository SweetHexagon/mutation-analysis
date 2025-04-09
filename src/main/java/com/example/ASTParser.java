package com.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import com.example.cparser.CParser;
import com.example.cparser.CLexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ASTParser {

    public static ParseTree parseCFile(String filePath) {

        try {
            String code = new String(Files.readAllBytes(Paths.get(filePath)));
            CharStream input = CharStreams.fromString(code);
            CLexer lexer = new CLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CParser parser = new CParser(tokens);

            ParseTree tree = parser.compilationUnit();

            return tree;
        } catch (IOException e) {
            System.err.println("Błąd odczytu pliku: " + filePath);
            return null;
        }
    }








}
