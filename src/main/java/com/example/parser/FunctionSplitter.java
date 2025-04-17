package com.example.parser;

import com.example.cparser.CBaseListener;
import com.example.cparser.CParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import com.example.javaparser.Java20Parser;
import com.example.javaparser.Java20BaseListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a compilation-unit parse tree into a list of methodDeclaration sub-trees.
 */
public class FunctionSplitter {

    public static List<ParseTree> splitIntoFunctionTrees(ParseTree compilationUnit, LanguageParser parser) {
        List<ParseTree> functions = new ArrayList<>();

        ParseTreeListener listener = createListener(parser, functions);
        if (listener == null) {
            throw new UnsupportedOperationException("Unsupported parser type: " + parser.getClass().getSimpleName());
        }

        ParseTreeWalker.DEFAULT.walk(listener, compilationUnit);
        return functions;
    }

    private static ParseTreeListener createListener(LanguageParser parser, List<ParseTree> functions) {
        if (parser instanceof JavaParserImpl) {
            return new Java20BaseListener() {
                @Override
                public void enterMethodDeclaration(Java20Parser.MethodDeclarationContext ctx) {
                    functions.add(ctx);
                }
            };
        } else if (parser instanceof CParserImpl) {
            return new CBaseListener() {
                @Override
                public void enterFunctionDefinition(CParser.FunctionDefinitionContext ctx) {
                    functions.add(ctx);
                }
            };
        }
        // Add more parser conditions here as needed.

        return null;  // unsupported parser type
    }
}
