package com.example.mutation_tester.mutation_metadata_processing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodCallMapper {

    // Constants for default execution via main method
    private static final String XML_REPORT_PATH = "repositories_for_tests/jsoup/target/pit-reports/linecoverage.xml";
    private static final String JSON_OUTPUT_PATH = "method-test-mapping.json";
    private static final String TARGET_METHOD = "";

    /**
     * Main method to run the process with default constant values.
     */
    public static void main(String[] args) throws Exception {
        MethodCallMapper mapper = new MethodCallMapper();
        mapper.processCoverage(XML_REPORT_PATH, TARGET_METHOD);
    }

    /**
     * Parses a PIT line coverage XML report to map methods to the tests that cover them.
     *
     * @param xmlReportPath The file path to the PIT linecoverage.xml report.
     * @param targetMethod  The specific method to find tests for. If empty, processes all methods
     * and generates a JSON file.
     * @throws Exception if parsing or file writing fails.
     */
    public void processCoverage(String xmlReportPath, String targetMethod) throws Exception {
        processCoverage(xmlReportPath, targetMethod, "default-project");
    }

    /**
     * Parses a PIT line coverage XML report to map methods to the tests that cover them.
     *
     * @param xmlReportPath The file path to the PIT linecoverage.xml report.
     * @param targetMethod  The specific method to find tests for. If empty, processes all methods
     * and generates a JSON file.
     * @param projectName   The name of the project to include in the output filename.
     * @throws Exception if parsing or file writing fails.
     */
    public void processCoverage(String xmlReportPath, String targetMethod, String projectName) throws Exception {
        Map<String, Set<String>> rawMethodTestMap = parseCoverage(xmlReportPath);

        // Format keys and test values to be clean and ready-to-use
        Map<String, Set<String>> formattedMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : rawMethodTestMap.entrySet()) {
            String methodSig = entry.getKey();
            String simpleMethod = methodSig.contains("(") ? methodSig.substring(0, methodSig.indexOf("(")) : methodSig;
            Set<String> formattedTests = entry.getValue().stream()
                    .map(MethodCallMapper::formatTest)
                    .collect(Collectors.toSet());
            formattedMap.put(simpleMethod, formattedTests);
        }

        if (targetMethod == null || targetMethod.isEmpty()) {
            // If no specific method is targeted, process all and write to JSON with project name
            Set<String> allTests = formattedMap.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
            System.out.println("Total unique tests: " + allTests.size());

            String outputPath = projectName + "-method-test-mapping.json";
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(outputPath)) {
                gson.toJson(formattedMap, writer);
                System.out.println("JSON mapping written to " + outputPath);
            }

        } else {
            // If a specific method is targeted, find its tests and print a Maven command
            Set<String> tests = formattedMap.getOrDefault(targetMethod, new HashSet<>());
            if (tests.isEmpty()) {
                System.err.println("No tests found for method: " + targetMethod);
                return;
            }

            System.out.println("Tests for " + targetMethod + ":");
            tests.forEach(System.out::println);
            System.out.println();

            Map<String, Set<String>> classToMethods = new HashMap<>();
            for (String formatted : tests) {
                String[] parts = formatted.split("#");
                if (parts.length == 2) {
                    classToMethods.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts[1]);
                }
            }

            String testParam = classToMethods.entrySet().stream()
                    .map(e -> e.getKey() + "#" + String.join("+", e.getValue()))
                    .collect(Collectors.joining(","));

            System.out.println("Run only these tests with Maven (clean test):");
            System.out.println("mvn clean test -Dtest=\"" + testParam + "\"");
        }
    }

    private static String formatTest(String fullTest) {
        String className = extractBetween(fullTest, "/[class:", "]");
        String methodWithParams = extractBetween(fullTest, "/[method:", "]");
        String methodName = methodWithParams.replaceAll("\\(.*\\)", "");
        String simpleClass = className.substring(className.lastIndexOf('.') + 1);
        return simpleClass + "#" + methodName;
    }

    private static Map<String, Set<String>> parseCoverage(String xmlFile) throws Exception {
        Map<String, Set<String>> map = new HashMap<>();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();

        DefaultHandler handler = new DefaultHandler() {
            boolean isMethodElement = false;
            String currentMethod = "";
            Set<String> currentTests = null;

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if (qName.equals("block")) {
                    String methodSig = attributes.getValue("method");
                    if (methodSig != null && !methodSig.startsWith("<init>") && !methodSig.isEmpty()) {
                        currentMethod = attributes.getValue("classname") + "." + methodSig;
                        currentTests = new HashSet<>();
                    }
                } else if (qName.equals("test") && currentTests != null) {
                    String testName = attributes.getValue("name");
                    if (testName != null && !testName.isEmpty()) {
                        currentTests.add(testName);
                    }
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                if (qName.equals("block") && currentMethod != null && currentTests != null) {
                    map.put(currentMethod, currentTests);
                }
            }
        };

        saxParser.parse(Paths.get(xmlFile).toFile(), handler);
        return map;
    }

    private static String extractBetween(String s, String start, String end) {
        int i = s.indexOf(start);
        if (i < 0) return "";
        int j = s.indexOf(end, i + start.length());
        if (j < 0) return "";
        return s.substring(i + start.length(), j);
    }
}