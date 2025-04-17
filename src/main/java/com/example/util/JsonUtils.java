package com.example.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class JsonUtils {

    public static void writeJsonToFile(Object data, String filePath) {
        File file = new File(filePath);

        if (file.exists()) {
            if (!file.delete()) {
                System.out.println("Failed to delete existing file: " + filePath);
                return;
            }
        }

        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create();
            gson.toJson(data, writer);
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }
}

