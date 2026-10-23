package m.co.rh.id.a_medic_log.app.provider.component;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Single owner of the Android 13 (Tiramisu) {@code POST_NOTIFICATIONS}
 * runtime permission flow. The reminder workers only <em>check</em> the
 * permission before posting (a worker can never show the request dialog), so
 * without a runtime request the permission stays at its API 33+ default state
 * (denied) and medicine reminders silently never surface.
 * <p>
 * The decision is split in two: {@link #decide(int, boolean, boolean, boolean)}
 * is the pure, unit-testable rule set, while {@link #decide(Activity)} wraps
 * the Android calls (permission state, rationale availability) and the
 * persisted "asked" flag around it. View layers act on the returned
 * {@link Decision}, they never touch the permission APIs themselves.
 * <p>
 * Who asks when — "the ask" is the in-app rationale dialog (this app's
 * confirm dialog route) followed, on confirm, by the system
 * POST_NOTIFICATIONS dialog. The launch flow (MainActivity's unlocked
 * resume) uses {@link #shouldAskOnLaunch(int, boolean, boolean, boolean)}:
 * the ask is made at most once per process launch, and the "asked" flag is
 * persisted the moment the system dialog fires (persist-on-fire), which
 * makes the system dialog an at-most-once-per-install event that
 * result-routing races cannot duplicate; a cancellation of the in-app
 * dialog persists nothing so the ask is made again on the next launch. The
 * contextual flows (reminder save/enable, settings row) use plain
 * {@link #decide(Activity)} and may ask again per Android's own rationale
 * rules. A denial with "don't ask again" flips the decision to
 * {@link Decision#OPEN_SETTINGS}, whose only entry point is the manual tap
 * in the settings page (the gate never launches system settings on its
 * own, it only hands out the intent).
 */
public class NotificationPermissionGate {

    /**
     * What a caller should do to move the notification permission forward.
     */
    public enum Decision {
        /**
         * Show the system permission request dialog (first ask on this
         * install, or a previous denial where the system still allows asking
         * again, i.e. the rationale would be shown).
         */
        REQUEST,
        /**
         * The system would silently ignore a new request (denied with "don't
         * ask again"), recovery is only possible in the app notification
         * settings screen.
         */
        OPEN_SETTINGS,
        /**
         * Nothing to do, the permission is granted or irrelevant on this
         * Android version.
         */
        NONE
    }

    /**
     * Request code of the runtime permission request, launched with
     * {@link ActivityCompat#requestPermissions(Activity, String[], int)} and
     * answered by the owning activity's {@code onRequestPermissionsResult}
     * through {@link #onRequestPermissionsResult()}. Deliberately outside the
     * navigator's request-code range, next to
     * {@link AppLockAuthHandler#REQUEST_CONFIRM_CREDENTIAL}.
     */
    public static final int REQUEST_POST_NOTIFICATIONS = 4202;

    private final ProviderValue<AppSharedPreferences> mAppSharedPreferences;

    public NotificationPermissionGate(Provider provider) {
        mAppSharedPreferences = provider.lazyGet(AppSharedPreferences.class);
    }

    /**
     * Pure decision core, no Android calls, unit-testable on the JVM.
     *
     * @param sdkInt              device API level
     * @param permissionGranted   current runtime state of POST_NOTIFICATIONS
     * @param askedBefore         whether a runtime request was already
     *                            presented to the user on this install
     * @param shouldShowRationale system rationale signal, only meaningful
     *                            after a first denial
     */
    public static Decision decide(int sdkInt, boolean permissionGranted,
                                  boolean askedBefore, boolean shouldShowRationale) {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            // below Android 13 the permission does not exist, notifications
            // are granted at install time
            return Decision.NONE;
        }
        if (permissionGranted) {
            return Decision.NONE;
        }
        if (!askedBefore) {
            // never asked on this install, the first request is always ours
            // to take (the system shows the dialog without a rationale)
            return Decision.REQUEST;
        }
        // asked and denied: the system still shows the dialog while the
        // "ask again" path is alive (rationale), otherwise the request would
        // be silently ignored and only the settings screen can recover
        return shouldShowRationale ? Decision.REQUEST : Decision.OPEN_SETTINGS;
    }

    /**
     * Runtime state of the notification permission. True below Android 13,
     * where the permission does not exist and notifications are always
     * allowed.
     */
    public boolean isPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Decision for the given activity, combining the pure rules with the live
     * permission state, the system rationale signal and the persisted "asked"
     * flag.
     */
    public Decision decide(Activity activity) {
        boolean permissionGranted = isPermissionGranted(activity);
        if (!permissionGranted
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean shouldShowRationale = ActivityCompat
                    .shouldShowRequestPermissionRationale(activity,
                            Manifest.permission.POST_NOTIFICATIONS);
            boolean askedBefore = mAppSharedPreferences.get()
                    .isNotificationPermissionAsked();
            return decide(Build.VERSION.SDK_INT, false, askedBefore,
                    shouldShowRationale);
        }
        return Decision.NONE;
    }

    /**
     * Launch-flow variant of the decision, pure and unit-testable on the
     * JVM: true only when the ask should be made on launch. All four
     * conditions must hold — the permission must exist (Tiramisu+) and be
     * not granted, no request may have fired on this install yet
     * (persist-on-fire makes the system dialog at-most-once-per-install, so
     * past that point the in-app dialog would be pointless) and the ask must
     * not have been made in the current process (the owning controller
     * passes its session flag, an unlocked re-resume or a theme rebuild must
     * never repeat the ask within one session). A cancellation of the
     * in-app dialog persists nothing, so the ask re-qualifies on the next
     * launch by itself.
     */
    public static boolean shouldAskOnLaunch(int sdkInt, boolean permissionGranted,
                                            boolean askedBefore, boolean askedThisSession) {
        return sdkInt >= Build.VERSION_CODES.TIRAMISU
                && !permissionGranted
                && !askedBefore
                && !askedThisSession;
    }

    /**
     * {@link #shouldAskOnLaunch(int, boolean, boolean, boolean)} with the
     * live Android inputs and the persisted "asked" flag; the session flag
     * stays with the caller (it is the owning controller's instance state).
     * For the launch flow ONLY: the settings page row and the contextual
     * reminder flows must use plain {@link #decide(Activity)} instead.
     */
    public boolean shouldAskOnLaunch(Activity activity, boolean askedThisSession) {
        return shouldAskOnLaunch(Build.VERSION.SDK_INT,
                isPermissionGranted(activity),
                mAppSharedPreferences.get().isNotificationPermissionAsked(),
                askedThisSession);
    }

    /**
     * Pure predicate for the deferred dialog show
     * (NotificationPermissionController posts its ask attempt to the main
     * looper because the navigator restores its route stack
     * asynchronously): true only when the world is still fit to
     * show the in-app rationale dialog at execution time — the activity
     * alive (not finishing/destroyed) and resumed, the app unlocked, and the
     * navigator settled on the HOME route, not merely "has some route".
     * <p>
     * The home-route requirement is the load-bearing check: a null route
     * means the async restore has not applied yet (showing would make the
     * dialog the bottom route and a later pop would finish the activity),
     * while the splash route means the fresh-install/cold-start transition
     * is still in flight — its {@code replace(home)} pops whatever sits on
     * top of the splash, which would silently wipe an unanswerable dialog
     * (null route result, the ask consumed). Only the home route guarantees
     * a show that sticks.
     * <p>
     * Pure rule, no Android calls, unit-testable on the JVM.
     */
    public static boolean shouldShowDialogNow(boolean activityAlive, boolean activityResumed,
                                              boolean lockRequired, String currentRouteName,
                                              boolean gateAsks) {
        return activityAlive
                && activityResumed
                && !lockRequired
                && Routes.HOME_PAGE.equals(currentRouteName)
                && gateAsks;
    }

    /**
     * Presents the system permission request dialog. Only call with a
     * {@link Decision#REQUEST} from {@link #decide(Activity)} (contextual
     * flows) or from a confirmed in-app dialog (launch flow), any other
     * state either needs no request or would be silently ignored by the
     * system.
     * <p>
     * Persist-on-fire: the "asked" flag is set immediately AFTER firing the
     * request, NOT when the result arrives. The activity pauses/resumes
     * around the system dialog, so a result-routed persist could race a
     * concurrent resume path into re-prompting; persisting on fire makes the
     * system dialog an at-most-once-per-install event regardless of how the
     * result is routed (or whether it ever arrives). The known tradeoff: if
     * the app is locked or the screen turns off before the user answers, the
     * flag is already persisted, so the launch ask is never made again —
     * recovery is the settings page's Notifications row.
     * <p>
     * The outcome is delivered to the owning activity's
     * {@code onRequestPermissionsResult}, which must still route
     * {@link #REQUEST_POST_NOTIFICATIONS} to {@link #onRequestPermissionsResult()}.
     */
    public void requestPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
        // persist right after firing (flash-deck semantics), see above
        mAppSharedPreferences.get().setNotificationPermissionAsked(true);
    }

    /**
     * Outcome of {@link #REQUEST_POST_NOTIFICATIONS}, called from the owning
     * activity's {@code onRequestPermissionsResult}. With persist-on-fire in
     * {@link #requestPermission(Activity)} this is normally redundant
     * (idempotent backup for a result that still slips through); it remains a
     * no-op beyond the flag: the reminder save/enable that triggered a
     * request is never blocked on the grant.
     */
    public void onRequestPermissionsResult() {
        mAppSharedPreferences.get().setNotificationPermissionAsked(true);
    }

    /**
     * Contextual ask for the high-intent reminder flows (saving a NEW
     * reminder, flipping a reminder's enable switch): fires the system
     * dialog only while Android still allows asking — a plain
     * {@link Decision#REQUEST} means the first ask on this install or a
     * denial with the rationale still alive. OPEN_SETTINGS is deliberately
     * ignored here: once the system would silently ignore a request,
     * permanent-denial recovery belongs to the manual tap on the settings
     * page's Notifications row (NotificationMenuSV), not to a save/enable
     * action that must never block on the permission. The caller is
     * responsible for deciding WHEN the ask is relevant (new or
     * newly-enabled reminder); this only encapsulates the ask-or-skip
     * decision.
     */
    public void contextualRequestIfNeeded(Activity activity) {
        if (decide(activity) == Decision.REQUEST) {
            requestPermission(activity);
        }
    }

    /**
     * Fire-and-forget contextual ask for the reminder enable/save flows: call
     * this when an action leaves a reminder ENABLED (created-with-enable or
     * flipped-on) — that is the only situation where asking is relevant, a
     * disabled reminder never surfaces as a notification. Asks only while
     * Android still allows asking (a plain {@link Decision#REQUEST});
     * OPEN_SETTINGS is deliberately ignored here, permanent-denial recovery
     * belongs to the manual tap on the settings page's Notifications row
     * (NotificationMenuSV). The save/enable proceeds whatever the user
     * answers in the system dialog — MainActivity consumes the result and
     * persists only the gate's "asked" flag.
     */
    public void maybeRequestOnReminderEnabled(Activity activity,
                                              MedicineReminder reminder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !Boolean.TRUE.equals(reminder.reminderEnabled)) {
            return;
        }
        contextualRequestIfNeeded(activity);
    }

    /**
     * Recovers from a {@link Decision#OPEN_SETTINGS} state: opens this app's
     * notification settings screen where the user can re-enable notifications
     * manually. Only reachable on Tiramisu+ (the OPEN_SETTINGS decision only
     * exists there), the API gate lives on the caller's decision.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public Intent createAppNotificationSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        return intent;
    }
}
