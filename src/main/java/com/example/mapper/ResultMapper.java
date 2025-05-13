package com.example.mapper;

import com.example.EditOperation;
import com.example.pojo.FileResult;
import com.example.dto.EditOperationDto;
import com.example.dto.FileResultDto;
import com.example.dto.RepoResultDto;
import com.example.pojo.RepoResult;

import java.util.Map;
import java.util.stream.Collectors;

public class ResultMapper {

    public static EditOperationDto toDto(EditOperation op) {
        return EditOperationDto.builder()
                .type(op.type().name())
                .fromText(getTextSafe(op.srcNode()))
                .toText(getTextSafe(op.dstNode()))
                .method(op.method())
                .context(op.context())
                .build();
    }

    private static String getTextSafe(/* CtElement */ Object element) {
        if (element == null) {
            return "«missing code fragment»";
        }
        try {
            return element.toString().trim().replaceAll("\\s+", " ");
        } catch (Exception e) {
            return "«error rendering node»";
        }
    }

    public static FileResultDto toDto(FileResult fileResult) {
        return FileResultDto.builder()
                .name(fileResult.getName())
                .oldCommit(fileResult.getOldCommit())
                .newCommit(fileResult.getNewCommit())
                //.metrics(fileResult.getMetrics().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)))
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



