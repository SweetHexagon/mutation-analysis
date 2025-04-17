package com.example.pojo;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class RepoResult {
    public String repoUlr;
    public List<FileResult> filesResults;

    @Override
    public String toString(){
        StringBuilder result = new StringBuilder();
        result.append("Repo url: ").append(repoUlr).append("\n");
        for (FileResult fileResult : filesResults) {
            result.append(fileResult.toString()).append("\n");
        }
        return result.toString();
    }
}

