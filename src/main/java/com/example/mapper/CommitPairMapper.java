package com.example.mapper;

import com.example.CommitPairWithFiles;
import com.example.dto.CommitPairDTO;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CommitPairMapper {

    public static List<CommitPairDTO> toDTOs(List<CommitPairWithFiles> pairs) {
        List<CommitPairDTO> dtos = new ArrayList<>();
        for (CommitPairWithFiles p : pairs) {
            dtos.add(new CommitPairDTO(
                    p.oldCommit().getName(),
                    p.newCommit().getName(),
                    p.changedFiles()
            ));
        }
        return dtos;
    }

    public static List<CommitPairWithFiles> fromDTOs(Repository repo, List<CommitPairDTO> dtos) throws IOException {
        List<CommitPairWithFiles> pairs = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repo)) {
            for (CommitPairDTO dto : dtos) {
                RevCommit oldCommit = walk.parseCommit(repo.resolve(dto.oldCommitSha()));
                RevCommit newCommit = walk.parseCommit(repo.resolve(dto.newCommitSha()));
                pairs.add(new CommitPairWithFiles(oldCommit, newCommit, dto.changedFiles()));
            }
        }
        return pairs;
    }
}
