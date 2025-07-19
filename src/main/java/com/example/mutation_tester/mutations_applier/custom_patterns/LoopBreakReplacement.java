package com.example.mutation_tester.mutations_applier.custom_patterns;

import com.example.mutation_tester.mutations_applier.MutationResult;
import com.example.mutation_tester.mutations_applier.CustomMutantPattern;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.List;

public class LoopBreakReplacement implements CustomMutantPattern {

    @Override
    public MutationResult applyMutation(CompilationUnit cu) {
        List<String> affected = new ArrayList<>();

        cu.findAll(ContinueStmt.class).forEach(continueStmt -> {
            // find enclosing method
            continueStmt.findAncestor(MethodDeclaration.class)
                    .ifPresent(md -> {
                        String fqName = fullyQualifiedName(md);
                        if (!affected.contains(fqName)) {
                            affected.add(fqName);
                        }
                    });
            // replace the continue with a break
            continueStmt.replace(new BreakStmt());
        });

        return new MutationResult(cu, affected);
    }

    @Override
    public boolean checkIfCanMutate(String code) {
        return code.contains("continue");
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
