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
import java.util.*;
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
        return customMutantPatternHashMap.get(mutation).mutate(code).getMutatedUnit().toString() ;
    }


    public List<String> applyMutationToProjectDirectory(CustomMutations mutation, String rootPath) {
        Path rootDir = Paths.get(rootPath);
        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            System.err.println("Error: The path does not exist or is not a directory: " + rootPath);
            return Collections.emptyList();
        }

        CustomMutantPattern pattern = customMutantPatternHashMap.get(mutation);
        // use a set to dedupe across files, if desired
        Set<String> allAffected = new LinkedHashSet<>();

        try (var pathStream = Files.walk(rootDir)) {
            pathStream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/test/") && !p.toString().contains("\\test\\"))
                    .forEach(javaFile -> {
                        try {
                            String originalCode = Files.readString(javaFile);
                            // now returns MutationResult, not just String
                            MutationResult result = pattern.mutate(originalCode);
                            List<String> affected = result.getAffectedMethods();

                            if (!affected.isEmpty()) {
                                System.out.println("Mutated: " + javaFile);
                                // write out the mutated source
                                Files.writeString(javaFile, result.getMutatedUnit().toString());
                                allAffected.addAll(affected);
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

        // return a list of all affected method names
        return new ArrayList<>(allAffected);
    }



}
