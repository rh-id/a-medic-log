package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class AppLockManagerTest {

    @Test
    public void initial_state_isLocked() {
        // the app is always born locked on a fresh process
        assertTrue(new AppLockManager().isLocked());
    }

    @Test
    public void stashedIntents_areConsumedInFifoOrder() {
        AppLockManager appLockManager = new AppLockManager();
        Intent first = new Intent("m.co.rh.id.a_medic_log.test.FIRST");
        Intent second = new Intent("m.co.rh.id.a_medic_log.test.SECOND");

        appLockManager.stashIntent(first);
        appLockManager.stashIntent(second);

        assertSame(first, appLockManager.consumeStashedIntent());
        assertSame(second, appLockManager.consumeStashedIntent());
        assertNull(appLockManager.consumeStashedIntent());
    }

    @Test
    public void lockAndUnlock_togglesState() {
        AppLockManager appLockManager = new AppLockManager();
        appLockManager.unlock();
        assertFalse(appLockManager.isLocked());
        appLockManager.lock();
        assertTrue(appLockManager.isLocked());
    }

    @Test
    public void checkAvailability_returnsValidEnum() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppLockManager.LockAvailability availability = new AppLockManager().checkAvailability(context);
        // don't over-assert device specific availability, the enum just must be
        // non-null and consistent with the device's secure lock state
        assertNotNull(availability);
        KeyguardManager keyguardManager = (KeyguardManager)
                context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && !keyguardManager.isKeyguardSecure()) {
            // without a secure screen lock the lock can never be available,
            // CI emulators (API 23+) have a software fingerprint that is
            // typically un-enrolled, which must map to a non-available state
            assertNotEquals(AppLockManager.LockAvailability.AVAILABLE, availability);
        }
    }

    @Test
    public void appLockEnabledPreference_roundTrips() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProviderModule module = new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                providerRegistry.register(ExecutorService.class, Executors::newSingleThreadExecutor);
                providerRegistry.register(Handler.class,
                        () -> new Handler(Looper.getMainLooper()));
            }
        };
        Provider provider = Provider.createProvider(context, module);
        try {
            AppSharedPreferences preferences = new AppSharedPreferences(provider);
            preferences.setAppLockEnabled(true);
            assertTrue(preferences.isAppLockEnabled());
            preferences.setAppLockEnabled(false);
            assertFalse(preferences.isAppLockEnabled());
        } finally {
            provider.dispose();
        }
    }
}
