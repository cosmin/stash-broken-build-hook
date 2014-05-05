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

    protected BuildState getAggregatedStatus(String theHash) {
        boolean hasPending = false;
        boolean hasSuccess = false;
        for (BuildStatus status : buildStatusService.findAll(theHash).getValues()) {
            if (BuildStatus.State.FAILED == status.getState()) {
                return BuildState.FAILED;
            } else if (status.getState() == BuildStatus.State.INPROGRESS) {
                hasPending = true;
            } else if (status.getState() == BuildStatus.State.SUCCESSFUL) {
                hasSuccess = true;
            }
        }

        if (hasPending) {
            return BuildState.INPROGRESS;
        }
        if (hasSuccess) {
            return BuildState.SUCCESSFUL;
        }
        return BuildState.UNDEFINED;
    }

    protected BranchState getAggregatedStatus(Page<Changeset> changesets) {
        boolean hasPending = false;
        for (Changeset changeset : changesets.getValues()) {
            BuildState aggregatedStatus = getAggregatedStatus(changeset.getId());
            switch (aggregatedStatus) {
                case UNDEFINED:
                    continue;
                case SUCCESSFUL:
                    return new BranchState(BuildState.SUCCESSFUL);
                case FAILED:
                    return new BranchState(BuildState.FAILED, changeset.getDisplayId());
                case INPROGRESS:
                    hasPending = true;
                    break;
            }
        }
        if (hasPending) {
            return new BranchState(BuildState.INPROGRESS);
        }
        return new BranchState(BuildState.UNDEFINED);
    }

    protected static class BranchState {
        protected final BuildState state;
        protected final String commit;

        public BranchState(BuildState state) {
            this.state = state;
            this.commit = null;
        }

        public BranchState(BuildState state, String commit) {
            this.state = state;
            this.commit = commit;
        }
    }

    public static enum BuildState {UNDEFINED, SUCCESSFUL, FAILED, INPROGRESS}
}