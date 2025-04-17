package com.example.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class FileResultDto {
    private String name;
    private String oldCommit;
    private String newCommit;
    private Map<String, Integer> metrics;
    private List<EditOperationDto> editOperations;
}

