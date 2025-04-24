package com.example.mapper;

import com.example.EditOperation;
import com.example.TreeNode;
import com.example.dto.EditOperationDto;
import com.example.dto.FileResultDto;
import com.example.dto.RepoResultDto;
import com.example.pojo.FileResult;
import com.example.pojo.RepoResult;

import java.util.Map;
import java.util.stream.Collectors;

public class ResultMapper {

    public static EditOperationDto toDto(EditOperation op) {
        return EditOperationDto.builder()
                .type(op.type().name())
                .fromText(getTextSafe(op.fromNode()))
                .toText(getTextSafe(op.toNode()))
                .context(op.context())
                .build();
    }

    private static String getTextSafe(TreeNode node) {
        if (node == null) {
            return "null";
        }
        if (node.getAstNode() != null) {
            try {
                return node.getAstNode().toString().trim();
            } catch (Exception e) {
                return node.getLabel();
            }
        }
        return node.getLabel();
    }

    public static FileResultDto toDto(FileResult fileResult) {
        return FileResultDto.builder()
                .name(fileResult.getName())
                .oldCommit(fileResult.getOldCommit())
                .newCommit(fileResult.getNewCommit())
                .metrics(fileResult.getMetrics().entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().name(),
                                Map.Entry::getValue
                        )))
                .editOperations(fileResult.getEditOperations().stream()
                        .map(ResultMapper::toDto)
                        .collect(Collectors.toList()))
                .build();
    }

    public static RepoResultDto toDto(RepoResult repoResult) {
        return RepoResultDto.builder()
                .repoUrl(repoResult.repoUlr)
                .fileResults(repoResult.filesResults.stream()
                        .map(ResultMapper::toDto)
                        .collect(Collectors.toList()))
                .build();
    }
}