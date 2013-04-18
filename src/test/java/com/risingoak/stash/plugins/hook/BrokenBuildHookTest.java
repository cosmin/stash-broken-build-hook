package com.risingoak.stash.plugins.hook;

import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.internal.build.InternalBuildStatus;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;


public class BrokenBuildHookTest {

    public static final String DEFAULT_BRANCH_REF = "refs/heads/master";
    private final RepositoryMetadataService repositoryMetadataService = mock(RepositoryMetadataService.class);
    private final BuildStatusService buildStatusService = mock(BuildStatusService.class);
    private final HistoryService historyService = mock(HistoryService.class);
    private final BrokenBuildHook brokenBuildHook = new BrokenBuildHook(repositoryMetadataService, buildStatusService, historyService);
    private final RepositoryHookContext repositoryHookContext = mock(RepositoryHookContext.class);
    private final HookResponse hookResponse = mock(HookResponse.class);
    private final Branch branch = mock(Branch.class);
    private final Repository repository = mock(Repository.class);

    @Before
    public void setUp() {
        when(repositoryHookContext.getRepository()).thenReturn(repository);
        when(repositoryMetadataService.getDefaultBranch(isA(Repository.class))).thenReturn(branch);
        when(branch.getId()).thenReturn(DEFAULT_BRANCH_REF);
        when(hookResponse.err()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    public void shouldIgnorePushesThatDoNotAffectTheDefaultBranch() {
        SimpleRefChange refChange = getRefChangeFor("refs/heads/foobarbaz");
        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push to non-default branch", response);
        verifyZeroInteractions(historyService, buildStatusService);
    }

    @Test
    public void shouldAllowPushIfCommitBeingPushedHasSuccessfulBuild() {
        SimpleRefChange refChange = getRefChangeFor(DEFAULT_BRANCH_REF);
        Page page = mockBuildStatusList(BuildStatus.State.SUCCESSFUL);
        when(buildStatusService.findAll(refChange.getToHash())).thenReturn(page);
        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push that passes build", response);
        verify(buildStatusService).findAll(refChange.getToHash());
        verifyZeroInteractions(historyService);
    }

    @Test
    public void shouldAllowPushIfMostRecentBuildIsSuccessful() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildStatus.State.SUCCESSFUL);

        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push", response);
        verify(buildStatusService).findAll(refChange.getToHash());
        verify(buildStatusService).findAll(refChange.getFromHash());
    }

    @Test
    public void shouldRejectPushIfMostRecentBuildFailed() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildStatus.State.FAILED);

        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertFalse("hook incorrectly allowed push", response);
        verify(buildStatusService).findAll(refChange.getToHash());
        verify(buildStatusService).findAll(refChange.getFromHash());
    }

    @Test
    public void shouldAllowPushIfMostRecentNonPendingBuildIsSuccessful() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildStatus.State.INPROGRESS, BuildStatus.State.INPROGRESS, BuildStatus.State.SUCCESSFUL);

        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push", response);
    }

    @Test
    public void shouldRejectPushIfMostRecentNonPendingBuildFailed() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildStatus.State.INPROGRESS, BuildStatus.State.INPROGRESS, BuildStatus.State.FAILED);

        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertFalse("hook incorrectly allowed push", response);
    }

    @Test
    public void shouldRejectPushIfAllRecentBuildsArePending() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildStatus.State.INPROGRESS, BuildStatus.State.INPROGRESS, BuildStatus.State.INPROGRESS);

        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertFalse("hook incorrectly allowed push", response);
    }

    @Test
    public void shouldAllowPushIfNoBuildInformationIsPresent() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(null, null);

        boolean response = brokenBuildHook.onReceive(repositoryHookContext, Arrays.<RefChange>asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push", response);
    }

    private SimpleRefChange mockSimpleRefChangeWithPriorBuildStates(BuildStatus.State... states) {
        SimpleRefChange refChange = getRefChangeFor(DEFAULT_BRANCH_REF);


        Page emptyPage = emptyBuildStatusList();
        when(buildStatusService.findAll(refChange.getToHash())).thenReturn(emptyPage);

        setBuildStateForHash(refChange.getFromHash(), states[0]);

        List<Changeset> changesets = new ArrayList<Changeset>();
        Changeset changeset = mockChangeset(refChange.getFromHash());
        changesets.add(changeset);

        if (states.length > 1) {
            for (int idx = 1; idx < states.length; idx++) {
                BuildStatus.State state = states[idx];
                String hash = "hash-" + idx;
                changesets.add(mockChangeset(hash));
                setBuildStateForHash(hash, state);
            }
        }


        Page changesetsPage = mock(Page.class);
        when(changesetsPage.getValues()).thenReturn(changesets);
        when(historyService.getChangesets(eq(repository), eq(refChange.getFromHash()), anyString(), isA(PageRequest.class))).thenReturn(changesetsPage);

        return refChange;
    }

    private void setBuildStateForHash(String hash, BuildStatus.State state) {
        Page page = mockBuildStatusList(state);
        when(buildStatusService.findAll(hash)).thenReturn(page);
    }

    private Changeset mockChangeset(String fromHash) {
        Changeset changeset = mock(Changeset.class);
        when(changeset.getId()).thenReturn(fromHash);
        return changeset;
    }

    private Page<BuildStatus> emptyBuildStatusList() {
        Page<BuildStatus> page = mock(Page.class);
        List<BuildStatus> buildStatuses = new ArrayList<BuildStatus>();
        when(page.getValues()).thenReturn(buildStatuses);
        return page;
    }

    private Page<BuildStatus> mockBuildStatusList(BuildStatus.State... states) {
        Page<BuildStatus> page = mock(Page.class);
        List<BuildStatus> buildStatuses = new ArrayList<BuildStatus>();
        int idx = 0;
        for(BuildStatus.State state : states) {
            if (state != null) {
                buildStatuses.add(new InternalBuildStatus(state, "key-" + String.valueOf(idx++), null, "http://example.com", null, new Date()));
            }
        }
        when(page.getValues()).thenReturn(buildStatuses);
        return page;
    }

    private SimpleRefChange getRefChangeFor(String ref) {
        return new SimpleRefChange.Builder().refId(ref).fromHash("fromhash").toHash("tohash").type(RefChangeType.UPDATE).build();
    }
}
