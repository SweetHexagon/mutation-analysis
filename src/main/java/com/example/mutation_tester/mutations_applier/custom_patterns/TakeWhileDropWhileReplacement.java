package com.example.mutation_tester.mutations_applier.custom_patterns;

import com.example.mutation_tester.mutations_applier.MutationResult;
import com.example.mutation_tester.mutations_applier.CustomMutantPattern;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.List;

public class TakeWhileDropWhileReplacement implements CustomMutantPattern {

    @Override
    public MutationResult applyMutation(CompilationUnit cu) {
        List<String> affected = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();

            if ("takeWhile".equals(methodName)) {
                // Find enclosing method to track affected methods
                methodCall.findAncestor(MethodDeclaration.class)
                        .ifPresent(md -> {
                            String fqName = fullyQualifiedName(md);
                            if (!affected.contains(fqName)) {
                                affected.add(fqName);
                            }
                        });

                // Replace takeWhile with dropWhile
                methodCall.setName("dropWhile");
            }
        });

        return new MutationResult(cu, affected);
    }

    @Override
    public boolean checkIfCanMutate(String code) {
        return code.contains("takeWhile");
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
