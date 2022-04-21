package m.co.rh.id.a_medic_log.app.provider.component;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppSharedPreferences {
    private static final String SHARED_PREFERENCES_NAME = "AppSharedPreferences";
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<Handler> mHandler;
    private SharedPreferences mSharedPreferences;

    private int mSelectedTheme;
    private String mSelectedThemeKey;

    public AppSharedPreferences(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        initValue();
    }

    private void initValue() {
        mSelectedThemeKey = SHARED_PREFERENCES_NAME
                + ".selectedTheme";

        int selectedTheme = mSharedPreferences.getInt(
                mSelectedThemeKey,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setSelectedTheme(selectedTheme);
    }

    private void selectedTheme(int setting) {
        mSelectedTheme = setting;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putInt(mSelectedThemeKey, setting)
                        .commit());
    }

    public void setSelectedTheme(int setting) {
        selectedTheme(setting);
        mHandler.get().post(() ->
                AppCompatDelegate.setDefaultNightMode(setting));
    }

    public int getSelectedTheme() {
        return mSelectedTheme;
    }
}
