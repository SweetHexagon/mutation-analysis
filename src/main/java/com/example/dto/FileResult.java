package com.example.dto;

import com.example.EditOperation;
import com.example.Metrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

@Getter @Setter
@Builder
public class FileResult{
    String name;
    HashMap<Metrics, Integer> metrics;
    List<EditOperation> editOperations;
    String changedTreeFragment;
    String originalTreeFragment;

    @Override
    public String toString(){
        StringBuilder result = new StringBuilder();
        result.append("Name of file: ").append(name).append("\n");
        result.append("Changed tree: ").append(changedTreeFragment).append("\n");
        result.append("Original tree: ").append(originalTreeFragment).append("\n");

        if (editOperations != null) {
            result.append("Operations: ").append("\n");
            for(EditOperation editOperation : editOperations){
                result.append("  ").append(editOperation.toString()).append("\n");
            }
        }

        result.append("Metrics: ").append("\n");
        for(Metrics metricsKey : metrics.keySet()){
            result.append("  ").append(metricsKey.toString()).append(": ").append(metrics.get(metricsKey)).append("\n");
        }

        return result.toString();
    }
}
