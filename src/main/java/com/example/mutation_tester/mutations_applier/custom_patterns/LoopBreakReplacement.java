package com.example.mutation_tester.mutations_applier.custom_patterns;

import com.example.mutation_tester.mutations_applier.CustomMutantPattern;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.*;


public class LoopBreakReplacement implements CustomMutantPattern {

    @Override
    public CompilationUnit applyMutation(CompilationUnit cu) {
        cu.findAll(ContinueStmt.class).forEach(continueStmt -> {
            continueStmt.replace(new BreakStmt());
        });
        return cu;
    }

    @Override
    public boolean checkIfCanMutate(String code) {
        return code.matches("(?s).*\\bcontinue\\b.*");
    }
}
