package com.example.mutation_tester.mutations_applier.custom_patterns;

import com.example.mutation_tester.mutations_applier.MutationResult;
import com.example.mutation_tester.mutations_applier.CustomMutantPattern;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Safe Stream API mutations - only methods with identical signatures
 * These are commonly used and create meaningful semantic differences
 */
public class SafeStreamMethodReplacement implements CustomMutantPattern {

    private static final Map<String, String> MUTATION_MAP = new HashMap<>();

    static {
        // These mutations are type-safe and commonly used:
        MUTATION_MAP.put("findFirst", "findAny");        // Optional<T> -> Optional<T>
        MUTATION_MAP.put("findAny", "findFirst");        // Optional<T> -> Optional<T>
        MUTATION_MAP.put("anyMatch", "allMatch");        // boolean anyMatch(Predicate) -> boolean allMatch(Predicate)
        MUTATION_MAP.put("allMatch", "anyMatch");        // boolean allMatch(Predicate) -> boolean anyMatch(Predicate)
        MUTATION_MAP.put("noneMatch", "anyMatch");       // boolean noneMatch(Predicate) -> boolean anyMatch(Predicate)
        MUTATION_MAP.put("takeWhile", "dropWhile");      // Stream<T> takeWhile(Predicate) -> Stream<T> dropWhile(Predicate)
        MUTATION_MAP.put("dropWhile", "takeWhile");      // Stream<T> dropWhile(Predicate) -> Stream<T> takeWhile(Predicate)
    }

    @Override
    public MutationResult applyMutation(CompilationUnit cu) {
        List<String> affected = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();

            if (MUTATION_MAP.containsKey(methodName)) {
                // Check if this looks like a Stream method call
                if (isLikelyStreamCall(methodCall)) {
                    // Find enclosing method to track affected methods
                    methodCall.findAncestor(MethodDeclaration.class)
                            .ifPresent(md -> {
                                String fqName = fullyQualifiedName(md);
                                if (!affected.contains(fqName)) {
                                    affected.add(fqName);
                                }
                            });

                    // Apply the safe mutation
                    methodCall.setName(MUTATION_MAP.get(methodName));
                }
            }
        });

        return new MutationResult(cu, affected);
    }

    @Override
    public boolean checkIfCanMutate(String code) {
        return MUTATION_MAP.keySet().stream()
                .anyMatch(code::contains);
    }

    /**
     * Simple heuristic to identify Stream method calls
     */
    private boolean isLikelyStreamCall(MethodCallExpr methodCall) {
        // Look for Stream API patterns
        String code = methodCall.toString();
        return code.contains(".stream()") ||
               code.contains("Stream.") ||
               (methodCall.getScope().isPresent() &&
                methodCall.getScope().get().toString().contains("stream"));
    }

    /**
     * Utility to build "com.example.YourClass.yourMethod" from a MethodDeclaration.
     */
    private String fullyQualifiedName(MethodDeclaration md) {
        String pkg = md.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getName().toString())
                .orElse("");
        String cls = md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .map(c -> c.getNameAsString())
                .orElse("");
        String method = md.getNameAsString();
        return (pkg.isEmpty() ? "" : pkg + ".") + cls + "." + method;
    }
}
