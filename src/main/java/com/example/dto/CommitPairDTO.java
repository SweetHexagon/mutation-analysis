package com.example.dto;

import java.util.List;

public record CommitPairDTO(String oldCommitSha, String newCommitSha, List<String> changedFiles) {}

