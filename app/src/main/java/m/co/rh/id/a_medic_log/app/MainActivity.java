package m.co.rh.id.a_medic_log.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.RxProviderModule;
import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
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
        mRebuildUi = BehaviorSubject.create();
        // rebuild UI is expensive and error prone, avoid spam rebuild (especially due to day and night mode)
        mActivityProvider.get(RxDisposer.class)
                .add("rebuildUI", mRebuildUi.debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                BaseApplication.of(this).getNavigator(this).reBuildAllRoute();
                                // Switching to night mode didn't update window background for some reason?
                                // seemed to occur on android 8 and below
                                getWindow().setBackgroundDrawableResource(R.color.daynight_white_black);
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