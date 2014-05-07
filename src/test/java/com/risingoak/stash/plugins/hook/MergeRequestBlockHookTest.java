package com.risingoak.stash.plugins.hook;

import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.Branch;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryMetadataService;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class MergeRequestBlockHookTest {

    public static final String DEFAULT_BRANCH_REF = "refs/heads/master";

    @Mock
    private RepositoryMetadataService repositoryMetadataService;
    @Mock
    private BuildStatusService buildStatusService;
    @Mock
    private HistoryService historyService;

    @Mock
    private RepositoryMergeRequestCheckContext repositoryHookContext;
    @Mock
    private Branch branch;
    @Mock
    private Repository repository;
    @Mock
    private MergeRequest mergeRequest;
    @Mock
    private PullRequest pullRequest;
    @Mock
    private PullRequestRef toRef;

    @Before
    public void setUp() {
        when(repositoryHookContext.getMergeRequest()).thenReturn(mergeRequest);
        when(mergeRequest.getPullRequest()).thenReturn(pullRequest);
        when(pullRequest.getToRef()).thenReturn(toRef);
        when(toRef.getRepository()).thenReturn(repository);
        when(repositoryMetadataService.getDefaultBranch(isA(Repository.class))).thenReturn(branch);
        when(branch.getId()).thenReturn(DEFAULT_BRANCH_REF);
        Page<Changeset> changesetsPage = mock(Page.class);
        when(changesetsPage.getValues()).thenReturn(new ArrayList<Changeset>());
        when(historyService.getChangesets(eq(repository), anyString(), anyString(), isA(PageRequest.class))).thenReturn(changesetsPage);
    }

    @Test
    public void allowMerge_mostRecentBuildIsSuccessful() {
        MergeRequestBlockHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState.SUCCESSFUL, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void allowMerge_mostRecentBuildIsUndefined() {
        MergeRequestBlockHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState.UNDEFINED, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void rejectMerge_mostRecentBuildIsInProgress() {
        MergeRequestBlockHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState.INPROGRESS, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest).veto(anyString(), anyString());
    }

    @Test
    public void rejectMerge_mostRecentBuildIsFailed() {
        MergeRequestBlockHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState.FAILED, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest).veto(anyString(), anyString());
    }

    private MergeRequestBlockHook mockBuildHook(final AbstractRejectHook.BranchState branchState) {
        return new MergeRequestBlockHook(repositoryMetadataService, buildStatusService, historyService) {
            @Override
            protected BranchState getAggregatedStatus(Page<Changeset> changesets) {
                return branchState;
            }
        };
    }
}