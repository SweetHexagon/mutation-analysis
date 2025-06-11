package com.example.mutation_tester.mutations_applier.custom_patterns;

import com.example.mutation_tester.mutations_applier.CustomMutantPattern;
import com.github.javaparser.ast.CompilationUnit;

public class ProcessAll implements CustomMutantPattern {
    @Override
    public CompilationUnit applyMutation(CompilationUnit cu) {
        return cu;
    }
    @Override
    public boolean checkIfCanMutate(String text){
        return true;
    }

}
