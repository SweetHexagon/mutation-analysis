package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class VoidMethodCallRemovalPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                if (node instanceof CtInvocation<?> invocation) {
                    CtTypeReference<?> returnType = invocation.getType();
                    if (returnType != null && "void".equals(returnType.getSimpleName())) {
                        matched.add(del);
                    }
                }
            }
        }
        return matched;
    }

    @Override
    public String description() {
        return "Mutation ‘VOID_METHOD_CALLS’ – removes calls to methods with void return type";
    }
}
