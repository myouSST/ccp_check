package org.example.git;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;

import lombok.AllArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

@AllArgsConstructor
public class GitCommitFetcher {
    private String remoteUrl;

    private String branchName;

    public void fetchAndExecute(Date since, String token, AfterAction afterAction) throws GitAPIException, IOException {
        URL url = new URL(remoteUrl);
        String path = url.getPath();

        // .git 이전의 부분에서 저장소 이름만 가져오기
        String repositoryName = path.substring(1, path.lastIndexOf(".git"));

        String generatedSourcesPath = "target/generated-sources";
        String localPath = generatedSourcesPath + "/" + repositoryName;

        try (Git git = Git.cloneRepository()
            .setDirectory(new File(localPath))
            .setURI(remoteUrl)
            .setBranch(branchName)
            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
            .call()) {

            Iterable<RevCommit> commits = git.log()
                .all()
                .call();

            List<RevCommit> sinceCommits = StreamSupport.stream(commits.spliterator(), false)
                .filter(commit -> commit.getAuthorIdent().getWhen().after(since))
                .toList();

            afterAction.execute(git, sinceCommits);
        }
    }

    public interface AfterAction {
        void execute(Git git, Iterable<RevCommit> commits) throws IOException, GitAPIException;
    }
}
