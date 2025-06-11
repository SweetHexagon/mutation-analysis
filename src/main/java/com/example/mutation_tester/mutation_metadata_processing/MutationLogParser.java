package com.example.mutation_tester.mutation_metadata_processing;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MutationLogParser {

    public static Map<String, Set<String>> parseMutationTrace(String logFilePath) {
        Map<String, Set<String>> methodToTests = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Expected format:
                // [MUTATION-TRACE] MutatedMethod=<signature> CalledByTest=<test>
                if (!line.startsWith("[MUTATION-TRACE]")) continue;

                int methodIndex = line.indexOf("MutatedMethod=");
                int testIndex = line.indexOf("CalledByTest=");

                if (methodIndex == -1 || testIndex == -1) continue;

                String methodSignature = line.substring(methodIndex + "MutatedMethod=".length(), testIndex).trim();
                String testSignature = line.substring(testIndex + "CalledByTest=".length()).trim();

                methodToTests
                        .computeIfAbsent(methodSignature, k -> new HashSet<>())
                        .add(testSignature);
            }
        } catch (IOException e) {
            System.err.println("Error reading mutation trace log: " + e.getMessage());
        }

        return methodToTests;
    }

    // Optional: pretty print it
    public static void printMethodToTests(Map<String, Set<String>> map) {
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }
}
