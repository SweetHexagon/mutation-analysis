package com.example.pojo;

import com.example.classifier.MutationKind;
import gumtree.spoon.diff.operations.Operation;
import java.util.EnumSet;

public class ClassifiedOperation {
    private final Operation op;
    private final EnumSet<MutationKind> kinds = EnumSet.noneOf(MutationKind.class);

    public ClassifiedOperation(Operation op) {
        this.op = op;
    }

    public Operation getOperation() {
        return op;
    }

    public void addKind(MutationKind kind) {
        kinds.add(kind);
    }

    public boolean hasKind(MutationKind kind) {
        return kinds.contains(kind);
    }

    public EnumSet<MutationKind> getKinds() {
        return EnumSet.copyOf(kinds);
    }
}
