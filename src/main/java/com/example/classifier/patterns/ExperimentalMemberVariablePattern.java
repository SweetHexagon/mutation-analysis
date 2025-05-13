package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class ExperimentalMemberVariablePattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                // must be an assignment statement
                if (node instanceof CtAssignment<?, ?> assign) {
                    CtExpression<?> lhs = assign.getAssigned();
                    // the left‐hand side must be a member‐variable write
                    if (lhs instanceof CtVariableAccess<?> varAcc
                            && varAcc.getVariable().getDeclaration() instanceof CtField<?>) {
                        matched.add(del);
                    }
                }
            }
        }
        return matched;
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_MEMBER_VARIABLE’ – removed assignment to a member variable (now initialized to its Java default)";
    }
}
