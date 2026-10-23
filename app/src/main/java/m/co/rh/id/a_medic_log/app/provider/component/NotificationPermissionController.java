package m.co.rh.id.a_medic_log.app.provider.component;

import android.app.Activity;
import android.os.Handler;

import androidx.annotation.Nullable;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.ui.component.settings.NotificationMenuSV;
import m.co.rh.id.a_medic_log.app.util.UiUtils;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRouteChangedListener;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Stateful controller of the Android 13+ notification permission LAUNCH flow
 * (the in-app rationale dialog): owned by MainActivity, which forwards its
 * own lifecycle into {@link #markResumed()} (every resume, locked included),
 * {@link #onHostResume(Activity)} (the unlocked branch only — the ask on
 * launch), {@link #onHostPause()} and {@link #onHostDestroy()}. The reminder
 * workers only <em>check</em>
 * POST_NOTIFICATIONS before posting, so on API 33+ (where the permission
 * defaults to denied) reminders silently never show until something requests
 * it at runtime — this controller is that something.
 * <p>
 * Whole flow: the host's UNLOCKED onResume branch calls
 * {@link #onHostResume(Activity)} (lock-safety invariant: the in-app
 * rationale dialog and the system permission dialog behind it are their own
 * windows and must never stack with or race behind the LockDialog) → the ask
 * is deferred one main-loop turn → re-validation at execution time → the
 * in-app rationale dialog (the navigator's confirm dialog route, same
 * pattern as the delete confirmations) → user confirm fires the system
 * permission dialog → the gate persists the "asked" flag ON FIRE
 * (persist-on-fire, so the system dialog is an at-most-once-per-install
 * event no matter how its result is routed); a cancellation of the in-app
 * dialog persists nothing and the ask is made again on the next launch. The
 * known tradeoff (accepted, same as flash-deck): if the app is locked or the
 * screen turns off before the user answers the system dialog, the flag is
 * already set and the ask is never made again; recovery is the manual tap on
 * the settings page's Notifications row ({@link NotificationMenuSV}).
 * Contextual asks (reminder save/enable) are separate flows and use plain
 * REQUEST per Android's own rationale rules.
 * <p>
 * Scoping: deliberately NOT provider-registered — the controller holds
 * per-activity state (activity reference, resumed flag, session flag,
 * pending listener), so its lifetime must be the host activity's; it is
 * plain-{@code new}ed by the host and fully released in
 * {@link #onHostDestroy()} (listener unregistered from the
 * application-scoped navigator, activity reference dropped). The gate,
 * handler, preferences and lock manager it holds are application-scoped
 * singletons; the one deliberate exception is the provider — the host's
 * ACTIVITY-scoped nested provider, needed only for the confirm-dialog
 * config, lifetime-bound to the same activity that owns this controller:
 * onHostDestroy runs before the host disposes it and no controller method
 * runs afterwards, so it is never used post-dispose.
 * <p>
 * Constructor style mirrors {@link NotificationPermissionGate} itself: the
 * host's activity-scoped nested provider comes in as the single argument and
 * the application-scoped singletons are resolved lazily from it (matching
 * accessor per registration — lazyGet for the deferred registrations, get
 * for the eagerly registered Handler).
 * <p>
 * The decisions this controller consumes —
 * {@link NotificationPermissionGate#shouldAskOnLaunch(Activity, boolean)},
 * {@link NotificationPermissionGate#shouldShowDialogNow(boolean, boolean,
 * boolean, String, boolean)}, {@link NotificationPermissionGate#decide},
 * persist-on-fire in {@link NotificationPermissionGate#requestPermission} —
 * are pure or thin and unit-tested in NotificationPermissionGateTest; this
 * class only orchestrates them.
 */
public class NotificationPermissionController {

    /**
     * The host's activity-scoped provider, needed only to resolve the
     * navigator's confirm-dialog configuration for the dialog show.
     */
    private final Provider mProvider;
    private final ProviderValue<NotificationPermissionGate> mGate;
    /**
     * The provider-registered main-looper Handler (BaseProviderModule's
     * {@code new Handler(Looper.getMainLooper())}), the same handler the
     * host uses to stack the lock dialog — NOT a new instance.
     */
    private final Handler mHandler;
    private final ProviderValue<AppSharedPreferences> mSharedPreferences;
    private final ProviderValue<AppLockManager> mAppLockManager;

    /**
     * The host activity, set by {@link #onHostResume(Activity)} and dropped
     * by {@link #onHostDestroy()}. Nullable exactly while no host is live.
     */
    @Nullable
    private Activity mActivity;
    /**
     * Asked flag of the current process launch, set at the moment the
     * in-app rationale dialog route is actually pushed (inside the deferred
     * runnable): an unlocked re-resume (e.g. the pause/resume around the
     * system permission dialog, a theme rebuild) must never repeat the ask
     * within one session, while a deferred run that bails (activity gone,
     * locked, navigator not restored yet) must NOT consume the
     * once-per-process ask. The cross-launch guard is the persisted
     * "asked" flag in AppSharedPreferences, deliberately NOT written by a
     * cancellation of the in-app dialog.
     */
    private boolean mAskedThisSession;
    /**
     * Scoped one-shot route-change listener of the pending ask attempt:
     * registered while the navigator is still in the splash/restore
     * transition, fires when a route change lands (checked against the home
     * route), unregistered after the first successful show and in host
     * destroy (the navigator is application-scoped and must not keep the
     * activity reachable).
     */
    @Nullable
    private NavOnRouteChangedListener mRouteChangedListener;
    /**
     * Tracked manually via {@link #markResumed()} (every resume, locked
     * included) and {@link #onHostPause()}
     * (Activity.isResumed() is a hidden API and not callable); lets the
     * deferred runnable verify the activity is actually resumed at
     * execution time.
     */
    private boolean mIsResumed;

    public NotificationPermissionController(Provider provider) {
        mProvider = provider;
        mGate = provider.lazyGet(NotificationPermissionGate.class);
        // eagerly registered in BaseProviderModule, resolve at construction
        mHandler = provider.get(Handler.class);
        mSharedPreferences = provider.lazyGet(AppSharedPreferences.class);
        mAppLockManager = provider.lazyGet(AppLockManager.class);
    }

    /**
     * Host lifecycle entry, called from the host's onResume UNCONDITIONALLY
     * — locked resume included: a locked resume still means the activity IS
     * lifecycle-resumed, so the flag is a lifecycle fact, not a gate on
     * asking. Only the ask itself is gated to the unlocked branch
     * (see {@link #onHostResume(Activity)}) per the lock-safety design;
     * this split exactly preserves the pre-extraction flag semantics.
     */
    public void markResumed() {
        mIsResumed = true;
    }

    /**
     * Host lifecycle entry, called from the host's UNLOCKED onResume branch
     * only — never while the lock is presented (lock-safety invariant, see
     * class Javadoc). The resumed fact is tracked separately by
     * {@link #markResumed()}; this entry contains only the ask attempt:
     * it records the host and defers the ask one main-loop turn.
     * <p>
     * ASYNC-RESTORE CONSTRAINT — do not "simplify" this into a direct show
     * from onResume: a-navigator restores its route stack ASYNCHRONOUSLY. Its
     * onActivityCreated hands the persisted-state load to a background thread
     * which posts the stack-apply to the main looper, and that post only
     * executes after the create-&gt;start-&gt;resume transaction has returned.
     * A direct show from onResume therefore lands on an EMPTY stack: the
     * in-app rationale dialog becomes the bottom/initial route, the later
     * restore sees a non-empty stack and never adds the home page, and the
     * first pop of the size-1 stack makes the navigator finish the activity
     * (popInitialRoute -&gt; setActivityResultAndFinish) — the app exits to
     * the launcher (observed live on an API 36 emulator, fresh install).
     * The ask is therefore posted to the provider's main-looper Handler
     * (the same handler the host uses to stack the lock dialog). The FIFO
     * ordering versus the restore's post is LIKELY but not guaranteed (the
     * background load completion races onResume's post) — the execution-time
     * re-validation in {@link #tryShowDialog()} is what makes either ordering
     * safe, never the queue position alone.
     *
     * @param activity the host activity, held until {@link #onHostDestroy()}
     */
    public void onHostResume(Activity activity) {
        mActivity = activity;
        mHandler.post(this::tryAskOrWaitForHome);
    }

    /**
     * Host lifecycle entry, called from the host's onPause: clears the
     * resumed flag so a still-pending deferred runnable (or route-change
     * event) bails instead of showing over a backgrounded activity.
     */
    public void onHostPause() {
        mIsResumed = false;
    }

    /**
     * Host lifecycle entry, called from the host's onDestroy BEFORE its
     * super.onDestroy()/provider dispose: unregisters the pending
     * route-change listener from the application-scoped navigator (the
     * navigator outlives the activity and must not keep it reachable) and
     * drops the activity reference. A pending deferred runnable that still
     * fires afterwards finds no activity and bails.
     */
    public void onHostDestroy() {
        removeRouteChangedListener();
        mActivity = null;
    }

    /**
     * Execution-time entry of the deferred ask: show immediately when the
     * navigator has settled on the home route, otherwise wait for the
     * splash-&gt;home transition as a scoped route-change listener. Re-run
     * once per qualifying resume while the ask stays un-consumed.
     */
    private void tryAskOrWaitForHome() {
        if (mActivity == null) {
            // host destroyed between the post and this run (onHostDestroy
            // already dropped the reference); the ask is not lost, the next
            // live host resume re-runs it (session flag untouched)
            return;
        }
        if (tryShowDialog()) {
            // a listener from an earlier attempt may still be pending (e.g.
            // splash window, then backgrounded and resumed straight onto the
            // restored home route) — it is obsolete now
            removeRouteChangedListener();
            return;
        }
        // not showable right now: restore still in flight, splash transition,
        // activity gone, paused, or locked again. When the route is merely in
        // transition, the route-change listener lets the ask land in the
        // same session the moment the home route arrives.
        Activity activity = mActivity;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !mIsResumed || mRouteChangedListener != null) {
            return;
        }
        // SPLASH-TRANSITION CONSTRAINT — do not weaken the home-route check
        // to "stack not empty": on a fresh install (and on any cold start
        // after a normal exit, where the snapshot was cleared) the restore
        // pushes the initial route and SplashPage replaces it with the home
        // page one second later. A dialog shown in that window would sit ON
        // TOP of the splash and the replace() would pop it away with a NULL
        // route result — unanswerable, ask consumed, permanently suppressed
        // for that session. Only the home route guarantees a show that
        // sticks. While the route is merely in transition (not a hard bail),
        // a scoped one-shot listener waits for the home route: the navigator
        // fires it for the initial push (fresh install, its restore lambda
        // calls the public push) and for the splash->home replace, so the
        // dialog still lands in the SAME session, event-driven, no polling.
        // Known residual gap: a SAVED-stack restore applies silently (no
        // route-change event) — if it lands after the deferred attempt, the
        // ask waits for the next qualifying resume; the session flag is
        // only ever consumed by an actual show.
        //
        // one-shot scoped listener, unregistered after the home route lands
        // and in onHostDestroy (the navigator is application-scoped, the
        // listener must not outlive the activity)
        mRouteChangedListener = (previousRoute, currentRoute) -> {
            // only the transition landing on the home route is of interest;
            // read the navigator's own current route rather than the callback
            // parameter — the push has already been applied to the stack at
            // dispatch time, which makes this the single source of truth
            NavRoute current = BaseApplication.of(activity).getNavigator(activity)
                    .getCurrentRoute();
            if (current == null || !Routes.HOME_PAGE.equals(current.getRouteName())) {
                return;
            }
            // unregister BEFORE deciding the show outcome — the listener is
            // one-shot whatever the outcome, a terminal state (gate no longer
            // asking) must not keep it registered until destroy. Deferred
            // removal: the navigator dispatches onChanged while iterating its
            // listener list (a fail-fast collection — an ArrayList in
            // v0.0.73), a structural remove inside the callback would throw
            // ConcurrentModificationException
            mHandler.post(this::removeRouteChangedListener);
            tryShowDialog();
        };
        BaseApplication.of(activity).getNavigator(activity)
                .addOnRouteChangedListener(mRouteChangedListener);
    }

    /**
     * The actual, fully re-validated show attempt: only an actual show
     * consumes the once-per-process ask (mAskedThisSession), any failed
     * check leaves it false so the next qualifying resume (or route change)
     * tries again. Runs on the main thread — every push/pop/replace/retry
     * caller (the operations that dispatch onRouteChanged) originates there.
     *
     * @return true when the in-app rationale dialog route was pushed
     */
    private boolean tryShowDialog() {
        Activity activity = mActivity;
        INavigator navigator = BaseApplication.of(activity).getNavigator(activity);
        NavRoute currentRoute = navigator.getCurrentRoute();
        boolean gateAsks = mGate.get().shouldAskOnLaunch(activity,
                mAskedThisSession);
        if (!NotificationPermissionGate.shouldShowDialogNow(
                !activity.isFinishing() && !activity.isDestroyed(), mIsResumed,
                isLockRequired(),
                currentRoute != null ? currentRoute.getRouteName() : null,
                gateAsks)) {
            return false;
        }
        mAskedThisSession = true;
        UiUtils.showConfirmDialog(navigator,
                mProvider,
                activity.getString(R.string.title_confirm),
                activity.getString(R.string.notification_permission_rationale),
                // confirmed: fire the system dialog, the gate persists the
                // "asked" flag on fire. Cancelled or dismissed outside:
                // nothing is persisted, the ask is made again next launch
                () -> mGate.get().requestPermission(activity));
        return true;
    }

    private void removeRouteChangedListener() {
        if (mRouteChangedListener != null) {
            Activity activity = mActivity;
            if (activity != null) {
                BaseApplication.of(activity).getNavigator(activity)
                        .removeOnRouteChangedListener(mRouteChangedListener);
            }
            mRouteChangedListener = null;
        }
    }

    private boolean isLockRequired() {
        return mSharedPreferences.get().isAppLockEnabled()
                && mAppLockManager.get().isLocked();
    }
}
