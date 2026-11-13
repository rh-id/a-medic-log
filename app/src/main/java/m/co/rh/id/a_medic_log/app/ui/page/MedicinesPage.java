package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.MedicineListSV;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Standalone "My Medicines" page, reachable from the
 * drawer without going through notes. V1 is view + manage only: medicines
 * stay created from the note editor.
 */
public class MedicinesPage extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, Toolbar.OnMenuItemClickListener {
    private static final String TAG = MedicinesPage.class.getName();

    private Long mSelectedProfileId;
    // component
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient ILogger mLogger;
    private transient ProviderValue<ProfileDao> mProfileDao;
    private transient ProfileChangeNotifier mProfileChangeNotifier;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private MedicineListSV mMedicineListSV;
    // View related
    private transient ViewGroup mContainerProfilePicker;
    private transient MaterialButton mButtonSelectProfile;
    private transient View mTextNoProfile;

    public MedicinesPage() {
        mAppBarSV = new AppBarSV(R.menu.page_medicines);
        mMedicineListSV = new MedicineListSV();
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
        mProfileDao = mSvProvider.lazyGet(ProfileDao.class);
        mProfileChangeNotifier = mSvProvider.get(ProfileChangeNotifier.class);
        loadProfiles();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_medicines, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_medicines));
        mAppBarSV.setMenuItemListener(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        mContainerProfilePicker = rootLayout.findViewById(R.id.container_profile_picker);
        mButtonSelectProfile = rootLayout.findViewById(R.id.button_select_profile);
        mButtonSelectProfile.setOnClickListener(view -> showProfileSelectPage());
        mTextNoProfile = rootLayout.findViewById(R.id.text_no_profile);
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mMedicineListSV.buildView(activity, rootLayout));
        mRxDisposer
                .add("createView_onProfileChanged",
                        mProfileChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(profileState -> loadProfiles()));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        return false;
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

    /**
     * Loads the profiles for the profile picker, the first profile is selected by default
     */
    private void loadProfiles() {
        mRxDisposer
                .add("MedicinesPage_loadProfiles",
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
                if (profile.id != null && profile.id.equals(mSelectedProfileId)) {
                    selectedProfile = profile;
                    break;
                }
            }
            if (selectedProfile == null && !profiles.isEmpty()) {
                // fall back to the first profile when the selected profile is missing
                selectedProfile = profiles.get(0);
            }
            if (selectedProfile != null) {
                mSelectedProfileId = selectedProfile.id;
                mButtonSelectProfile.setText(selectedProfile.name);
            }
        }
        boolean hasProfiles = selectedProfile != null;
        mContainerProfilePicker.setVisibility(hasProfiles ? View.VISIBLE : View.GONE);
        mTextNoProfile.setVisibility(hasProfiles ? View.GONE : View.VISIBLE);
        mMedicineListSV.setProfileId(hasProfiles ? mSelectedProfileId : null);
    }

    private void onProfileSelected(Profile profile) {
        if (profile == null) {
            return;
        }
        mSelectedProfileId = profile.id;
        if (mButtonSelectProfile != null) {
            mButtonSelectProfile.setText(profile.name);
        }
        mMedicineListSV.setProfileId(mSelectedProfileId);
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mMedicineListSV != null) {
            mMedicineListSV.dispose(activity);
            mMedicineListSV = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mContainerProfilePicker = null;
        mButtonSelectProfile = null;
        mTextNoProfile = null;
    }
}
