package com.risingoak.stash.plugins.hook;


import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.repository.RepositoryMetadataService;
import com.atlassian.stash.util.Page;

public class AbstractRejectHook {
    public static final int COMMITS_TO_INSPECT = 10;
    protected RepositoryMetadataService repositoryMetadataService;
    protected BuildStatusService buildStatusService;
    protected HistoryService historyService;

    public AbstractRejectHook(RepositoryMetadataService repositoryMetadataService, HistoryService historyService, BuildStatusService buildStatusService) {
        this.repositoryMetadataService = repositoryMetadataService;
        this.historyService = historyService;
        this.buildStatusService = buildStatusService;
    }

    protected BuildStatus.State getAggregatedStatus(String theHash) {
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

    protected BrokenBuildHook.BranchState getAggregatedStatus(Page<Changeset> changesets) {
        boolean hasPending = false;
        for (Changeset changeset : changesets.getValues()) {
            BuildStatus.State aggregatedStatus = getAggregatedStatus(changeset.getId());
            if (aggregatedStatus == null) {
                continue;
            }
            switch (aggregatedStatus) {
                case SUCCESSFUL:
                    return new BrokenBuildHook.BranchState(BuildStatus.State.SUCCESSFUL);
                case FAILED:
                    return new BrokenBuildHook.BranchState(BuildStatus.State.FAILED, changeset.getDisplayId());
                case INPROGRESS:
                    hasPending = true;
                    break;
            }
        }
        if (hasPending) {
            return new BrokenBuildHook.BranchState(BuildStatus.State.INPROGRESS);
        }
        return null;
    }

    protected static class BranchState {
        protected final BuildStatus.State state;
        protected final String commit;

        public BranchState(BuildStatus.State state) {
            this.state = state;
            this.commit = null;
        }

        public BranchState(BuildStatus.State state, String commit) {
            this.state = state;
            this.commit = commit;
        }
    }
}