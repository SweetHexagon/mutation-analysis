package com.example.dto;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class RepoResult {
    String name;
    String secondCommit;
    String firstCommit;
    List<FileResult> filesResults;

    @Override
    public String toString(){
        StringBuilder result = new StringBuilder();
        result.append("Name: ").append(name).append("\n");
        result.append("Second commit: ").append(secondCommit).append("\n");
        result.append("First commit: ").append(firstCommit).append("\n");
        for (FileResult fileResult : filesResults) {
            result.append(fileResult.toString()).append("\n");
        }
        return result.toString();
    }
}

