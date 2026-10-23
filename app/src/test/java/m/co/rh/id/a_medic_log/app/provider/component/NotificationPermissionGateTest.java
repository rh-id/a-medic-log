package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.component.NotificationPermissionGate.Decision;

/**
 * Pure JVM tests for {@link NotificationPermissionGate#decide(int, boolean,
 * boolean, boolean)}, {@link NotificationPermissionGate#shouldAskOnLaunch(int,
 * boolean, boolean, boolean)} and {@link NotificationPermissionGate#shouldShowDialogNow(boolean,
 * boolean, boolean, String, boolean)}, no Android runtime involved (the
 * decisions must not depend on the device, only on their inputs).
 */
public class NotificationPermissionGateTest {

    private static final int SDK_M = 23;
    private static final int SDK_S = 31;
    private static final int SDK_T = 33; // Tiramisu, POST_NOTIFICATIONS appears here
    private static final int SDK_U = 34;

    @Test
    public void belowTiramisu_nothingToDo_evenWhenDenied() {
        // the permission does not exist below API 33, notifications are
        // granted at install time whatever the other inputs say
        assertEquals(Decision.NONE,
                NotificationPermissionGate.decide(SDK_M, false, false, false));
        assertEquals(Decision.NONE,
                NotificationPermissionGate.decide(SDK_S, false, true, true));
        assertEquals(Decision.NONE,
                NotificationPermissionGate.decide(SDK_T - 1, false, false, false));
    }

    @Test
    public void granted_nothingToDo_onEveryLevel() {
        assertEquals(Decision.NONE,
                NotificationPermissionGate.decide(SDK_T, true, false, false));
        assertEquals(Decision.NONE,
                NotificationPermissionGate.decide(SDK_T, true, true, false));
        assertEquals(Decision.NONE,
                NotificationPermissionGate.decide(SDK_U, true, true, true));
    }

    @Test
    public void neverAsked_requests_first() {
        // first ask on this install is always ours to take, the rationale
        // signal is irrelevant before any denial
        assertEquals(Decision.REQUEST,
                NotificationPermissionGate.decide(SDK_T, false, false, false));
        assertEquals(Decision.REQUEST,
                NotificationPermissionGate.decide(SDK_T, false, false, true));
        assertEquals(Decision.REQUEST,
                NotificationPermissionGate.decide(SDK_U, false, false, false));
    }

    @Test
    public void asked_withRationale_requests_again() {
        // denied once but the system still shows the "ask again" dialog
        assertEquals(Decision.REQUEST,
                NotificationPermissionGate.decide(SDK_T, false, true, true));
        assertEquals(Decision.REQUEST,
                NotificationPermissionGate.decide(SDK_U, false, true, true));
    }

    @Test
    public void asked_withoutRationale_opensSettings() {
        // asked and denied with no rationale: a new request would be silently
        // ignored, recovery is only possible in the notification settings
        assertEquals(Decision.OPEN_SETTINGS,
                NotificationPermissionGate.decide(SDK_T, false, true, false));
        assertEquals(Decision.OPEN_SETTINGS,
                NotificationPermissionGate.decide(SDK_U, false, true, false));
    }

    @Test
    public void tiramisu_isTheBoundary() {
        // 32 is the last level without the permission, 33 the first with it
        assertEquals(Decision.NONE,
                NotificationPermissionGate.decide(32, false, false, false));
        assertEquals(Decision.REQUEST,
                NotificationPermissionGate.decide(33, false, false, false));
    }

    @Test
    public void shouldAskOnLaunch_true_neverAskedAndNotAskedThisSession() {
        // the launch ask qualifies exactly once per install AND once per
        // process: Tiramisu+, not granted, never asked, not yet asked in
        // this session
        assertTrue(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_T, false, false, false));
        assertTrue(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_U, false, false, false));
    }

    @Test
    public void shouldAskOnLaunch_false_whenAskedThisSession() {
        // session guard: the controller sets its asked flag BEFORE showing
        // the dialog route, so the pause/resume around the system dialog or
        // a theme rebuild must never repeat the ask within one process
        assertFalse(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_T, false, false, true));
        assertFalse(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_U, false, false, true));
    }

    @Test
    public void shouldAskOnLaunch_false_whenAskedBefore() {
        // persist-on-fire makes the system dialog at-most-once-per-install,
        // an in-app dialog after any fired request is pointless — even in
        // the rationale-alive state where the contextual flows still ask
        assertTrue(Decision.REQUEST == NotificationPermissionGate
                .decide(SDK_T, false, true, true));
        assertFalse(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_T, false, true, false));
        // and in the permanently-denied state likewise
        assertFalse(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_U, false, true, false));
    }

    @Test
    public void shouldAskOnLaunch_false_granted_orBelowTiramisu() {
        assertFalse(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_T, true, false, false));
        assertFalse(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_U, true, true, true));
        // the permission does not exist below API 33
        assertFalse(NotificationPermissionGate
                .shouldAskOnLaunch(SDK_S, false, false, false));
    }

    @Test
    public void shouldShowDialogNow_true_onlyWhenSettledOnHomeRoute() {
        // every deferred-show condition green, current route = home ("/")
        assertTrue(NotificationPermissionGate.shouldShowDialogNow(
                true, true, false, Routes.HOME_PAGE, true));
    }

    @Test
    public void shouldShowDialogNow_false_whenRouteNotSettledOnHome() {
        // null = async restore not applied yet: showing would make the dialog
        // the bottom route of an empty stack
        assertFalse(NotificationPermissionGate.shouldShowDialogNow(
                true, true, false, null, true));
        // splash = fresh-install/cold-start transition still in flight: its
        // replace(home) would pop an unanswerable dialog off the stack
        assertFalse(NotificationPermissionGate.shouldShowDialogNow(
                true, true, false, "splash", true));
        // any other route likewise
        assertFalse(NotificationPermissionGate.shouldShowDialogNow(
                true, true, false, "/settings", true));
    }

    @Test
    public void shouldShowDialogNow_false_whenAnyOtherConditionFails() {
        String home = Routes.HOME_PAGE;
        // activity finishing/destroyed
        assertFalse(NotificationPermissionGate.shouldShowDialogNow(
                false, true, false, home, true));
        // activity paused
        assertFalse(NotificationPermissionGate.shouldShowDialogNow(
                true, false, false, home, true));
        // app locked
        assertFalse(NotificationPermissionGate.shouldShowDialogNow(
                true, true, true, home, true));
        // gate no longer asks (asked flag set or already asked this session)
        assertFalse(NotificationPermissionGate.shouldShowDialogNow(
                true, true, false, home, false));
    }
}
