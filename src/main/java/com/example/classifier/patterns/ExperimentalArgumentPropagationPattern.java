package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class ExperimentalArgumentPropagationPattern
        implements ChangeClassifier.MutationPattern {


    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();

        // 1) collect the delete of the invocation
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del
                    && del.getNode() instanceof CtInvocation<?>) {
                matched.add(op);
            }
        }
        if (matched.isEmpty()) {
            return List.of();
        }

        // 2) find the inserted or moved parameter read that matches
        for (Operation op : ops) {
            CtVariableRead<?> varRead = extractVarRead(op);
            if (varRead == null) {
                continue;
            }
            if (!(varRead.getVariable().getDeclaration() instanceof CtParameter<?>)) {
                continue;
            }
            CtExecutable<?> parentExec = varRead.getParent(CtExecutable.class);
            if (parentExec == null) {
                continue;
            }
            CtTypeReference<?> returnType = parentExec.getType();
            CtTypeReference<?> paramType  = varRead.getType();
            if (returnType != null && returnType.equals(paramType)) {
                matched.add(op);
                break;
            }
        }

        // only return if we have both a delete of the call and a var‐read op
        return (matched.size() >= 2) ? matched : List.of();
    }

    private CtVariableRead<?> extractVarRead(Operation op) {
        CtElement node = null;
        if (op instanceof InsertOperation ins) {
            node = ins.getNode();
        } else if (op instanceof MoveOperation mv) {
            node = mv.getNode();
        }
        return (node instanceof CtVariableRead<?> vr)
                ? vr
                : null;
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_ARGUMENT_PROPAGATION’ – replaces a method call with one of its parameters of matching type";
    }
}
