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

public class BrokenBuildHook implements PreReceiveRepositoryHook {
    public static final int COMMITS_TO_INSPECT = 10;
    private RepositoryMetadataService repositoryMetadataService;
    private BuildStatusService buildStatusService;
    private HistoryService historyService;

    public BrokenBuildHook(RepositoryMetadataService repositoryMetadataService, BuildStatusService buildStatusService, HistoryService historyService) {
        this.repositoryMetadataService = repositoryMetadataService;
        this.buildStatusService = buildStatusService;
        this.historyService = historyService;
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext repositoryHookContext, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        RefChange push = getPushToDefaultBranch(repositoryHookContext, refChanges);
        if (push != null) {
            Repository repository = repositoryHookContext.getRepository();
            String fromHash = push.getFromHash();
            String toHash = push.getToHash();

            // if for some reason we happen to have seen the status of the commit
            BuildStatus.State justPushedStatus = getAggregatedStatus(toHash);
            if (justPushedStatus == BuildStatus.State.SUCCESSFUL) {
                return true;
            } else if (justPushedStatus == BuildStatus.State.FAILED) {
                printPushingCommitWithFailedStatusMsg(hookResponse, toHash);
                return false;
            }

            Changeset mostRecentPushedCommit = historyService.getChangeset(repository, toHash);

            boolean hasPending = false;
            Page<Changeset> changesets = historyService.getChangesets(repository, fromHash, null, new PageRequestImpl(0, COMMITS_TO_INSPECT));
            for (Changeset changeset : changesets.getValues()) {
                BuildStatus.State mostRecentStatus = getAggregatedStatus(changeset.getId());
                if (mostRecentStatus == BuildStatus.State.SUCCESSFUL) {
                    return true;
                } else if (mostRecentStatus == BuildStatus.State.FAILED) {
                    if (mostRecentPushedCommit.getMessage().contains("fixes " + changeset.getDisplayId())) {
                        hookResponse.out().format("Build is broken at commit %s, but your push claims to fix it.\n", changeset.getDisplayId());
                        return true;
                    } else {
                        printBranchHasFailedBuildMsg(hookResponse, push, changeset.getDisplayId());
                        return false;
                    }
                } else if (mostRecentStatus == BuildStatus.State.INPROGRESS) {
                    hasPending = true;
                }
            }
            if (hasPending) {
                printTooManyPendingBuilds(hookResponse, push);
                return false;
            }

        }
        return true;
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

    private BuildStatus.State getAggregatedStatus(String theHash) {
        boolean hasPending = false;
        boolean hasSuccess = false;
        for (BuildStatus status : buildStatusService.findAll(theHash).getValues()) {
            if (BuildStatus.State.FAILED == status.getState()) {
                return BuildStatus.State.FAILED;
            } else if (status.getState() == BuildStatus.State.INPROGRESS) {
                hasPending = true;
            } else if (status.getState() == BuildStatus.State.SUCCESSFUL) {
                hasSuccess = true;
            }
        }
        if (hasSuccess && !hasPending) {
            return BuildStatus.State.SUCCESSFUL;
        } else if (hasPending) {
            return BuildStatus.State.INPROGRESS;
        } else {
            return null;
        }
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
