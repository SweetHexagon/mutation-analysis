package com.example.classifier;


import com.example.classifier.patterns.*;
import gumtree.spoon.diff.operations.Operation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;


@Service
public class ChangeClassifier {

    public interface MutationPattern {

        boolean matches(List<Operation> ops);

        String description();
    }

    private static final List<MutationPattern> PATTERNS = List.of(
            new ABSPattern(),
            new AODPattern(),
            new AORPattern(),
            new ConditionalBoundaryPattern(),
            new ConstructorCallsPattern(),
            new CRCRPattern(),
            new EmptyReturnsPattern(),
            new ExperimentalArgumentPropagationPattern(),
            new ExperimentalBigIntegerPattern(),
            new ExperimentalMemberVariablePattern(),
            new ExperimentalNakedReceiverPattern(),
            new ExperimentalSwitchPattern(),
            new FalseReturnsPattern(),
            new IncrementsPattern()
    );


    public static List<String> classify(List<Operation> ops) {
        return PATTERNS.stream()
                .filter(p -> p.matches(ops))
                .map(MutationPattern::description)
                .collect(Collectors.toList());
    }
}

