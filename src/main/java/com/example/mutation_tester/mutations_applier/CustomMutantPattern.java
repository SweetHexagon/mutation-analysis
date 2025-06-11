package com.example.mutation_tester.mutations_applier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

public interface CustomMutantPattern {
     default String mutate(String text){

         if (checkIfCanMutate(text)) {
             CompilationUnit mutated = applyMutation(StaticJavaParser.parse(text));
             return mutated.toString();
         }

         return text;
     };

    default boolean checkIfCanMutate(String text) {
        CompilationUnit original = StaticJavaParser.parse(text);
        CompilationUnit mutated = applyMutation(original.clone());

        return !original.equals(mutated);
    }

    CompilationUnit applyMutation(CompilationUnit cu);

}
