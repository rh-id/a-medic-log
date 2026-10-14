package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertFalse;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class AppLockAuthHandlerTest {

    private Provider mProvider;
    private AppLockAuthHandler mAuthHandler;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProviderModule module = new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                providerRegistry.register(Handler.class,
                        () -> new Handler(Looper.getMainLooper()));
                providerRegistry.registerAsync(AppLockManager.class, AppLockManager::new);
            }
        };
        mProvider = Provider.createProvider(context, module);
        mAuthHandler = new AppLockAuthHandler(mProvider);
    }

    @After
    public void tearDown() {
        if (mProvider != null) {
            mProvider.dispose();
            mProvider = null;
        }
    }

    @Test
    public void initial_state_isNotPresenting() {
        assertFalse(mAuthHandler.isPresenting());
    }

    @Test
    public void cancel_onFreshHandler_isSafeNoOp() {
        // invariant: cancel must be safe with no active presentation, it
        // unconditionally bumps the presentation generation and clears the
        // presenting flag (see AppLockAuthHandler.cancel()), and production
        // calls cancel from teardown paths where double-cancel happens
        mAuthHandler.cancel();
        assertFalse(mAuthHandler.isPresenting());
        // second call proves idempotence, nothing must throw or flip state
        mAuthHandler.cancel();
        assertFalse(mAuthHandler.isPresenting());
    }

    @Test
    public void onCredentialResult_withoutPending_isSilentNoOp() {
        // invariant: with no pending credential launch the result must be
        // discarded silently (the one-shot null-pending guard), for both the
        // success and the user-backed-out code, onActivityResult can fire
        // without any prior keyguard launch when the activity is recycled
        mAuthHandler.onCredentialResult(Activity.RESULT_OK);
        assertFalse(mAuthHandler.isPresenting());
        // the backed-out code path must behave exactly like the success one
        mAuthHandler.onCredentialResult(Activity.RESULT_CANCELED);
        assertFalse(mAuthHandler.isPresenting());
    }
}
