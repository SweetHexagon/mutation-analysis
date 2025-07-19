package com.example.mutation_tester.mutations_applier.custom_patterns;

import com.example.mutation_tester.mutations_applier.CustomMutantPattern;
import com.example.mutation_tester.mutations_applier.MutationResult;
import com.github.javaparser.ast.CompilationUnit;

import java.util.Collections;

public class ProcessAll implements CustomMutantPattern {

    @Override
    public MutationResult applyMutation(CompilationUnit cu) {
        // No actual changes, so no affected methods
        return new MutationResult(cu, Collections.emptyList());
    }

    // You can omit checkIfCanMutate() since the default `mutate(...)`
    // will detect "no change" and return an empty list anyway.
}
