package com.example.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class EditOperationDto {
    private String type;
    private String fromText;
    private String toText;
    private List<String> context;
}
