package com.example.parser;

import java.util.HashMap;
import java.util.Map;

public class ParserFactory {

    private static final Map<String, LanguageParser> parserRegistry = new HashMap<>();

    static {
        parserRegistry.put("c", new CParserImpl());
        parserRegistry.put("java", new JavaParserImpl());
    }

    public static LanguageParser getParser(String filePath) {
        String extension = getFileExtension(filePath);
        return parserRegistry.get(extension);
    }

    private static String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }
}

