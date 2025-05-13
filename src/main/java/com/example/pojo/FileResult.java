package com.example.pojo;

import com.example.EditOperation;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter @Setter
@Builder
public class FileResult{
    String name;
    String oldCommit;
    String newCommit;
    Map<String, Integer> metrics;
    List<EditOperation> editOperations;

    @Override
    public String toString(){
        StringBuilder result = new StringBuilder();
        result.append("Name of file: ").append(name).append("\n");
        result.append("Old commit: ").append(oldCommit).append("\n");
        result.append("New commit: ").append(newCommit).append("\n");
        if (editOperations != null) {
            result.append("Edit operations:\n");
            for(EditOperation editOperation : editOperations){
                result.append("  Operation: ").append(editOperation.toString()).append("\n");
            }
        }

        //result.append("Metrics: ").append("\n");
        //for(var metricsKey : metrics.keySet()){
        //    result.append("  ").append(metricsKey.toString()).append(": ").append(metrics.get(metricsKey)).append("\n");
        //}

        return result.toString();
    }
}
