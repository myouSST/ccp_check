package org.example.git.process;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class CCPChecker {

    public static void process(Git git, Iterable<RevCommit> commits) throws IOException, GitAPIException {
        HashMap<String, Set<String>> groupedPackages = new HashMap<>();

        for (RevCommit commit : commits) {
            processDiffs(git, commit, groupedPackages);
        }

        for (String jiraTask : groupedPackages.keySet()) {
            Set<String> packages = groupedPackages.get(jiraTask);
            String prevComponentName = "";

            for (String packageInfo : packages) {
                String componentName = getComponentName(packageInfo);
                if (!prevComponentName.equals(componentName)) {
                    System.out.println("Jira task: " + jiraTask + " - CCP component: " + componentName);
                }
                prevComponentName = componentName;
            }
        }
    }

    private static void processDiffs(Git git, RevCommit commit, HashMap<String, Set<String>> groupedPackages) throws IOException, GitAPIException {
        List<DiffEntry> diffs = getDiffs(git, commit);
        for (DiffEntry entry : diffs) {
            if (entry.getNewPath().endsWith(".java")) {
                String jiraKey = extractJiraKey(commit.getFullMessage());
                if (jiraKey == null) {
                    continue;
                }

                String packageInfo = extractPackageInfo(git.getRepository(), commit, entry.getNewPath());
                groupedPackages.computeIfAbsent(jiraKey, k -> new HashSet<>()).add(packageInfo);
            }
        }
    }

    private static List<DiffEntry> getDiffs(Git git, RevCommit commit) throws IOException, GitAPIException {
        return git.diff()
            .setOldTree(prepareTreeParser(git.getRepository(), commit.getParent(0)))
            .setNewTree(prepareTreeParser(git.getRepository(), commit))
            .call();
    }

    private static CanonicalTreeParser prepareTreeParser(Repository repository, RevCommit commit) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();
            return treeParser;
        }
    }

    private static String extractPackageInfo(Repository repository, RevCommit commit, String filePath) throws IOException {
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(prepareTreeParser(repository, commit));
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));

            if (!treeWalk.next()) {
                throw new IllegalStateException("No file found in commit: " + commit.getName());
            }

            byte[] bytes = repository.open(treeWalk.getObjectId(0)).getBytes();
            String fileContent = new String(bytes);

            String regex = "package\\s+([\\w.]+);";
            return extractRegex(fileContent, regex);
        }
    }

    private static String extractRegex(String content, String regex) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "N/A";
        }
    }

    private static String extractJiraKey(String input) {
        // Jira 이슈 키를 추출하는 정규 표현식
        String regex = "(?i)\\b([A-Z]+-\\d+)\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        // 정규 표현식과 일치하는 첫 번째 그룹 추출
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    private static String getComponentName(String packageInfo) {
        String[] packageParts = packageInfo.split("\\.");
        return packageParts[3];
    }
}
