package com.example.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a compilation-unit parse tree into a list of methodDeclaration sub-trees.
 */
public class FunctionSplitter {
    public static List<MethodDeclaration> splitIntoMethods(CompilationUnit cu) {
        return new ArrayList<>(cu.findAll(MethodDeclaration.class));
    }
}
