package com.example.mutation_tester.mutations_applier;

import com.github.javaparser.ast.CompilationUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Result of applying a mutation:
 *  - the possibly-mutated AST
 *  - the list of methods whose bodies changed
 */
@AllArgsConstructor
@Getter @Setter
public class MutationResult {
    private final CompilationUnit mutatedUnit;
    private final List<String> affectedMethods;

    /** Convenience: mutated source as text */
    public String asString() {
        return mutatedUnit.toString();
    }
}
