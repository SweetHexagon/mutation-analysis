package com.example.mutation_tester.mutations_applier;


import com.example.mutation_tester.mutations_applier.custom_patterns.LoopBreakReplacement;
import com.example.mutation_tester.mutations_applier.custom_patterns.ProcessAll;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class MutationApplier {
    final HashMap<CustomMutations, CustomMutantPattern> customMutantPatternHashMap = new HashMap<>();

    public MutationApplier(){
        customMutantPatternHashMap.put(CustomMutations.LOOP_BREAK_REPLACEMENT, new LoopBreakReplacement());
        customMutantPatternHashMap.put(CustomMutations.PROCESS_ALL, new ProcessAll());

    }
    public enum CustomMutations {
        LOOP_BREAK_REPLACEMENT,
        PROCESS_ALL
    }
    public String getMutatedCode(CustomMutations mutation, String code){
        return customMutantPatternHashMap.get(mutation).mutate(code);
    }

    public void triggerCrashOnMutatedFiles(CustomMutations mutation, String rootPath) {
        Path rootDir = Paths.get(rootPath);

        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            System.err.println("Error: The path does not exist or is not a directory: " + rootPath);
            return;
        }

        CustomMutantPattern pattern = customMutantPatternHashMap.get(mutation);
        AtomicInteger mutatedCount = new AtomicInteger();

        try (var pathStream = Files.walk(rootDir)) {
            pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/test/") && !path.toString().contains("\\test\\"))
                    .forEach(javaFile -> {
                        try {
                            String originalCode = Files.readString(javaFile);
                            CompilationUnit compilationUnit = StaticJavaParser.parse(originalCode);
                            AtomicBoolean fileModified = new AtomicBoolean(false);

                            compilationUnit.findAll(MethodDeclaration.class).forEach(method -> {
                                method.getBody().ifPresent(body -> {
                                    // Create a copy of the method to test mutation
                                    MethodDeclaration methodCopy = method.clone();

                                    if (pattern.checkIfCanMutate(methodCopy.toString())) {
                                        // Get class name
                                        Optional<ClassOrInterfaceDeclaration> parentClass = method.findAncestor(ClassOrInterfaceDeclaration.class);
                                        String className = parentClass.map(ClassOrInterfaceDeclaration::getNameAsString).orElse("UnknownClass");

                                        // Try to get package
                                        String packageName = compilationUnit.getPackageDeclaration()
                                                .map(pd -> pd.getName().asString())
                                                .orElse("");

                                        // Full class name
                                        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;

                                        // Method name and parameters
                                        String methodName = method.getNameAsString();
                                        String paramTypes = method.getParameters().stream()
                                                .map(p -> p.getType().toString())
                                                .collect(Collectors.joining(", "));

                                        // Create the full method signature string
                                        String methodSignature = fullClassName + "#" + methodName + "(" + paramTypes + ")";

                                        // Prepare Java code block with the methodSignature as a local variable
                                        String loggingCode =
                                                "{\n" +
                                                        "    String __mut_sig = \"" + methodSignature + "\";\n" +
                                                        "    StackTraceElement[] __mut_trace = Thread.currentThread().getStackTrace();\n" +
                                                        "    for (int __mut_i = 0; __mut_i < __mut_trace.length; __mut_i++) {\n" +
                                                        "        String __mut_cls = __mut_trace[__mut_i].getClassName();\n" +
                                                        "        String __mut_mtd = __mut_trace[__mut_i].getMethodName();\n" +
                                                        "        if (__mut_cls.contains(\"Test\") || __mut_mtd.startsWith(\"test\")) {\n" +
                                                        "            try (java.io.FileWriter __mut_fw = new java.io.FileWriter(\"mutation-trace.log\", true)) {\n" +
                                                        "                __mut_fw.write(\"[MUTATION-TRACE] MutatedMethod=\" + __mut_sig + \" CalledByTest=\" + __mut_cls + \"#\" + __mut_mtd + \"\\n\");\n" +
                                                        "            } catch (Exception __mut_e) {\n" +
                                                        "                System.err.println(\"[Injected] Failed to write mutation trace: \" + __mut_e);\n" +
                                                        "            }\n" +
                                                        "            break;\n" +
                                                        "        }\n" +
                                                        "    }\n" +
                                                        "}";


                                        BlockStmt loggingBlock = StaticJavaParser.parseBlock(loggingCode);
                                        body.getStatements().addAll(0, loggingBlock.getStatements());
                                        mutatedCount.getAndIncrement();
                                        fileModified.set(true);
                                        System.out.println("Log injected into method: " + methodSignature + " in " + javaFile);
                                    }



                                });
                            });

                            if (fileModified.get()) {
                                Files.writeString(javaFile, compilationUnit.toString());
                            }

                        } catch (IOException e) {
                            System.err.println("Error processing file: " + javaFile);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Failed to walk directory: " + rootPath);
            e.printStackTrace();
        }
    }


    public void applyMutationToProjectDirectory(CustomMutations mutation, String rootPath) {
        Path rootDir = Paths.get(rootPath);

        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            System.err.println("Error: The path does not exist or is not a directory: " + rootPath);
            return;
        }

        CustomMutantPattern pattern = customMutantPatternHashMap.get(mutation);

        try (var pathStream = Files.walk(rootDir)) {
            pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/test/") && !path.toString().contains("\\test\\"))
                    .forEach(javaFile -> {
                        try {
                            String originalCode = Files.readString(javaFile);
                            String mutatedCode = pattern.mutate(originalCode);

                            String normalizedOriginal = originalCode.replaceAll("[^a-zA-Z0-9{}();]", "");
                            String normalizedMutated  = mutatedCode.replaceAll("[^a-zA-Z0-9{}();]", "");

                            if (!normalizedOriginal.equals(normalizedMutated)) {
                                System.out.println("Mutated: " + javaFile);
                                Files.writeString(javaFile, mutatedCode);
                            }
                        } catch (IOException e) {
                            System.err.println("Error processing file: " + javaFile);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Failed to walk directory: " + rootPath);
            e.printStackTrace();
        }
    }



}
