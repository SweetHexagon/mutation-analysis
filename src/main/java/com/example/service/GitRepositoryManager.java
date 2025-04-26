package com.example.service;

import lombok.Getter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class GitRepositoryManager {

    private Repository currentRepository;


    @Getter
    private String currentRepoPath;

    public synchronized Repository loadRepository(String repoPath) {
        try {
            if (currentRepository != null) {
                closeRepository();
            }
            currentRepoPath = repoPath;
            currentRepository = Git.open(new File(repoPath)).getRepository();
            return currentRepository;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Git repository from " + repoPath, e);
        }
    }

    public Repository getCurrentRepository() {
        if (currentRepository == null) {
            throw new IllegalStateException("No repository is loaded.");
        }
        return currentRepository;
    }



    public void closeRepository() {
        try {
            if (currentRepository != null) currentRepository.close();
        } finally {
            currentRepository = null;
        }
    }
}

