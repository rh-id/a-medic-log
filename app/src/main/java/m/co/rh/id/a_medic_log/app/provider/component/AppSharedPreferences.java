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
    private static final boolean DEFAULT_DYNAMIC_COLORS_ENABLED = true;
    private static final boolean DEFAULT_APP_LOCK_ENABLED = false;
    private static final boolean DEFAULT_NOTIFICATION_PERMISSION_ASKED = false;
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<Handler> mHandler;
    private SharedPreferences mSharedPreferences;

    private int mSelectedTheme;
    private String mSelectedThemeKey;

    private boolean mDynamicColorsEnabled;
    private String mDynamicColorsEnabledKey;

    private boolean mAppLockEnabled;
    private String mAppLockEnabledKey;

    private boolean mNotificationPermissionAsked;
    private String mNotificationPermissionAskedKey;

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

        mDynamicColorsEnabledKey = SHARED_PREFERENCES_NAME
                + ".dynamicColorsEnabled";
        mDynamicColorsEnabled = mSharedPreferences.getBoolean(
                mDynamicColorsEnabledKey, DEFAULT_DYNAMIC_COLORS_ENABLED);

        mAppLockEnabledKey = SHARED_PREFERENCES_NAME
                + ".appLockEnabled";
        mAppLockEnabled = mSharedPreferences.getBoolean(
                mAppLockEnabledKey, DEFAULT_APP_LOCK_ENABLED);

        mNotificationPermissionAskedKey = SHARED_PREFERENCES_NAME
                + ".notificationPermissionAsked";
        mNotificationPermissionAsked = mSharedPreferences.getBoolean(
                mNotificationPermissionAskedKey, DEFAULT_NOTIFICATION_PERMISSION_ASKED);
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

    private void dynamicColorsEnabled(boolean enabled) {
        mDynamicColorsEnabled = enabled;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mDynamicColorsEnabledKey, enabled)
                        .commit());
    }

    public void setDynamicColorsEnabled(boolean enabled) {
        dynamicColorsEnabled(enabled);
    }

    public boolean isDynamicColorsEnabled() {
        return mDynamicColorsEnabled;
    }

    private void appLockEnabled(boolean enabled) {
        mAppLockEnabled = enabled;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mAppLockEnabledKey, enabled)
                        .commit());
    }

    public void setAppLockEnabled(boolean enabled) {
        appLockEnabled(enabled);
    }

    public boolean isAppLockEnabled() {
        return mAppLockEnabled;
    }

    private void notificationPermissionAsked(boolean asked) {
        mNotificationPermissionAsked = asked;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mNotificationPermissionAskedKey, asked)
                        .commit());
    }

    public void setNotificationPermissionAsked(boolean asked) {
        notificationPermissionAsked(asked);
    }

    public boolean isNotificationPermissionAsked() {
        return mNotificationPermissionAsked;
    }
}
