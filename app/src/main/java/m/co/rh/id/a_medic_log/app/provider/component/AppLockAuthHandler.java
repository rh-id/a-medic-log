package m.co.rh.id.a_medic_log.app.provider.component;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Single owner of the app-lock biometric prompt lifecycle and of its presenting
 * flag. View layers only request a presentation or surface hints through
 * {@link Callbacks}, they never mutate the authentication state themselves.
 * <p>
 * Android 10 (Q) only: when no biometric is enrolled the presentation skips
 * BiometricPrompt entirely and launches the legacy
 * {@link KeyguardManager#createConfirmDeviceCredentialIntent} credential
 * screen instead, because the biometric 1.1.0 credential flow on Q loses its
 * result (platform bug b/142740104, the app would stay locked forever); see
 * {@link #presentViaKeyguard}.
 */
public class AppLockAuthHandler {

    /**
     * Request code of the Q-only keyguard credential fallback, launched with
     * {@link FragmentActivity#startActivityForResult(Intent, int)} and answered
     * by the owning activity's {@code onActivityResult} through
     * {@link #onCredentialResult(int)}. Deliberately outside the navigator's
     * request-code range.
     */
    public static final int REQUEST_CONFIRM_CREDENTIAL = 4201;

    /**
     * Outcome callbacks of a presentation. Invoked on the main thread and only
     * for the current presentation generation, callbacks of superseded
     * presentations are discarded as stale.
     */
    public interface Callbacks {
        /**
         * A message to surface to the user (lockout, missing authenticator, ...)
         */
        void onHint(@NonNull CharSequence hint);

        /**
         * The user authenticated successfully for the current presentation
         */
        void onAuthenticated();
    }

    private final ProviderValue<Handler> mHandler;
    private final ProviderValue<AppLockManager> mAppLockManager;
    /**
     * Generation token, every presentation and every cancel bumps it so that
     * callbacks of superseded presentations are discarded as stale
     */
    private int mPresentation;
    private BiometricPrompt mActivePrompt;
    /**
     * True while a prompt presentation is in flight. volatile because
     * registerAsync may construct this component on a background thread while
     * reads happen on the main thread
     */
    private volatile boolean mPresenting;
    /**
     * Pending record of the Q-only keyguard credential launch: the launching
     * activity, its callbacks and the generation at launch time. Held only
     * while the system credential screen is up, cleared on result delivery and
     * by cancel(). Main-thread only, like every other lifecycle mutation here.
     */
    @Nullable
    private PendingCredentialLaunch mPendingCredentialLaunch;

    public AppLockAuthHandler(Provider provider) {
        mHandler = provider.lazyGet(Handler.class);
        mAppLockManager = provider.lazyGet(AppLockManager.class);
    }

    public boolean isPresenting() {
        return mPresenting;
    }

    /**
     * Presents the biometric prompt with the default title (the app name)
     * unless a presentation is already in flight. Re-checks the authenticator
     * availability first and surfaces a set-up hint instead of presenting when
     * no authenticator exists on the device.
     * <p>
     * The actual presentation is deferred one main-loop turn: biometric 1.1.0
     * silently no-ops (and never calls back) while the cancel of a previous
     * presentation is still in flight, the defer avoids that cancel-race.
     *
     * @param activity  the activity that owns the prompt window
     * @param callbacks outcome callbacks, never invoked after {@link #cancel()}
     */
    public void ensurePresented(@NonNull FragmentActivity activity, @NonNull Callbacks callbacks) {
        ensurePresented(activity, callbacks, null);
    }

    /**
     * Core of the presentation queue above, kept title-parameterized only for
     * {@link #forcePresent(FragmentActivity, Callbacks, CharSequence)} (the
     * security menu's enable-confirmation prompt overrides the title), private
     * so callers cannot bypass the default-title entry point above.
     *
     * @param activity      the activity that owns the prompt window
     * @param callbacks     outcome callbacks, never invoked after {@link #cancel()}
     * @param titleOverride optional prompt title, the app name is used when null
     */
    private void ensurePresented(@NonNull FragmentActivity activity, @NonNull Callbacks callbacks,
                                 @Nullable CharSequence titleOverride) {
        if (mPresenting) {
            return;
        }
        if (mAppLockManager.get().checkAvailability(activity)
                == AppLockManager.LockAvailability.NO_AUTHENTICATION) {
            // nothing to authenticate with, tell the user what to set up
            callbacks.onHint(activity.getString(R.string.app_lock_no_auth_hint));
            return;
        }
        final int presentation = ++mPresentation;
        mHandler.get().post(() -> {
            if (presentation != mPresentation) {
                // superseded by cancel() while the presentation was deferred
                return;
            }
            // Q without an enrolled biometric: the biometric 1.1.0 credential
            // flow loses its result on Q (b/142740104), route to the legacy
            // keyguard screen instead. canAuthenticate only runs on Q, the
            // SDK_INT check short-circuits before it on every other level.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                    && BiometricManager.from(activity)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    != BiometricManager.BIOMETRIC_SUCCESS) {
                presentViaKeyguard(activity, callbacks, presentation, titleOverride);
                return;
            }
            doPresent(activity, callbacks, presentation, titleOverride);
        });
    }

    /**
     * Recovers from any wedged presentation with the default prompt title:
     * resets the presenting flag unconditionally (explicit user intent, e.g.
     * the Unlock button) and delegates to
     * {@link #ensurePresented(FragmentActivity, Callbacks)}.
     *
     * @param activity  the activity that owns the prompt window
     * @param callbacks outcome callbacks
     */
    public void forcePresent(@NonNull FragmentActivity activity, @NonNull Callbacks callbacks) {
        forcePresent(activity, callbacks, null);
    }

    /**
     * Recovers from any wedged presentation: resets the presenting flag
     * unconditionally (explicit user intent, e.g. the security menu's
     * enable-confirmation prompt) and delegates to the presentation queue with
     * the given prompt title.
     *
     * @param activity      the activity that owns the prompt window
     * @param callbacks     outcome callbacks
     * @param titleOverride optional prompt title, the app name is used when null
     */
    public void forcePresent(@NonNull FragmentActivity activity, @NonNull Callbacks callbacks,
                             @Nullable CharSequence titleOverride) {
        mPresenting = false;
        ensurePresented(activity, callbacks, titleOverride);
    }

    /**
     * Tears the current presentation down: bumps the generation (callbacks of
     * the current or of a queued not-yet-started presentation are discarded as
     * stale), cancels the system prompt when one is active and clears the
     * presenting flag.
     */
    public void cancel() {
        // bumped unconditionally so a cancel issued between ensurePresented's
        // deferred post and the actual presentation also invalidates the
        // queued presentation
        mPresentation++;
        BiometricPrompt activePrompt = mActivePrompt;
        if (activePrompt != null) {
            activePrompt.cancelAuthentication();
            mActivePrompt = null;
        }
        // a keyguard credential screen cannot be dismissed from here (the
        // system screen has no cancel handle), the generation bump above alone
        // marks its pending launch stale so the late onActivityResult is
        // discarded by onCredentialResult
        mPendingCredentialLaunch = null;
        mPresenting = false;
    }

    /**
     * Outcome of the Q-only keyguard credential screen, called from the owning
     * activity's {@code onActivityResult} for
     * {@link #REQUEST_CONFIRM_CREDENTIAL}. The pending launch record is
     * consumed one-shot whatever the outcome.
     * <p>
     * Stale results (the generation superseded by cancel() or a newer
     * presentation) are discarded like the stale BiometricPrompt callbacks, and
     * a non-OK result (the user backed out) stays silent with the same
     * semantics as ERROR_USER_CANCELED: the next resume or the Unlock button
     * re-presents.
     *
     * @param resultCode resultCode delivered to onActivityResult
     */
    public void onCredentialResult(int resultCode) {
        PendingCredentialLaunch pending = mPendingCredentialLaunch;
        mPendingCredentialLaunch = null;
        if (pending == null || pending.generation != mPresentation) {
            // no pending keyguard launch, or a stale one superseded by
            // cancel() or a newer presentation
            return;
        }
        mPresenting = false;
        if (pending.activity.isFinishing() || pending.activity.isDestroyed()) {
            // the owning activity died while the system screen was up, the
            // next resume re-presents (mirrors doPresent's dying-activity guard)
            return;
        }
        if (resultCode == Activity.RESULT_OK) {
            pending.callbacks.onAuthenticated();
        }
        // non-OK: the user backed out, stay silent, same semantics as
        // ERROR_USER_CANCELED in handleAuthenticationError
    }

    private void doPresent(@NonNull FragmentActivity activity, @NonNull Callbacks callbacks,
                           int presentation, @Nullable CharSequence titleOverride) {
        if (mPresenting || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        // a fresh BiometricPrompt instance must be constructed for every presentation
        Executor mainExecutor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt prompt = new BiometricPrompt(activity, mainExecutor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        if (presentation != mPresentation) {
                            // stale callback of a canceled presentation
                            return;
                        }
                        mActivePrompt = null;
                        mPresenting = false;
                        callbacks.onAuthenticated();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                      @NonNull CharSequence errString) {
                        if (presentation != mPresentation) {
                            // stale callback of a canceled presentation
                            return;
                        }
                        mActivePrompt = null;
                        mPresenting = false;
                        handleAuthenticationError(activity, errorCode, errString, callbacks);
                    }

                    // onAuthenticationFailed is a no-op on purpose,
                    // the system prompt already handles the retry UX
                });
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(titleOverride != null ? titleOverride
                        : activity.getString(R.string.app_name))
                .setSubtitle(activity.getString(R.string.app_lock_locked_subtitle))
                // credential mode forbids a negative button
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        // fault isolation: biometric 1.1.0 has verified silent no-op paths
        // (state already saved, activity gone, prompt already showing) that never
        // call back, without this guard the presenting flag would wedge forever
        try {
            mPresenting = true;
            mActivePrompt = prompt;
            prompt.authenticate(promptInfo);
        } catch (Exception e) {
            mActivePrompt = null;
            mPresenting = false;
            callbacks.onHint(activity.getString(R.string.app_lock_error_prefix,
                    String.valueOf(e.getMessage())));
        }
    }

    /**
     * Q-only device-credential fallback: launches the legacy KeyguardManager
     * credential screen instead of a BiometricPrompt.
     * <p>
     * Rationale: androidx.biometric 1.1.0 on Q with
     * BIOMETRIC_WEAK | DEVICE_CREDENTIAL and no biometric enrolled hits
     * platform bug b/142740104: the framework credential flow
     * (ConfirmDeviceCredentialActivity$InternalActivity finishes itself and
     * forwards to ConfirmLockPassword) loses its result, so
     * onAuthenticationSucceeded never fires and the app stays locked forever.
     * androidx's own fix (1.2.0-alpha04) silently falls back to
     * KeyguardManager.createConfirmDeviceCredentialIntent on Q, but no stable
     * androidx release contains it (stable is still 1.1.0 as of 2026-10), so
     * the same fallback is implemented here. The deprecated-on-Q
     * KeyguardManager API is used intentionally.
     * <p>
     * cancel() cannot dismiss the already-launched system credential screen, it
     * only invalidates the generation so the late onActivityResult is discarded
     * as stale (see cancel and onCredentialResult).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private void presentViaKeyguard(@NonNull FragmentActivity activity,
                                    @NonNull Callbacks callbacks, int presentation,
                                    @Nullable CharSequence titleOverride) {
        if (mPresenting || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        // string-based lookup like AppLockManager, the class-based overload is
        // API 23+ and this method must stay lint-clean on every level (the Q
        // gate lives on the caller, not here)
        KeyguardManager keyguardManager = (KeyguardManager)
                activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager == null || !keyguardManager.isDeviceSecure()) {
            // belt & braces, the availability check upstream should normally
            // have caught an unsecured device
            callbacks.onHint(activity.getString(R.string.app_lock_no_auth_hint));
            return;
        }
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                titleOverride != null ? titleOverride
                        : activity.getString(R.string.app_name),
                activity.getString(R.string.app_lock_locked_subtitle));
        if (intent == null) {
            // only null on an unsecured device, same hint path as above
            callbacks.onHint(activity.getString(R.string.app_lock_no_auth_hint));
            return;
        }
        // fault isolation: mirrors doPresent, a throwing launch must never
        // wedge the presenting flag forever
        try {
            mPresenting = true;
            mPendingCredentialLaunch =
                    new PendingCredentialLaunch(activity, callbacks, presentation);
            activity.startActivityForResult(intent, REQUEST_CONFIRM_CREDENTIAL);
        } catch (Exception e) {
            mPendingCredentialLaunch = null;
            mPresenting = false;
            callbacks.onHint(activity.getString(R.string.app_lock_error_prefix,
                    String.valueOf(e.getMessage())));
        }
    }

    private void handleAuthenticationError(@NonNull FragmentActivity activity, int errorCode,
                                           CharSequence errString, @NonNull Callbacks callbacks) {
        switch (errorCode) {
            case BiometricPrompt.ERROR_CANCELED:
            case BiometricPrompt.ERROR_USER_CANCELED:
                // ERROR_CANCELED is fired by backgrounding, stay silent, the next
                // resume re-presents the prompt.
                // ERROR_USER_CANCELED means the user backed out intentionally,
                // the Unlock button retries.
                break;
            case BiometricPrompt.ERROR_LOCKOUT:
            case BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                callbacks.onHint(activity.getString(R.string.app_lock_lockout_hint));
                break;
            default:
                callbacks.onHint(activity.getString(R.string.app_lock_error_prefix, errString));
                break;
        }
    }

    /**
     * Pending record of a keyguard credential launch, see
     * {@link #mPendingCredentialLaunch}.
     */
    private static final class PendingCredentialLaunch {
        final FragmentActivity activity;
        final Callbacks callbacks;
        final int generation;

        PendingCredentialLaunch(FragmentActivity activity, Callbacks callbacks, int generation) {
            this.activity = activity;
            this.callbacks = callbacks;
            this.generation = generation;
        }
    }
}
