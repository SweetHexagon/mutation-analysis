package com.example.util;

import com.example.CommitPairWithFiles;
import com.example.classifier.MutationKind;
import com.example.dto.CommitPairDTO;
import com.example.dto.FileResultDto;
import com.example.mapper.ResultMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

import com.example.pojo.FileResult;

public class JsonUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void initializeJsonOutput(String repoUrl) {
        String outputPath = "src/main/resources/programOutput/" + generateComparisonFileName(repoUrl);
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("repoUrl", repoUrl);
            root.set("fileResults", mapper.createArrayNode());
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), root);
        } catch (IOException e) {
            System.err.println("Failed to initialize JSON output: " + e.getMessage());
        }
    }

    public static void appendPatternCounts(String repoUrl,
                                           ConcurrentMap<MutationKind,Integer> counts) {
        String path = "src/main/resources/programOutput/" + generateComparisonFileName(repoUrl);
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(new File(path));
            ObjectNode pc = mapper.createObjectNode();
            for (var e : counts.entrySet()) {
                pc.put(e.getKey().name(), e.getValue());
            }
            root.set("patternCounts", pc);
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), root);
        } catch (IOException e) {
            System.err.println("Failed to append patternCounts: " + e.getMessage());
        }
    }

    public static void appendBatchResults(String repoUrl, List<FileResultDto> batchResults) {
        String outputPath = "src/main/resources/programOutput/" + generateComparisonFileName(repoUrl);
        try {
            byte[] jsonData = Files.readAllBytes(new File(outputPath).toPath());
            ObjectNode root = (ObjectNode) mapper.readTree(jsonData);
            ArrayNode fileResultsNode = (ArrayNode) root.get("fileResults");

            for (FileResultDto resultDto : batchResults) {
                fileResultsNode.addPOJO(resultDto);
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), root);
        } catch (IOException e) {
            System.err.println("Failed to append batch results to JSON: " + e.getMessage());
        }
    }


    public static void writeCommitPairDTOsToFile(List<CommitPairDTO> dtos, String path) {
        try {
            new File(path).getParentFile().mkdirs();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), dtos);
        } catch (IOException e) {
            System.err.println("Failed to write DTOs: " + e.getMessage());
        }
    }

    public static List<CommitPairDTO> readCommitPairDTOsFromFile(String path) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return Arrays.asList(mapper.readValue(new File(path), CommitPairDTO[].class));
        } catch (IOException e) {
            System.err.println("Failed to read DTOs: " + e.getMessage());
            return List.of();
        }
    }

    public static String generateComparisonFileName(String repoUrl) {
        if (repoUrl == null || !repoUrl.startsWith("https://github.com/")) {
            throw new IllegalArgumentException("Invalid GitHub URL");
        }

        String[] parts = repoUrl.replace("https://github.com/", "").split("/");

        if (parts.length < 2) {
            throw new IllegalArgumentException("URL does not contain both owner and repository name");
        }

        String owner = parts[0];
        String repo = parts[1];
        repo = repo.replace(".git", "");
        return "comparison_" + owner + "_" + repo + ".json";
    }
    /**
     * Reads the comparison JSON for the given repoUrl, filters out duplicate edit operations
     * (by type, fromText, toText), removes any fileResults that end up with no operations,
     * and writes the result to a new file prefixed with 'unique_'.
     */
    public static void filterUniqueOperations(String repoUrl) {
        String baseName = generateComparisonFileName(repoUrl);
        String inputPath = "src/main/resources/programOutput/" + baseName;
        String uniqueName = "filtered_" + baseName;
        String outputPath = "src/main/resources/programOutputFiltered/" + uniqueName;

        try {
            // 1. Read the full input JSON
            JsonNode root = mapper.readTree(new File(inputPath));
            ArrayNode fileResults = (ArrayNode) root.get("fileResults");

            // Option A: grab the original patternCounts object if it exists
            JsonNode originalPatternCounts = root.get("patternCounts");

            // Prepare your filter structures
            Set<String> seen = new HashSet<>();
            ArrayNode filteredFileResults = mapper.createArrayNode();

            // Option B: recompute counts of each pattern type
            Map<String,Integer> recomputedCounts = new HashMap<>();

            for (JsonNode fileResult : fileResults) {
                ArrayNode ops = (ArrayNode) fileResult.get("editOperations");
                ArrayNode filteredOps = mapper.createArrayNode();

                for (JsonNode op : ops) {
                    String type     = op.path("type").asText();
                    String fromText = op.path("fromText").asText();
                    String toText   = op.path("toText").asText();
                    String key      = type + "||" + fromText + "||" + toText;

                    if (seen.add(key)) {
                        filteredOps.add(op);

                        // ------ for Option 2: bump the count for this pattern ------
                        recomputedCounts.put(
                                type,
                                recomputedCounts.getOrDefault(type, 0) + 1
                        );
                    }
                }

                // Only include fileResult if there are operations left
                if (filteredOps.size() > 0) {
                    ObjectNode clone = ((ObjectNode) fileResult).deepCopy();
                    clone.set("editOperations", filteredOps);
                    filteredFileResults.add(clone);
                }
            }

            // Build output root
            ObjectNode outRoot = mapper.createObjectNode();
            outRoot.put("repoUrl", repoUrl);
            outRoot.set("fileResults", filteredFileResults);

            // ====== ATTACH METRICS HERE ======

            // If you want to reuse the original metrics block:
            if (originalPatternCounts != null) {
                outRoot.set("patternCounts", originalPatternCounts);
            }

            // ==================================

            // Finally write it out
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(outputPath), outRoot);

            System.out.println("Wrote unique comparison JSON to " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to filter unique operations: " + e.getMessage());
        }
    }

    /**
     * Aggregates all filtered JSON files (either with `fileResults` wrappers or
     * direct `editOperations` arrays) in a directory into one big JSON of unique ops.
     * @param filteredDirPath directory containing JSON files to aggregate
     * @param outputPath where to write the combined JSON
     */
    public static void aggregateUniqueOperations(String filteredDirPath, String outputPath) {
        try {
            Set<String> seen = new HashSet<>();
            ArrayNode operations = mapper.createArrayNode();
            Map<String, Integer> aggregatedPatternCounts = new HashMap<>();

            File dir = new File(filteredDirPath);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));

            if (files != null) {
                for (File f : files) {
                    JsonNode root = mapper.readTree(f);

                    // Aggregate editOperations uniquely
                    if (root.has("fileResults")) {
                        for (JsonNode fileResult : root.get("fileResults")) {
                            ArrayNode ops = (ArrayNode) fileResult.get("editOperations");
                            if (ops != null) extractOps(ops, seen, operations);
                        }
                    } else if (root.has("editOperations")) {
                        ArrayNode ops = (ArrayNode) root.get("editOperations");
                        if (ops != null) extractOps(ops, seen, operations);
                    }

                    // Aggregate the patternCounts block
                    JsonNode patternCounts = root.get("patternCounts");
                    if (patternCounts != null && patternCounts.isObject()) {
                        patternCounts.fields().forEachRemaining(entry -> {
                            String pattern = entry.getKey();
                            int count = entry.getValue().asInt();
                            aggregatedPatternCounts.merge(pattern, count, Integer::sum);
                        });
                    }
                }
            }

            // Build output JSON
            ObjectNode outRoot = mapper.createObjectNode();
            outRoot.set("editOperations", operations);

            // Add the aggregated patternCounts
            ObjectNode patternCountsNode = mapper.createObjectNode();
            aggregatedPatternCounts.forEach(patternCountsNode::put);
            outRoot.set("patternCounts", patternCountsNode);

            // Write to file
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), outRoot);
            System.out.println("Aggregated unique operations and patternCounts written to " + outputPath);

        } catch (IOException e) {
            System.err.println("Failed to aggregate unique operations: " + e.getMessage());
        }
    }


    /**
     * Helper to dedupe and append operations
     */
    private static void extractOps(ArrayNode ops, Set<String> seen, ArrayNode target) {
        for (JsonNode op : ops) {
            String type = op.path("type").asText();
            String fromText = op.path("fromText").asText();
            String toText = op.path("toText").asText();
            String key = type + "||" + fromText + "||" + toText;
            if (seen.add(key)) {
                target.add(op);
            }
        }
    }

    public static void refilterAllProgramOutput(String programOutputDirPath) {
        File dir = new File(programOutputDirPath);
        File[] files = dir.listFiles((d, name) -> name.startsWith("comparison_") && name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String fileName = f.getName(); // e.g. comparison_owner_repo.json
                String base = fileName.substring("comparison_".length(), fileName.length() - ".json".length());
                int idx = base.indexOf('_');
                if (idx > 0) {
                    String owner = base.substring(0, idx);
                    String repo = base.substring(idx + 1);
                    String repoUrl = "https://github.com/" + owner + "/" + repo;
                    filterUniqueOperations(repoUrl);
                }
            }
        }
    }

}

