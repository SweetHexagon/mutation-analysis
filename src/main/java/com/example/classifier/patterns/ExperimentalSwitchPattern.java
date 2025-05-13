package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ExperimentalSwitchPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();

        // 1) Identify the single switch context
        Set switches = ops.stream()
                .map(op -> op.getNode().getParent(CtSwitch.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (switches.size() != 1) {
            return List.of();
        }
        CtSwitch<?> sw = (CtSwitch<?>) switches.iterator().next();
        int totalCases = sw.getCases().size();

        // 2) Collect the literal‐label moves
        List<MoveOperation> labelMoves = ops.stream()
                .filter(op -> op instanceof MoveOperation)
                .map(MoveOperation.class::cast)
                .filter(mv -> {
                    CtElement n = mv.getNode();
                    if (!(n instanceof CtLiteral<?> lit)) {
                        return false;
                    }
                    CtCase<?> parent = lit.getParent(CtCase.class);
                    return parent != null && parent.getCaseExpression() == lit;
                })
                .collect(Collectors.toList());

        // 3) Collect the body‐literal updates
        List<UpdateOperation> bodyUpdates = ops.stream()
                .filter(op -> op instanceof UpdateOperation)
                .map(UpdateOperation.class::cast)
                .filter(u -> u.getNode() instanceof CtLiteral<?>)
                .collect(Collectors.toList());

        int M = labelMoves.size();
        int U = bodyUpdates.size();

        // 4a) single‐literal‐move form
        if (M == 1 && U == 0 && ops.size() == 1) {
            matched.add(labelMoves.get(0));
            return matched;
        }

        // 4b) full multi‐case form
        if (M == 1 && U > 0 && (M + U == totalCases - 1)) {
            Object newVal = ((CtLiteral<?>) bodyUpdates.get(0).getDstNode()).getValue();
            boolean allSame = bodyUpdates.stream()
                    .map(u -> ((CtLiteral<?>) u.getDstNode()).getValue())
                    .allMatch(v -> Objects.equals(v, newVal));
            if (allSame) {
                matched.addAll(labelMoves);
                matched.addAll(bodyUpdates);
                return matched;
            }
        }

        return List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_SWITCH’ – swaps the switch’s default label with the first non-default case label (also catches the single‐literal‐move form)";
    }
}
