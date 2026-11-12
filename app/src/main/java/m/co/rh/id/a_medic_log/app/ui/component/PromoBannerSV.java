package m.co.rh.id.a_medic_log.app.ui.component;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.component.AppSharedPreferences;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Home card that promotes the companion app (Personal Stuff) for tracking
 * medicine stock. Shown only while the companion app is not installed, and
 * after a dismissal again at most every {@link #COMPANION_PROMO_RESHOW_MILLIS}.
 * The install check runs on this device only.
 */
public class PromoBannerSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = PromoBannerSV.class.getName();
    private static final String COMPANION_APP_PACKAGE_NAME = "m.co.rh.id.a_personal_stuff";
    private static final String COMPANION_APP_URL =
            "https://f-droid.org/packages/m.co.rh.id.a_personal_stuff/";
    private static final String COMPANION_APP_GITHUB_URL =
            "https://github.com/rh-id/a-personal-stuff/releases";
    private static final String CHECK_DISPOSABLE_KEY = "PromoBannerSV_check";
    public static final long COMPANION_PROMO_RESHOW_MILLIS = 14L * 24 * 60 * 60 * 1000;

    // last show decision, null until the first check completes
    private Boolean mLastShowDecision;
    // component
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient ILogger mLogger;
    private transient AppSharedPreferences mAppSharedPreferences;
    // View related
    private transient ViewGroup mHomeContent;
    private transient View mCardView;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mLogger = mSvProvider.get(ILogger.class);
        mAppSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater()
                .inflate(R.layout.view_companion_promo_card, container, false);
        mCardView = rootLayout;
        mHomeContent = container.findViewById(R.id.container_home_content);
        if (mLastShowDecision != null) {
            // apply the last decision before the async check, otherwise a
            // rebuilt view would flash the card in or out
            rootLayout.setVisibility(mLastShowDecision ? View.VISIBLE : View.GONE);
        }
        rootLayout.findViewById(R.id.button_view_companion_app).setOnClickListener(this);
        rootLayout.findViewById(R.id.button_view_companion_github).setOnClickListener(this);
        rootLayout.findViewById(R.id.button_dismiss_companion_promo).setOnClickListener(this);
        check();
        return rootLayout;
    }

    /**
     * Pure show decision, no Android calls, unit-testable on the JVM: never
     * while the companion app is installed, otherwise only when the card was
     * never dismissed or the dismissal is at least
     * {@link #COMPANION_PROMO_RESHOW_MILLIS} old.
     *
     * @param installed          whether the companion app is installed
     * @param dismissedAtEpochMs last dismissal time in epoch millis, 0 means never
     * @param nowEpochMs         current time in epoch millis
     */
    public static boolean shouldShow(boolean installed, long dismissedAtEpochMs, long nowEpochMs) {
        return !installed && (dismissedAtEpochMs == 0
                || nowEpochMs - dismissedAtEpochMs >= COMPANION_PROMO_RESHOW_MILLIS);
    }

    /**
     * Re-checks whether the card should show and renders the result. The
     * package lookup runs off the main thread and a newer check replaces a
     * pending one, cheap enough to call on every activity resume.
     */
    public void check() {
        if (mSvProvider == null) {
            return;
        }
        // read on the main thread, where the dismissal is written
        long dismissedAt = mAppSharedPreferences.getCompanionAppPromoDismissedAt();
        PackageManager packageManager = mSvProvider.getContext().getPackageManager();
        mRxDisposer
                .add(CHECK_DISPOSABLE_KEY,
                        Single.fromCallable(() -> shouldShow(isCompanionAppInstalled(packageManager),
                                        dismissedAt, System.currentTimeMillis()))
                                .subscribeOn(Schedulers.from(mExecutorService))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(this::render,
                                        throwable -> {
                                            // install state unknown, keep the card hidden.
                                            // Debug level only, info and above show a toast
                                            mLogger.d(TAG, "Failed to check companion app: "
                                                    + throwable.getMessage(), throwable);
                                            render(false);
                                        }));
    }

    /**
     * Hides the card right away, e.g. after the user dismissed it.
     */
    public void hide() {
        if (mRxDisposer != null) {
            // drop a check still in flight, its result predates this hide
            mRxDisposer.add(CHECK_DISPOSABLE_KEY, Disposable.disposed());
        }
        render(false);
    }

    private void render(boolean show) {
        mLastShowDecision = show;
        if (mCardView == null) {
            return;
        }
        int visibility = show ? View.VISIBLE : View.GONE;
        if (mCardView.getVisibility() == visibility) {
            return;
        }
        if (mHomeContent != null) {
            TransitionManager.beginDelayedTransition(mHomeContent, new AutoTransition().setDuration(250));
        }
        mCardView.setVisibility(visibility);
    }

    /**
     * Only a definite "not found" means not installed, any other failure is
     * thrown on so the check treats the install state as unknown.
     */
    @SuppressWarnings("deprecation")
    private static boolean isCompanionAppInstalled(PackageManager packageManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(COMPANION_APP_PACKAGE_NAME,
                        PackageManager.PackageInfoFlags.of(0));
            } else {
                packageManager.getPackageInfo(COMPANION_APP_PACKAGE_NAME, 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_view_companion_app) {
            openCompanionAppPage(mCardView.getContext(), COMPANION_APP_URL);
        } else if (id == R.id.button_view_companion_github) {
            openCompanionAppPage(mCardView.getContext(), COMPANION_APP_GITHUB_URL);
        } else if (id == R.id.button_dismiss_companion_promo) {
            dismissCompanionAppPromo();
        }
    }

    /**
     * Opens a companion app link, e.g. its F-Droid or GitHub releases page.
     */
    private void openCompanionAppPage(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            // no app can open the link, retrying the same intent would throw again
            mLogger.i(TAG, context.getString(R.string.error_no_app_to_open_link));
        }
    }

    private void dismissCompanionAppPromo() {
        mAppSharedPreferences.setCompanionAppPromoDismissedAt(System.currentTimeMillis());
        hide();
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mHomeContent = null;
        mCardView = null;
        mLastShowDecision = null;
    }
}
