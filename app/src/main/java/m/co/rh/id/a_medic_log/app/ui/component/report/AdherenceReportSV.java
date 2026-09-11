package m.co.rh.id.a_medic_log.app.ui.component.report;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.AdherenceReport;
import m.co.rh.id.a_medic_log.app.provider.command.GetAdherenceReportCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.page.ProfileSelectPage;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AdherenceReportSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider> {
    private static final String TAG = AdherenceReportSV.class.getName();
    public static final int RANGE_7_DAYS = 7;
    public static final int RANGE_30_DAYS = 30;

    private long mProfileId;
    private int mRangeDays;
    // component
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient ILogger mLogger;
    private transient ProviderValue<GetAdherenceReportCmd> mGetAdherenceReportCmd;
    private transient ProviderValue<ProfileDao> mProfileDao;
    private transient MedicineIntakeChangeNotifier mMedicineIntakeChangeNotifier;
    private transient MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private transient MedicineChangeNotifier mMedicineChangeNotifier;
    private transient ProfileChangeNotifier mProfileChangeNotifier;
    private transient AdherenceReportAdapter mAdherenceReportAdapter;
    // View related
    private transient LinearLayout mContainerProfilePicker;
    private transient MaterialButton mButtonSelectProfile;
    private transient MaterialTextView mTextAdherencePercent;
    private transient MaterialTextView mTextAdherenceTakenMissed;
    private transient AdherenceBarChartView mAdherenceBarChartView;
    private transient MaterialTextView mTextAdherenceNoData;

    public AdherenceReportSV() {
        mRangeDays = RANGE_7_DAYS;
    }

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
        mGetAdherenceReportCmd = mSvProvider.lazyGet(GetAdherenceReportCmd.class);
        mProfileDao = mSvProvider.lazyGet(ProfileDao.class);
        mMedicineIntakeChangeNotifier = mSvProvider.get(MedicineIntakeChangeNotifier.class);
        mMedicineReminderChangeNotifier = mSvProvider.get(MedicineReminderChangeNotifier.class);
        mMedicineChangeNotifier = mSvProvider.get(MedicineChangeNotifier.class);
        mProfileChangeNotifier = mSvProvider.get(ProfileChangeNotifier.class);
        mAdherenceReportAdapter = new AdherenceReportAdapter();
        loadProfiles();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.view_adherence_report, container, false);
        mTextAdherencePercent = rootLayout.findViewById(R.id.text_adherence_percent);
        mTextAdherenceTakenMissed = rootLayout.findViewById(R.id.text_adherence_taken_missed);
        mAdherenceBarChartView = rootLayout.findViewById(R.id.view_adherence_chart);
        mTextAdherenceNoData = rootLayout.findViewById(R.id.text_adherence_no_data);
        mContainerProfilePicker = rootLayout.findViewById(R.id.container_profile_picker);
        mButtonSelectProfile = rootLayout.findViewById(R.id.button_select_profile);
        mButtonSelectProfile.setOnClickListener(view -> showProfileSelectPage());
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mAdherenceReportAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity,
                DividerItemDecoration.VERTICAL));
        MaterialButtonToggleGroup toggleGroupRange =
                rootLayout.findViewById(R.id.toggle_group_range);
        // sync the toggle with the persisted range before registering the
        // listener so the restore itself does not trigger a reload
        toggleGroupRange.check(mRangeDays == RANGE_30_DAYS
                ? R.id.toggle_30_days : R.id.toggle_7_days);
        toggleGroupRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int newRangeDays = checkedId == R.id.toggle_30_days
                    ? RANGE_30_DAYS : RANGE_7_DAYS;
            if (newRangeDays != mRangeDays) {
                mRangeDays = newRangeDays;
                load();
            }
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
                                .subscribe(profile -> loadProfiles()));
        return rootLayout;
    }

    private void showProfileSelectPage() {
        mNavigator.push(Routes.PROFILE_SELECT_PAGE,
                (navigator, navRoute, activity, currentView) -> {
                    ProfileSelectPage.Result result = ProfileSelectPage.Result.of(navRoute);
                    if (result == null) {
                        return;
                    }
                    Profile profile = result.getSelectedProfile().get(0);
                    onProfileSelected(profile);
                });
    }

    private void load() {
        mRxDisposer
                .add("AdherenceReportSV_load",
                        mGetAdherenceReportCmd.get().execute(mProfileId, mRangeDays)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((adherenceReport, throwable) -> {
                                    if (throwable != null) {
                                        RxUtils.logError(mLogger, TAG, throwable);
                                        return;
                                    }
                                    updateReport(adherenceReport);
                                }));
    }

    /**
     * Loads the profiles for the profile picker, the first profile is selected by default
     */
    private void loadProfiles() {
        mRxDisposer
                .add("AdherenceReportSV_loadProfiles",
                        Single.fromCallable(() -> mProfileDao.get().findProfiles())
                                .subscribeOn(Schedulers.from(mExecutorService))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((profiles, throwable) -> {
                                    if (throwable != null) {
                                        RxUtils.logError(mLogger, TAG, throwable);
                                        return;
                                    }
                                    updateProfiles(profiles);
                                }));
    }

    private void updateProfiles(List<Profile> profiles) {
        if (mSvProvider == null || mContainerProfilePicker == null) {
            return;
        }
        Profile selectedProfile = null;
        if (profiles != null) {
            for (Profile profile : profiles) {
                if (profile.id != null && profile.id == mProfileId) {
                    selectedProfile = profile;
                    break;
                }
            }
            if (selectedProfile == null && !profiles.isEmpty()) {
                // fall back to the first profile when the selected profile is missing
                selectedProfile = profiles.get(0);
            }
            if (selectedProfile != null) {
                mProfileId = selectedProfile.id == null ? 0 : selectedProfile.id;
            }
        }
        boolean hasProfiles = selectedProfile != null;
        mContainerProfilePicker.setVisibility(hasProfiles ? View.VISIBLE : View.GONE);
        if (hasProfiles) {
            mButtonSelectProfile.setText(selectedProfile.name);
        }
        load();
    }

    private void onProfileSelected(Profile profile) {
        if (profile == null) {
            return;
        }
        mProfileId = profile.id == null ? 0 : profile.id;
        if (mButtonSelectProfile != null) {
            mButtonSelectProfile.setText(profile.name);
        }
        load();
    }

    private void updateReport(AdherenceReport adherenceReport) {
        if (mSvProvider == null || mTextAdherencePercent == null) {
            return;
        }
        Context context = mSvProvider.getContext();
        mTextAdherencePercent.setText(context.getString(R.string.adherence_percent,
                adherenceReport.adherencePercent));
        mTextAdherenceTakenMissed.setText(context.getString(R.string.adherence_taken_missed_format,
                context.getString(R.string.adherence_taken), adherenceReport.totalTaken,
                context.getString(R.string.adherence_missed), adherenceReport.totalMissed));
        mAdherenceBarChartView.setData(adherenceReport.daily);
        mAdherenceReportAdapter.setItems(adherenceReport.perMedicine);
        mTextAdherenceNoData.setVisibility(
                adherenceReport.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mTextAdherencePercent = null;
        mTextAdherenceTakenMissed = null;
        mAdherenceBarChartView = null;
        mTextAdherenceNoData = null;
        mContainerProfilePicker = null;
        mButtonSelectProfile = null;
        mAdherenceReportAdapter = null;
    }
}
