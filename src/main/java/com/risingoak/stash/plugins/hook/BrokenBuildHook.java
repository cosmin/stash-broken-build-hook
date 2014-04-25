package com.risingoak.stash.plugins.hook;

import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.Branch;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryMetadataService;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequestImpl;

import javax.annotation.Nonnull;
import java.util.Collection;

public class BrokenBuildHook extends AbstractRejectHook implements PreReceiveRepositoryHook {
    public BrokenBuildHook(RepositoryMetadataService repositoryMetadataService, BuildStatusService buildStatusService, HistoryService historyService) {
        super(repositoryMetadataService, historyService, buildStatusService);
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext repositoryHookContext, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        RefChange push = getPushToDefaultBranch(repositoryHookContext, refChanges);
        if (push == null) {
            return true;
        }

        String toHash = push.getToHash();

        // if for some reason we happen to have seen the status of the commit
        BuildStatus.State justPushedStatus = getAggregatedStatus(toHash);
        if (justPushedStatus == BuildStatus.State.SUCCESSFUL) {
            return true;
        } else if (justPushedStatus == BuildStatus.State.FAILED) {
            printPushingCommitWithFailedStatusMsg(hookResponse, toHash);
            return false;
        }

        Repository repository = repositoryHookContext.getRepository();
        BranchState defaultBranchState = getAggregatedStatus(getChangesets(repository, push.getFromHash()));
        if (defaultBranchState == null) {
            return true;
        }

        switch (defaultBranchState.state) {
            case INPROGRESS:
                printTooManyPendingBuilds(hookResponse, push);
                return false;
            case FAILED:
                if (isFix(repository, toHash, defaultBranchState.commit)) {
                    hookResponse.out().format("Build is broken at commit %s, but your push claims to fix it.\n", defaultBranchState.commit);
                    return true;
                } else {
                    printBranchHasFailedBuildMsg(hookResponse, push, defaultBranchState.commit);
                    return false;
                }
            case SUCCESSFUL:
                return true;
            default:
                return true;
        }
    }

    private Page<Changeset> getChangesets(Repository repository, String fromHash) {
        return historyService.getChangesets(repository, fromHash, null, new PageRequestImpl(0, COMMITS_TO_INSPECT));
    }

    private boolean isFix(Repository repository, String head, String commit) {
        Changeset mostRecentPushedCommit = historyService.getChangeset(repository, head);
        return mostRecentPushedCommit.getMessage().contains("fixes " + commit);
    }

    private void printPushingCommitWithFailedStatusMsg(HookResponse hookResponse, String toHash) {
        hookResponse.err().println();
        hookResponse.err().format("REJECTED: You are pushing a commit <%s> that has at least 1 failed build.\n", toHash);
    }

    private void printTooManyPendingBuilds(HookResponse hookResponse, RefChange push) {
        hookResponse.err().println();
        hookResponse.err().format("REJECTED: Too many pending builds on branch %s, wait a couple of minutes and try again.", push.getRefId());
    }

    private void printBranchHasFailedBuildMsg(HookResponse hookResponse, RefChange push, String fromHash) {
        hookResponse.err().println();
        hookResponse.err().format("REJECTED: Branch %s has at least 1 failed build for commit %s\n", push.getRefId(), fromHash);
        hookResponse.err().println();
        hookResponse.err().println("If you are fixing the build, amend your commit to contain the following message: ");
        hookResponse.err().println();
        hookResponse.err().format("'fixes %s'\n", fromHash);
    }

    private RefChange getPushToDefaultBranch(RepositoryHookContext repositoryHookContext, Collection<RefChange> refChanges) {
        Branch defaultBranch = repositoryMetadataService.getDefaultBranch(repositoryHookContext.getRepository());
        for (RefChange refChange : refChanges) {
            if (refChange.getRefId().equals(defaultBranch.getId())) {
                return refChange;
            }
        }
        return null;
    }
}
