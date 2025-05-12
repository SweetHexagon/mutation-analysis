package com.example.classifier.patterns;
import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class ExperimentalMemberVariablePattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        // Look for any deletion of a field‐assignment
        return ops.stream().anyMatch(this::isDeletedFieldAssignment);
    }

    private boolean isDeletedFieldAssignment(Operation op) {
        if (!(op instanceof DeleteOperation del)) {
            return false;
        }
        CtElement node = del.getNode();
        // must be an assignment statement
        if (!(node instanceof CtAssignment<?, ?> assign)) {
            return false;
        }
        // the left‐hand side must be a member‐variable write
        CtExpression<?> lhs = assign.getAssigned();
        if (lhs instanceof CtVariableAccess<?> varAcc) {
            // confirm it's a field (not a local or parameter)
            if (varAcc.getVariable().getDeclaration() instanceof CtField<?>) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_MEMBER_VARIABLE’ – removed assignment to a member variable (now initialized to its Java default)";
    }
}

