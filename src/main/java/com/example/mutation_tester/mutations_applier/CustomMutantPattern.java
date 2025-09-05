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

    /**
     * Apply mutation only to a specific method signature.
     * Default implementation falls back to regular mutation if the target method is affected.
     *
     * @param source the source code to mutate
     * @param targetMethodSignature the specific method to mutate (e.g., "org.example.Class.methodName")
     * @return MutationResult with only the targeted method mutated, or null if target not found
     */
    default MutationResult mutateSpecificMethod(String source, String targetMethodSignature) {
        // First, check if the target method would be affected by regular mutation
        MutationResult fullMutation = mutate(source);

        if (fullMutation.getAffectedMethods().contains(targetMethodSignature)) {
            // The target method is in the affected list, so we can proceed
            CompilationUnit original = StaticJavaParser.parse(source);
            CompilationUnit working = original.clone();

            // Apply mutation but filter results to only include the target method
            MutationResult result = applyMutationToSpecificMethod(working, targetMethodSignature);

            // Return the result only if the target method was actually mutated
            if (result != null && result.getAffectedMethods().contains(targetMethodSignature)) {
                return result;
            }
        }

        // If target method not found or not affected, return null
        return null;
    }

    /**
     * Apply mutation to a specific method within the compilation unit.
     * This is meant to be overridden by implementations that support targeted mutations.
     * Default implementation falls back to applying all mutations and filtering the results.
     *
     * @param cu the compilation unit to mutate
     * @param targetMethodSignature the specific method to mutate
     * @return MutationResult with only the targeted method mutated, or null if not applicable
     */
    default MutationResult applyMutationToSpecificMethod(CompilationUnit cu, String targetMethodSignature) {
        // Default implementation: apply all mutations and check if target method is affected
        MutationResult fullResult = applyMutation(cu.clone());

        if (fullResult.getAffectedMethods().contains(targetMethodSignature)) {
            // Filter the result to only include the target method
            return new MutationResult(fullResult.getMutatedUnit(), List.of(targetMethodSignature));
        }

        return null;
    }
}
