package com.example.mutation_tester.mutations_applier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

public interface CustomMutantPattern {

    /**
     * Quick pre-check: parse, apply your mutation to a clone,
     * then compare ASTs. If nothing changed, we’ll skip the real work.
     */
    default boolean checkIfCanMutate(String source) {
        CompilationUnit original = StaticJavaParser.parse(source);
        CompilationUnit mutated  = applyMutation(original.clone()).getMutatedUnit();
        return !original.equals(mutated);
    }

    /**
     * Parse the source, run the pre-check, then either
     * skip or do the full mutation+affected-methods collection.
     */
    default MutationResult mutate(String source) {
        // skip heavy work if there’s nothing to do
        if (!checkIfCanMutate(source)) {
            CompilationUnit cu = StaticJavaParser.parse(source);
            return new MutationResult(cu, List.of());
        }

        CompilationUnit original = StaticJavaParser.parse(source);
        CompilationUnit working  = original.clone();

        // your implementation applies changes and gathers affected methods
        MutationResult result = applyMutation(working);

        // if the ASTs are still equal, normalize to “no changes”
        if (original.equals(result.getMutatedUnit())) {
            return new MutationResult(original, List.of());
        }

        return result;
    }

    /**
     * Do your AST edits and return both:
     *  - the mutated CompilationUnit
     *  - a List<String> of fully-qualified methods touched
     */
    MutationResult applyMutation(CompilationUnit cu);
}
