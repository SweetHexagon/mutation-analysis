package com.example.util;

import com.example.CommitPairWithFiles;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * (by type, fromText, toText), and writes the result to a new file prefixed with 'unique_'.
     */
    public static void filterUniqueOperations(String repoUrl) {
        String baseName = generateComparisonFileName(repoUrl);
        String inputPath = "src/main/resources/programOutput/" + baseName;
        String uniqueName = "filtered_" + baseName;
        String outputPath = "src/main/resources/programOutputFiltered/" + uniqueName;

        try {
            JsonNode root = mapper.readTree(new File(inputPath));
            ArrayNode fileResults = (ArrayNode) root.get("fileResults");
            Set<String> seen = new HashSet<>();

            for (JsonNode fileResult : fileResults) {
                ArrayNode ops = (ArrayNode) fileResult.get("editOperations");
                ArrayNode filtered = mapper.createArrayNode();
                for (JsonNode op : ops) {
                    String type = op.path("type").asText();
                    String fromText = op.path("fromText").asText();
                    String toText = op.path("toText").asText();
                    String key = type + "||" + fromText + "||" + toText;
                    if (seen.add(key)) {
                        filtered.add(op);
                    }
                }
                ((ObjectNode) fileResult).set("editOperations", filtered);
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), root);
            System.out.println("Wrote filtered comparison JSON to " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to filter unique operations: " + e.getMessage());
        }
    }


}

