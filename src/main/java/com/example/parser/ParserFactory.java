package com.example.parser;

import java.util.HashMap;
import java.util.Map;

public class ParserFactory {

    private static final Map<String, Class<? extends LanguageParser>> parserRegistry = new HashMap<>();

    static {
        parserRegistry.put("c", CParserImpl.class);
        parserRegistry.put("java", JavaParserImpl.class);
    }

    public static LanguageParser getParser(String filePath) {
        String extension = getFileExtension(filePath);
        Class<? extends LanguageParser> parserClass = parserRegistry.get(extension);
        if (parserClass == null) return null;
        try {
            return parserClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot create parser for *." + extension, e);
        }
    }

    private static String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }
}

