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
 * Safe assertion method mutations - targets commonly used assertion methods
 * These create meaningful test behavior differences while maintaining type safety
 */
public class AssertionMethodReplacement implements CustomMutantPattern {

    private static final Map<String, String> MUTATION_MAP = new HashMap<>();

    static {
        // JUnit/TestNG assertion mutations (same signatures):
        MUTATION_MAP.put("assertTrue", "assertFalse");     // void assertTrue(boolean) -> void assertFalse(boolean)
        MUTATION_MAP.put("assertFalse", "assertTrue");     // void assertFalse(boolean) -> void assertTrue(boolean)
        MUTATION_MAP.put("assertNotNull", "assertNull");   // void assertNotNull(Object) -> void assertNull(Object)
        MUTATION_MAP.put("assertNull", "assertNotNull");   // void assertNull(Object) -> void assertNotNull(Object)

        // Hamcrest matchers:
        MUTATION_MAP.put("equalTo", "not");               // Matcher<T> equalTo(T) -> Matcher<T> not(Matcher<T>)
        MUTATION_MAP.put("is", "isNot");                  // Matcher<T> is(T) -> Matcher<T> isNot(T)
        MUTATION_MAP.put("hasSize", "not");               // For collections

        // AssertJ assertions:
        MUTATION_MAP.put("isEqualTo", "isNotEqualTo");     // AbstractAssert isEqualTo(Object) -> AbstractAssert isNotEqualTo(Object)
        MUTATION_MAP.put("isNotEqualTo", "isEqualTo");     // AbstractAssert isNotEqualTo(Object) -> AbstractAssert isEqualTo(Object)
        MUTATION_MAP.put("isTrue", "isFalse");             // AbstractBooleanAssert isTrue() -> AbstractBooleanAssert isFalse()
        MUTATION_MAP.put("isFalse", "isTrue");             // AbstractBooleanAssert isFalse() -> AbstractBooleanAssert isTrue()
        MUTATION_MAP.put("isNotNull", "isNull");           // AbstractObjectAssert isNotNull() -> AbstractObjectAssert isNull()
        MUTATION_MAP.put("isNull", "isNotNull");           // AbstractObjectAssert isNull() -> AbstractObjectAssert isNotNull()
        MUTATION_MAP.put("isEmpty", "isNotEmpty");         // AbstractCharSequenceAssert isEmpty() -> AbstractCharSequenceAssert isNotEmpty()
        MUTATION_MAP.put("isNotEmpty", "isEmpty");         // AbstractCharSequenceAssert isNotEmpty() -> AbstractCharSequenceAssert isEmpty()
        MUTATION_MAP.put("contains", "doesNotContain");    // AbstractCharSequenceAssert contains(CharSequence) -> AbstractCharSequenceAssert doesNotContain(CharSequence)
        MUTATION_MAP.put("doesNotContain", "contains");    // AbstractCharSequenceAssert doesNotContain(CharSequence) -> AbstractCharSequenceAssert contains(CharSequence)
    }

    @Override
    public MutationResult applyMutation(CompilationUnit cu) {
        List<String> affected = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();

            if (MUTATION_MAP.containsKey(methodName)) {
                // Check if this looks like an assertion method call
                if (isLikelyAssertionCall(methodCall)) {
                    // Find enclosing method to track affected methods
                    methodCall.findAncestor(MethodDeclaration.class)
                            .ifPresent(md -> {
                                String fqName = fullyQualifiedName(md);
                                if (!affected.contains(fqName)) {
                                    affected.add(fqName);
                                }
                            });

                    // Apply the mutation
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
     * Simple heuristic to identify assertion method calls
     */
    private boolean isLikelyAssertionCall(MethodCallExpr methodCall) {
        String code = methodCall.toString();
        String scope = methodCall.getScope().map(Object::toString).orElse("");

        // Look for assertion patterns
        return code.contains("assert") ||
               code.contains("Assert.") ||
               scope.contains("assert") ||
               scope.contains("Assert") ||
               code.contains("Assertions.") ||
               code.contains("assertThat") ||
               scope.contains("assertThat") ||
               // Hamcrest patterns
               code.contains("equalTo") ||
               code.contains("hasSize") ||
               // Method names that are likely assertions
               methodCall.getNameAsString().startsWith("assert") ||
               methodCall.getNameAsString().startsWith("is") ||
               methodCall.getNameAsString().contains("Equal") ||
               methodCall.getNameAsString().contains("Contain");
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
