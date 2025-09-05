package com.example.mutation_tester.mutations_applier;


import com.example.mutation_tester.mutations_applier.custom_patterns.LoopBreakReplacement;
import com.example.mutation_tester.mutations_applier.custom_patterns.TakeWhileDropWhileReplacement;
import com.example.mutation_tester.mutations_applier.custom_patterns.SafeStreamMethodReplacement;
import com.example.mutation_tester.mutations_applier.custom_patterns.OptionalMethodReplacement;
import com.example.mutation_tester.mutations_applier.custom_patterns.AssertionMethodReplacement;
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
        customMutantPatternHashMap.put(CustomMutations.TAKE_WHILE_DROP_WHILE_REPLACEMENT, new TakeWhileDropWhileReplacement());
        customMutantPatternHashMap.put(CustomMutations.SAFE_STREAM_METHOD_REPLACEMENT, new SafeStreamMethodReplacement());
        customMutantPatternHashMap.put(CustomMutations.OPTIONAL_METHOD_REPLACEMENT, new OptionalMethodReplacement());
        customMutantPatternHashMap.put(CustomMutations.ASSERTION_METHOD_REPLACEMENT, new AssertionMethodReplacement());
    }

    public enum CustomMutations {
        LOOP_BREAK_REPLACEMENT,
        TAKE_WHILE_DROP_WHILE_REPLACEMENT,
        SAFE_STREAM_METHOD_REPLACEMENT,
        OPTIONAL_METHOD_REPLACEMENT,
        ASSERTION_METHOD_REPLACEMENT,
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

    /**
     * Identifies methods that can be mutated without actually applying the mutations.
     * This is used to get a list of all mutable methods before applying mutations one by one.
     *
     * @param mutation the type of mutation to apply
     * @param rootPath the root directory to scan
     * @return list of method signatures that can be mutated
     */
    public List<String> identifyMutableMethods(CustomMutations mutation, String rootPath) {
        Path rootDir = Paths.get(rootPath);
        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            System.err.println("Error: The path does not exist or is not a directory: " + rootPath);
            return Collections.emptyList();
        }

        CustomMutantPattern pattern = customMutantPatternHashMap.get(mutation);
        Set<String> allMutableMethods = new LinkedHashSet<>();

        try (var pathStream = Files.walk(rootDir)) {
            pathStream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/test/") && !p.toString().contains("\\test\\"))
                    .forEach(javaFile -> {
                        try {
                            String originalCode = Files.readString(javaFile);
                            // Apply mutation but don't save the result - just collect affected methods
                            MutationResult result = pattern.mutate(originalCode);
                            List<String> affected = result.getAffectedMethods();
                            allMutableMethods.addAll(affected);
                        } catch (IOException e) {
                            System.err.println("Error processing file: " + javaFile);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Failed to walk directory: " + rootPath);
            e.printStackTrace();
        }

        return new ArrayList<>(allMutableMethods);
    }

    /**
     * Applies mutation to a specific method only, leaving other methods unchanged.
     *
     * @param mutation the type of mutation to apply
     * @param rootPath the root directory to scan
     * @param targetMethodSignature the method signature to mutate (e.g., "org.example.Class.methodName")
     * @return true if the mutation was successfully applied, false otherwise
     */
    public boolean applyMutationToSpecificMethod(CustomMutations mutation, String rootPath, String targetMethodSignature) {
        Path rootDir = Paths.get(rootPath);
        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            System.err.println("Error: The path does not exist or is not a directory: " + rootPath);
            return false;
        }

        CustomMutantPattern pattern = customMutantPatternHashMap.get(mutation);
        AtomicBoolean mutationApplied = new AtomicBoolean(false);

        try (var pathStream = Files.walk(rootDir)) {
            pathStream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/test/") && !p.toString().contains("\\test\\"))
                    .forEach(javaFile -> {
                        try {
                            String originalCode = Files.readString(javaFile);
                            MutationResult result = pattern.mutate(originalCode);
                            List<String> affected = result.getAffectedMethods();

                            // Check if this file contains the target method
                            if (affected.contains(targetMethodSignature)) {
                                // Apply mutation only to the specific method
                                // We need to create a targeted mutation that only affects the specified method
                                MutationResult targetedResult = pattern.mutateSpecificMethod(originalCode, targetMethodSignature);

                                if (targetedResult != null && !targetedResult.getAffectedMethods().isEmpty()) {
                                    Files.writeString(javaFile, targetedResult.getMutatedUnit().toString());
                                    mutationApplied.set(true);
                                    System.out.println("Applied targeted mutation to: " + targetMethodSignature + " in " + javaFile);
                                }
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

        return mutationApplied.get();
    }
}
