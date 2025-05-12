package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.UpdateOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.declaration.CtElement;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ExperimentalSwitchPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        // 1) Find the (new‐AST) switch under which these ops occurred
        Set switches = ops.stream()
                .map(Operation::getNode)
                .map(n -> n.getParent(CtSwitch.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (switches.size() != 1) {
            return false;
        }
        CtSwitch<?> sw = (CtSwitch<?>) switches.iterator().next();
        int totalCases = sw.getCases().size();

        // 2) Grab exactly those MoveOperations that moved a case‐label literal
        List<MoveOperation> labelMoves = ops.stream()
                .filter(op -> op instanceof MoveOperation)
                .map(op -> (MoveOperation) op)
                .filter(mv -> {
                    CtElement n = mv.getNode();
                    if (!(n instanceof CtLiteral<?> lit)) {
                        return false;
                    }
                    // was it the literal used *as* the case‐expression?
                    CtCase<?> parent = lit.getParent(CtCase.class);
                    return parent != null
                            && parent.getCaseExpression() == lit;
                })
                .collect(Collectors.toList());

        // 3) Grab all the body‐literal updates
        List<UpdateOperation> bodyUpdates = ops.stream()
                .filter(op -> op instanceof UpdateOperation)
                .map(op -> (UpdateOperation) op)
                .filter(u -> u.getNode() instanceof CtLiteral<?>)
                .collect(Collectors.toList());

        int M = labelMoves.size();
        int U = bodyUpdates.size();

        // 4a) single‐case form? one label‐move, no updates, exactly one op total
        if (M == 1 && U == 0 && ops.size() == 1) {
            return true;
        }

        // 4b) “full multi‐case” form?
        //    exactly one label‐move + one body‐update *per* remaining case
        if (M == 1
                && U > 0
                && (M + U == totalCases - 1)    // each non‐moved case got its print‐literal updated
        ) {
            // and all updates agree on the *same* new literal (the old default)
            Object newVal = ((CtLiteral<?>) bodyUpdates.get(0).getDstNode())
                    .getValue();
            boolean allSame = bodyUpdates.stream()
                    .map(u -> ((CtLiteral<?>) u.getDstNode()).getValue())
                    .allMatch(v -> Objects.equals(v, newVal));
            return allSame;
        }

        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_SWITCH’ – swaps the switch’s default label with the first non-default case label (also catches the single‐literal‐move form)";
    }
}
