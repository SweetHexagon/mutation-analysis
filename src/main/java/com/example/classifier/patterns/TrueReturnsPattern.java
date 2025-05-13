package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class TrueReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();

        // Case A: UpdateOperation flipping false → true within a return
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (isFalseLiteral(src) && isTrueLiteral(dst) && isInReturnContext(src, dst)) {
                    matched.add(upd);
                }
            }
        }
        if (!matched.isEmpty()) {
            return matched;
        }

        // Case B: DeleteOperation of false literal + InsertOperation of true literal in return
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del && isFalseLiteral(del.getNode()) && isInReturnContext(del.getNode())) {
                for (Operation other : ops) {
                    if (other instanceof InsertOperation ins
                            && isTrueLiteral(ins.getNode())
                            && isInReturnContext(del.getNode(), ins.getNode())) {
                        return List.of(del, ins);
                    }
                }
            }
        }

        return List.of();
    }

    private boolean isFalseLiteral(CtElement element) {
        return element instanceof CtLiteral<?> lit
                && Boolean.FALSE.equals(lit.getValue());
    }

    private boolean isTrueLiteral(CtElement element) {
        return element instanceof CtLiteral<?> lit
                && Boolean.TRUE.equals(lit.getValue());
    }

    private boolean isInReturnContext(CtElement... elements) {
        for (CtElement e : elements) {
            if (!(e.getParent() instanceof CtReturn<?>)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String description() {
        return "Mutation ‘TRUE_RETURNS’ – replaces boolean return values with true";
    }
}
