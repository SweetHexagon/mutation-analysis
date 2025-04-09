package com.example;

import org.eclipse.jgit.revwalk.RevCommit;
import java.util.List;

public record CommitPairWithFiles(RevCommit oldCommit, RevCommit newCommit, List<String> changedFiles) {

}
