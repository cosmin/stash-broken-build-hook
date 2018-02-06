package com.risingoak.stash.plugins.hook;

import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheck;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RefService;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequestImpl;

import javax.annotation.Nonnull;

import static java.lang.String.format;

public class MergeRequestBlockHook extends AbstractRejectHook implements RepositoryMergeRequestCheck {
    public MergeRequestBlockHook(RefService repositoryMetadataService, BuildStatusService buildStatusService, HistoryService historyService) {
        super(repositoryMetadataService, historyService, buildStatusService);
    }

    @Override
    public void check(@Nonnull RepositoryMergeRequestCheckContext repositoryMergeRequestCheckContext) {
        MergeRequest mergeRequest = repositoryMergeRequestCheckContext.getMergeRequest();
        Repository repository = mergeRequest.getPullRequest().getToRef().getRepository();
        String branchName = repositoryMetadataService.getDefaultBranch(repository).getDisplayId();

        Page<Changeset> changesets = historyService.getChangesets(repository, null, null, new PageRequestImpl(0, COMMITS_TO_INSPECT));

        BranchState defaultBranchState = getAggregatedStatus(changesets);
        switch (defaultBranchState.state) {
            case INPROGRESS:
                mergeRequest.veto("Too many pending builds", format("REJECTED: Too many pending builds on branch %s, wait a couple of minutes and try again.", branchName));
                return;
            case FAILED:
                mergeRequest.veto("Destination branch is failed", format("REJECTED: Branch %s has at least 1 failed build for commit %s", branchName, defaultBranchState.commit));
                return;
            case UNDEFINED:
                return;
            case SUCCESSFUL:
                return;
        }
    }
}
