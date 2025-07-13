package com.example.mutation_tester.mutation_metadata_processing;

import java.io.FileWriter;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

public class MethodCallMapper {

    private static final String XML_REPORT_PATH = "repositories_for_tests/jsoup/target/pit-reports/linecoverage.xml";
    private static final String JSON_OUTPUT_PATH = "method-test-mapping.json";
    private static final String TARGET_METHOD = "";

    public static void main(String[] args) throws Exception {
        Map<String, Set<String>> rawMethodTestMap = parseCoverage(XML_REPORT_PATH);

        // Format keys and test values to be clean and ready-to-use
        Map<String, Set<String>> formattedMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : rawMethodTestMap.entrySet()) {
            String methodSig = entry.getKey();
            String simpleMethod = methodSig.contains("(") ? methodSig.substring(0, methodSig.indexOf("(")) : methodSig;
            Set<String> formattedTests = entry.getValue().stream().map(MethodCallMapper::formatTest).collect(Collectors.toSet());
            formattedMap.put(simpleMethod, formattedTests);
        }

        if (TARGET_METHOD.isEmpty()) {
            /*formattedMap.forEach((method, tests) -> {
                System.out.println(method + " ->");
                tests.forEach(System.out::println);
                System.out.println();
            });*/

            Set<String> allTests = formattedMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
            System.out.println("Total unique tests: " + allTests.size());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(JSON_OUTPUT_PATH)) {
                gson.toJson(formattedMap, writer);
                System.out.println("JSON mapping written to " + JSON_OUTPUT_PATH);
            }

        } else {
            Set<String> tests = formattedMap.getOrDefault(TARGET_METHOD, new HashSet<>());
            if (tests.isEmpty()) {
                System.err.println("No tests found for method: " + TARGET_METHOD);
                return;
            }

            System.out.println("Tests for " + TARGET_METHOD + ":");
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
