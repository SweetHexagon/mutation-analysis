package com.example.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class RepoResultDto {
    private String repoUrl;
    private List<FileResultDto> fileResults;
}
