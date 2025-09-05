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
 * Safe Optional API mutations - targets commonly used Optional methods
 * These create meaningful behavioral differences while maintaining type safety
 */
public class OptionalMethodReplacement implements CustomMutantPattern {

    private static final Map<String, String> MUTATION_MAP = new HashMap<>();

    static {
        // Type-safe Optional mutations:
        MUTATION_MAP.put("orElse", "orElseGet");         // T orElse(T) -> T orElseGet(Supplier<T>) - need to handle this carefully
        MUTATION_MAP.put("orElseThrow", "orElse");       // T orElseThrow() -> T orElse(T) - need default value
        MUTATION_MAP.put("ifPresent", "ifPresentOrElse"); // void ifPresent(Consumer) -> void ifPresentOrElse(Consumer, Runnable)
    }

    // Simpler, always safe mutations
    private static final Map<String, String> SAFE_MUTATION_MAP = new HashMap<>();

    static {
        SAFE_MUTATION_MAP.put("isPresent", "isEmpty");   // boolean isPresent() -> boolean isEmpty() (Java 11+)
        SAFE_MUTATION_MAP.put("isEmpty", "isPresent");   // boolean isEmpty() -> boolean isPresent() (Java 11+)
    }

    @Override
    public MutationResult applyMutation(CompilationUnit cu) {
        List<String> affected = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();

            // Only apply the always-safe mutations for now
            if (SAFE_MUTATION_MAP.containsKey(methodName)) {
                // Check if this looks like an Optional method call
                if (isLikelyOptionalCall(methodCall)) {
                    // Find enclosing method to track affected methods
                    methodCall.findAncestor(MethodDeclaration.class)
                            .ifPresent(md -> {
                                String fqName = fullyQualifiedName(md);
                                if (!affected.contains(fqName)) {
                                    affected.add(fqName);
                                }
                            });

                    // Apply the safe mutation
                    methodCall.setName(SAFE_MUTATION_MAP.get(methodName));
                }
            }
        });

        return new MutationResult(cu, affected);
    }

    @Override
    public boolean checkIfCanMutate(String code) {
        return SAFE_MUTATION_MAP.keySet().stream()
                .anyMatch(code::contains);
    }

    /**
     * Simple heuristic to identify Optional method calls
     */
    private boolean isLikelyOptionalCall(MethodCallExpr methodCall) {
        // Look for Optional patterns
        String code = methodCall.toString();
        return code.contains("Optional.") ||
               code.contains(".optional") ||
               (methodCall.getScope().isPresent() &&
                methodCall.getScope().get().toString().toLowerCase().contains("optional"));
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
