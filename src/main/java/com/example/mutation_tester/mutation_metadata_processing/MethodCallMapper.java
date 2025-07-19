package com.example.mutation_tester.mutation_metadata_processing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileWriter;
import java.io.StringReader;
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
            // If no specific method is targeted, process all and write to JSON
            Set<String> allTests = formattedMap.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
            System.out.println("Total unique tests: " + allTests.size());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(JSON_OUTPUT_PATH)) {
                gson.toJson(formattedMap, writer);
                System.out.println("JSON mapping written to " + JSON_OUTPUT_PATH);
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
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

        Document doc = db.parse(Paths.get(xmlFile).toFile());
        NodeList blocks = doc.getElementsByTagName("block");

        for (int i = 0; i < blocks.getLength(); i++) {
            Element block = (Element) blocks.item(i);
            String methodSig = block.getAttribute("method");
            if (methodSig.startsWith("<init>") || methodSig.isEmpty()) continue;

            String classAndMethod = block.getAttribute("classname") + "." + methodSig;
            NodeList tests = block.getElementsByTagName("test");
            if (tests.getLength() == 0) continue;

            Set<String> set = map.computeIfAbsent(classAndMethod, k -> new HashSet<>());
            for (int j = 0; j < tests.getLength(); j++) {
                String testName = ((Element) tests.item(j)).getAttribute("name");
                if (testName != null && !testName.isEmpty()) {
                    set.add(testName);
                }
            }
        }
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