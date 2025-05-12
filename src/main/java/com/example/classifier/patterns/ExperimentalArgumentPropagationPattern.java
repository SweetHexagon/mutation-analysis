package com.example.classifier.patterns;
import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;

import java.util.List;

public class ExperimentalArgumentPropagationPattern
        implements ChangeClassifier.MutationPattern {

    private boolean matchesArgumentPropagation(List<Operation> ops) {
        // 1) did we delete the original call?
        boolean sawDeleteCall = ops.stream().anyMatch(op ->
                op instanceof DeleteOperation del &&
                        del.getNode() instanceof spoon.reflect.code.CtInvocation<?>);

        if (!sawDeleteCall) {
            return false;
        }

        // 2) did we insert or move a parameter‐read of the correct type?
        return ops.stream().anyMatch(op -> {
            CtVariableRead<?> varRead = null;

            if (op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtVariableRead<?> ir) {
                varRead = ir;
            }
            else if (op instanceof MoveOperation mv
                    && mv.getNode() instanceof CtVariableRead<?> mr) {
                varRead = mr;
            }

            if (varRead == null) return false;

            // ensure it's a method‐parameter, not a local var
            if (!(varRead.getVariable().getDeclaration() instanceof CtParameter<?>)) {
                return false;
            }

            // 3) check that the parameter's type == enclosing method's return type
            CtExecutable<?> parentExec = varRead.getParent(CtExecutable.class);
            if (parentExec == null) {
                return false;
            }
            CtTypeReference<?> returnType = parentExec.getType();
            CtTypeReference<?> paramType  = varRead.getType();

            return returnType != null
                    && returnType.equals(paramType);
        });
    }

    @Override
    public boolean matches(List<Operation> ops) {
        return matchesArgumentPropagation(ops);
    }


    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_ARGUMENT_PROPAGATION’ – replaces a method call with one of its parameters of matching type";
    }
}
