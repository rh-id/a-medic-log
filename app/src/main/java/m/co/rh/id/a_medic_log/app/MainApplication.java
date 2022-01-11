package m.co.rh.id.a_medic_log.app;

import android.app.Activity;
import android.content.Context;

import androidx.multidex.MultiDex;

import m.co.rh.id.a_medic_log.app.provider.AppProviderModule;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class MainApplication extends BaseApplication {

    private Provider mProvider;

    @Override
    public void onCreate() {
        super.onCreate();
        mProvider = Provider.createProvider(this, new AppProviderModule(this));
        final Thread.UncaughtExceptionHandler defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            mProvider.get(ILogger.class)
                    .e("MainApplication", "App crash: " + throwable.getMessage(), throwable);
            mProvider.dispose();
            if (defaultExceptionHandler != null) {
                defaultExceptionHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(99);
            }
        });
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public Provider getProvider() {
        return mProvider;
    }

    public INavigator getNavigator(Activity activity) {
        if (activity instanceof MainActivity) {
            return mProvider.get(INavigator.class);
        }
        return null;
    }
}
