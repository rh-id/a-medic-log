package m.co.rh.id.a_medic_log.app.provider.component;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.util.LinkedList;

import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;

/**
 * Holds the in-memory lock state of the app.
 * <p>
 * The app starts locked on every process start (process death always starts locked),
 * is unlocked only by a successful biometric/device-credential authentication and
 * re-locks only when the screen goes off. Backgrounding, home press or app switches
 * never re-lock the app.
 * <p>
 * The biometric prompt lifecycle itself is owned by {@link AppLockAuthHandler},
 * this class only holds the resulting lock state.
 */
public class AppLockManager {

    /**
     * Result of {@link #checkAvailability(Context)}.
     */
    public enum LockAvailability {
        /**
         * At least one allowed authenticator can be used right now
         * (biometrics enrolled or a secure screen lock is set)
         */
        AVAILABLE,
        /**
         * Neither biometrics nor a secure screen lock is set up on this device
         */
        NO_AUTHENTICATION,
        /**
         * Biometric hardware is present but temporarily unavailable
         */
        HW_UNAVAILABLE
    }

    /**
     * Lock state of the current process, the app is always born locked.
     * volatile because registerAsync may construct this component on a background
     * thread, while reads happen on the main thread
     */
    private volatile boolean mLocked = true;
    private final LinkedList<Intent> mStashedIntents;

    public AppLockManager() {
        mStashedIntents = new LinkedList<>();
    }

    public void lock() {
        mLocked = true;
    }

    public void unlock() {
        mLocked = false;
    }

    public boolean isLocked() {
        return mLocked;
    }

    /**
     * Checks whether any allowed authenticator (biometric or device credential)
     * can currently be used on this device.
     * <p>
     * API level caveats: on API 29 and below
     * {@link BiometricManager#canAuthenticate(int)} does not reflect device
     * credential enrollment, therefore a secure screen lock is checked separately
     * with {@link KeyguardManager#isDeviceSecure()}. On API 22 and below the
     * biometric APIs are not available at all, so only the deprecated
     * {@link KeyguardManager#isKeyguardSecure()} is checked.
     * When no secure screen lock is set up, {@link LockAvailability#NO_AUTHENTICATION}
     * is always returned (even if the biometric hardware is temporarily
     * unavailable), because the settings row must deep-link the user to set an
     * authenticator up instead of reporting a temporary problem.
     *
     * @param context any context, the application context is used internally
     * @return the current availability, never null
     */
    public LockAvailability checkAvailability(Context context) {
        Context appContext = context.getApplicationContext();
        KeyguardManager keyguardManager = (KeyguardManager)
                appContext.getSystemService(Context.KEYGUARD_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (keyguardManager != null && keyguardManager.isKeyguardSecure()) {
                return LockAvailability.AVAILABLE;
            }
            return LockAvailability.NO_AUTHENTICATION;
        }
        int canAuthenticate = BiometricManager.from(appContext)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            return LockAvailability.AVAILABLE;
        }
        boolean keyguardSecure = keyguardManager != null && keyguardManager.isDeviceSecure();
        if (!keyguardSecure) {
            // nothing to authenticate with at all, direct the user to set up
            // a screen lock instead of reporting a temporary hardware problem
            return LockAvailability.NO_AUTHENTICATION;
        }
        if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            // a secure screen lock is set, report the temporary biometric problem
            return LockAvailability.HW_UNAVAILABLE;
        }
        // the secure screen lock alone is enough to use the lock
        return LockAvailability.AVAILABLE;
    }

    /**
     * Defers a notification intent until the app is unlocked,
     * the stashed intents are consumed in FIFO order by {@link #consumeStashedIntent()}.
     *
     * @param intent notification intent to defer
     */
    public void stashIntent(Intent intent) {
        synchronized (mStashedIntents) {
            mStashedIntents.add(intent);
        }
    }

    /**
     * @return the oldest stashed intent, or null when no intent is stashed.
     * The returned intent is removed from the stash.
     */
    public Intent consumeStashedIntent() {
        synchronized (mStashedIntents) {
            return mStashedIntents.pollFirst();
        }
    }

    /**
     * Registers a receiver that locks the app whenever the screen goes off.
     * Must be called once from {@link android.app.Application#onCreate()} so the
     * receiver exists even when the process is started in the background by
     * WorkManager. The receiver is intentionally never unregistered, it lives
     * for the whole process lifetime (the lock state is process state).
     * <p>
     * Manifest registration cannot be used for ACTION_SCREEN_OFF (it is no longer
     * delivered to manifest-declared receivers since API 26), and it must be a
     * dynamic receiver anyway since it mutates in-memory state.
     *
     * @param context any context, the application context is used internally
     */
    public void registerScreenOffReceiver(Context context) {
        Context appContext = context.getApplicationContext();
        IntentFilter screenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    lock();
                }
            }
        };
        // ACTION_SCREEN_OFF is a protected system broadcast, but registering
        // through ContextCompat keeps the export flag explicit and correct on
        // every API level
        ContextCompat.registerReceiver(appContext, screenOffReceiver,
                screenOffFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}
