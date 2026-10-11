package m.co.rh.id.a_medic_log.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.RxProviderModule;
import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.app.provider.component.AppSharedPreferences;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.aprovider.Provider;

public class MainActivity extends AppCompatActivity {

    private BehaviorSubject<Boolean> mRebuildUi;
    private Provider mActivityProvider;
    private AppNotificationHandler mAppNotificationHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mActivityProvider = Provider.createNestedProvider("ActivityProvider",
                BaseApplication.of(this).getProvider(), getApplicationContext()
                , new RxProviderModule());
        mAppNotificationHandler = mActivityProvider.get(AppNotificationHandler.class);
        // apply Material You dynamic colors before super.onCreate installs
        // the theme; gated on both the user setting and device support
        if (mActivityProvider.get(AppSharedPreferences.class).isDynamicColorsEnabled()
                && DynamicColors.isDynamicColorAvailable()) {
            getTheme().applyStyle(R.style.Theme_Amediclog_Overlay_DynamicColors, true);
        }
        mRebuildUi = BehaviorSubject.create();
        // rebuild UI is expensive and error prone, avoid spam rebuild (especially due to day and night mode)
        mActivityProvider.get(RxDisposer.class)
                .add("rebuildUI", mRebuildUi.debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                // re-evaluate the gate at each rebuild event, the setting can change
                                // while the activity is alive
                                boolean dynamicColorsActive = mActivityProvider.get(AppSharedPreferences.class)
                                        .isDynamicColorsEnabled()
                                        && DynamicColors.isDynamicColorAvailable();
                                if (dynamicColorsActive) {
                                    // the activity handles uiMode config changes without recreation,
                                    // so re-apply the overlay to re-resolve its DayNight variant
                                    // (light/dark) for the new configuration before views rebuild
                                    getTheme().applyStyle(R.style.Theme_Amediclog_Overlay_DynamicColors, true);
                                    // the window background drawable was instantiated under the previous
                                    // configuration and is never re-created by applyStyle, so re-resolve
                                    // it explicitly
                                    TypedValue windowBackground = new TypedValue();
                                    getTheme().resolveAttribute(android.R.attr.windowBackground, windowBackground, true);
                                    if (windowBackground.type >= TypedValue.TYPE_FIRST_COLOR_INT
                                            && windowBackground.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                                        getWindow().setBackgroundDrawable(new ColorDrawable(windowBackground.data));
                                    } else if (windowBackground.resourceId != 0) {
                                        getWindow().setBackgroundDrawableResource(windowBackground.resourceId);
                                    }
                                } else {
                                    // Switching to night mode didn't update window background for some reason?
                                    // seemed to occur on android 8 and below
                                    getWindow().setBackgroundDrawableResource(R.color.daynight_white_black);
                                    getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.daynight_status_bar_color));
                                }
                                BaseApplication.of(this).getNavigator(this).reBuildAllRoute();
                                // reBuildAllRoute re-installs the activity content view while the
                                // activity is running; the new hierarchy misses the initial inset
                                // dispatch, so request one explicitly or content lays out under the
                                // transparent status bar on Android 15+ (enforced edge-to-edge)
                                getWindow().getDecorView().requestApplyInsets();
                            }
                        })
                );
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        BaseApplication.of(MainActivity.this)
                                .getNavigator(MainActivity.this).onBackPressed();
                    }
                });
        mAppNotificationHandler.processNotification(getIntent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // let the system show the app icon in recents instead of a content snapshot
            setRecentsScreenshotEnabled(false);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPause() {
        // blank the app-switcher/recents snapshot so health data is not visible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mAppNotificationHandler.processNotification(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // this is required to let navigator handle onActivityResult
        BaseApplication.of(this).getNavigator(this).onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // using AppCompatDelegate.setDefaultNightMode trigger this method
        // but not triggering Application.onConfigurationChanged
        mRebuildUi.onNext(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mActivityProvider.dispose();
        mActivityProvider = null;
        mRebuildUi.onComplete();
        mRebuildUi = null;
    }
}