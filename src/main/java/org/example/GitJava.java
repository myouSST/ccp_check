package org.example;

import java.io.IOException;
import java.util.Date;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.example.git.GitCommitFetcher;
import org.example.git.process.CCPChecker;

public class GitJava {

    public static void main(String[] args) throws IOException, GitAPIException {
        String remoteUrl = "https://github.com/myouSST/pilot-test.git";
        String branchName = "main";
        String token = "";

        // Get commits within the past 24 hours
        Date since = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);

        GitCommitFetcher gitCommitFetcher = new GitCommitFetcher(remoteUrl, branchName);
        gitCommitFetcher.fetchAndExecute(since, token, CCPChecker::process);
    }
}
