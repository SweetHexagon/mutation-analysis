package com.example.mutation_tester.mutation_metadata_processing;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class MethodCallMapper {

    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("D:\\Java projects\\mutation-analysis\\repositories_for_tests\\jsoup");

        Map<String, Set<String>> methodToTestMap = new HashMap<>();
        Set<String> allTestMethods = new HashSet<>();

        Files.walk(projectRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> processFile(path, methodToTestMap, allTestMethods));

        System.out.println("\nTotal unique test methods: " + allTestMethods.size());

        Path outputFile = Paths.get("parsed_tests.txt");
        Files.write(outputFile, allTestMethods);
        System.out.println("Test methods written to: " + outputFile.toAbsolutePath());

        methodToTestMap.forEach((method, callers) -> {
            System.out.println("Method=" + method + " -> CalledByTests=" + callers);
        });
    }

    private static void processFile(Path path, Map<String, Set<String>> methodToTestMap, Set<String> allTestMethods) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = classDecl.getNameAsString();
                boolean isTestClass = className.contains("Test"); // ✅ Restore test class name check

                if (!isTestClass) return;

                classDecl.findAll(MethodDeclaration.class).forEach(methodDecl -> {
                    String methodName = methodDecl.getNameAsString();

                    Optional<PackageDeclaration> pkg = cu.getPackageDeclaration();
                    String packageName = pkg.map(pd -> pd.getNameAsString() + ".").orElse("");
                    String fullTestMethod = packageName + className + "#" + methodName;

                    boolean isTestMethod = methodDecl.getAnnotations().stream()
                            .map(AnnotationExpr::getNameAsString)
                            .anyMatch(name -> name.contains("Test"));

                    if (!isTestMethod) return;

                    allTestMethods.add(fullTestMethod);

                    methodDecl.findAll(MethodCallExpr.class).forEach(callExpr -> {
                        String calledMethod = callExpr.getNameAsString();
                        methodToTestMap
                                .computeIfAbsent(calledMethod, k -> new HashSet<>())
                                .add(fullTestMethod);
                    });
                });
            });

        } catch (IOException e) {
            System.err.println("Failed to parse: " + path);
        }
    }
}