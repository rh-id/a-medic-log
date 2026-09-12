package m.co.rh.id.a_medic_log.app.ui.component.report;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.textview.MaterialTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.GetTodayDoseStatusCmd;
import m.co.rh.id.a_medic_log.app.provider.command.TodayStatus;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.page.NotesPage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Home card that shows today's dose status per profile
 * (missed doses first, then in-progress, then done profiles).
 */
public class TodayCardSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider> {
    private static final String TAG = TodayCardSV.class.getName();
    private static final int MAX_COLLAPSED_ROWS = 4;
    private static final String TIME_FORMAT = "HH:mm";

    private boolean mExpanded;
    private transient List<TodayStatus> mTodayStatusList;
    // component
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient ILogger mLogger;
    private transient ProviderValue<GetTodayDoseStatusCmd> mGetTodayDoseStatusCmd;
    private transient MedicineIntakeChangeNotifier mMedicineIntakeChangeNotifier;
    private transient MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private transient MedicineChangeNotifier mMedicineChangeNotifier;
    private transient ProfileChangeNotifier mProfileChangeNotifier;
    // View related
    private transient ViewGroup mHomeContent;
    private transient View mCardView;
    private transient LinearLayout mContainerTodayRows;
    private transient MaterialTextView mTextTodayToggle;
    private transient List<View> mRowViews;
    private transient SimpleDateFormat mTimeFormat;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mLogger = mSvProvider.get(ILogger.class);
        mGetTodayDoseStatusCmd = mSvProvider.lazyGet(GetTodayDoseStatusCmd.class);
        mMedicineIntakeChangeNotifier = mSvProvider.get(MedicineIntakeChangeNotifier.class);
        mMedicineReminderChangeNotifier = mSvProvider.get(MedicineReminderChangeNotifier.class);
        mMedicineChangeNotifier = mSvProvider.get(MedicineChangeNotifier.class);
        mProfileChangeNotifier = mSvProvider.get(ProfileChangeNotifier.class);
        load();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater()
                .inflate(R.layout.view_today_card, container, false);
        mCardView = rootLayout;
        mHomeContent = container.findViewById(R.id.container_home_content);
        mContainerTodayRows = rootLayout.findViewById(R.id.container_today_rows);
        mTextTodayToggle = rootLayout.findViewById(R.id.text_today_toggle);
        mRowViews = new ArrayList<>();
        mTimeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());
        mTextTodayToggle.setOnClickListener(view -> {
            if (mHomeContent != null) {
                TransitionManager.beginDelayedTransition(mHomeContent, new AutoTransition().setDuration(250));
            }
            mExpanded = !mExpanded;
            applyExpansionDelta();
        });
        mRxDisposer
                .add("createView_onMedicineIntakeChanged",
                        mMedicineIntakeChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(medicineIntake -> load()));
        mRxDisposer
                .add("createView_onMedicineReminderChanged",
                        mMedicineReminderChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(medicineReminder -> load()));
        mRxDisposer
                .add("createView_onMedicineChanged",
                        mMedicineChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(medicineState -> load()));
        mRxDisposer
                .add("createView_onProfileChanged",
                        mProfileChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(profile -> load()));
        return rootLayout;
    }

    private void load() {
        mRxDisposer
                .add("TodayCardSV_load",
                        mGetTodayDoseStatusCmd.get().execute()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(this::renderList,
                                        throwable -> RxUtils.logError(mLogger, TAG, throwable)));
    }

    private void renderList(List<TodayStatus> todayStatusList) {
        if (mSvProvider == null || mCardView == null
                || mContainerTodayRows == null || mTextTodayToggle == null) {
            return;
        }
        mTodayStatusList = todayStatusList;
        if (todayStatusList == null || todayStatusList.isEmpty()) {
            mCardView.setVisibility(View.GONE);
            return;
        }
        mCardView.setVisibility(View.VISIBLE);
        mContainerTodayRows.removeAllViews();
        mRowViews.clear();
        int maxRows = mExpanded ? todayStatusList.size() : MAX_COLLAPSED_ROWS;
        int rowCount = Math.min(todayStatusList.size(), maxRows);
        for (int i = 0; i < rowCount; i++) {
            View rowView = createRowView(todayStatusList.get(i));
            mContainerTodayRows.addView(rowView);
            mRowViews.add(rowView);
        }
        updateToggleFooter();
    }

    /**
     * Toggles the expanded/collapsed state by only adding or removing the rows
     * beyond {@link #MAX_COLLAPSED_ROWS}, leaving the existing rows untouched.
     */
    private void applyExpansionDelta() {
        if (mSvProvider == null || mContainerTodayRows == null || mTextTodayToggle == null) {
            return;
        }
        if (mTodayStatusList == null || mTodayStatusList.size() <= MAX_COLLAPSED_ROWS) {
            updateToggleFooter();
            return;
        }
        if (mExpanded) {
            for (int i = MAX_COLLAPSED_ROWS; i < mTodayStatusList.size(); i++) {
                View rowView = createRowView(mTodayStatusList.get(i));
                mContainerTodayRows.addView(rowView);
                mRowViews.add(rowView);
            }
        } else {
            for (int i = mRowViews.size() - 1; i >= MAX_COLLAPSED_ROWS; i--) {
                mContainerTodayRows.removeView(mRowViews.get(i));
            }
            mRowViews.subList(MAX_COLLAPSED_ROWS, mRowViews.size()).clear();
        }
        updateToggleFooter();
    }

    private void updateToggleFooter() {
        if (mTodayStatusList == null || mContainerTodayRows == null || mTextTodayToggle == null) {
            return;
        }
        Context context = mContainerTodayRows.getContext();
        boolean toggleVisible = mTodayStatusList.size() > MAX_COLLAPSED_ROWS;
        mTextTodayToggle.setVisibility(toggleVisible ? View.VISIBLE : View.GONE);
        if (toggleVisible) {
            if (mExpanded) {
                mTextTodayToggle.setText(R.string.today_show_less);
            } else {
                mTextTodayToggle.setText(context.getString(R.string.today_more_profiles,
                        mTodayStatusList.size() - MAX_COLLAPSED_ROWS));
            }
        }
    }

    private View createRowView(TodayStatus status) {
        Context context = mContainerTodayRows.getContext();
        View rowView = LayoutInflater.from(context)
                .inflate(R.layout.item_today_profile, mContainerTodayRows, false);
        View dotView = rowView.findViewById(R.id.view_today_dot);
        MaterialTextView textName = rowView.findViewById(R.id.text_today_name);
        MaterialTextView textCounts = rowView.findViewById(R.id.text_today_counts);
        MaterialTextView textDetail = rowView.findViewById(R.id.text_today_detail);
        int colorRes;
        if (status.status == TodayStatus.STATUS_MISSED) {
            colorRes = R.color.adherence_missed;
        } else if (status.status == TodayStatus.STATUS_IN_PROGRESS) {
            colorRes = R.color.adherence_taken;
        } else {
            colorRes = R.color.today_done;
        }
        ((GradientDrawable) dotView.getBackground().mutate())
                .setColor(ContextCompat.getColor(context, colorRes));
        textName.setText(status.profileName);
        textCounts.setText(context.getString(R.string.today_doses_format,
                status.takenDoses, status.totalDoses));
        if (status.status == TodayStatus.STATUS_MISSED) {
            textDetail.setText(context.getString(R.string.today_missed_format,
                    status.missedMedicineName, mTimeFormat.format(status.missedDoseTime)));
        } else if (status.status == TodayStatus.STATUS_IN_PROGRESS) {
            textDetail.setText(context.getString(R.string.today_next_dose_format,
                    mTimeFormat.format(status.nextDoseTime)));
        } else {
            textDetail.setText(R.string.today_all_done);
        }
        rowView.setOnClickListener(view -> mNavigator.push(Routes.NOTES_PAGE,
                NotesPage.Args.withProfileId(status.profileId)));
        return rowView;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mCardView = null;
        mContainerTodayRows = null;
        mTextTodayToggle = null;
        if (mRowViews != null) {
            mRowViews.clear();
            mRowViews = null;
        }
        mTimeFormat = null;
        mTodayStatusList = null;
    }
}
