package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.NewProfileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateProfileCmd;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.util.SimpleTextWatcher;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class ProfileDetailPage extends BaseDetailPage implements Toolbar.OnMenuItemClickListener {

    private static final String TAG = ProfileDetailPage.class.getName();
    @NavInject
    private AppBarSV mAppBarSV;

    private SerialBehaviorSubject<Profile> mProfileSubject;
    private transient NewProfileCmd mNewProfileCmd;
    private transient TextWatcher mNameTextWatcher;
    private transient TextWatcher mAboutTextWatcher;

    public ProfileDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_profile_detail);
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Profile profile = getProfile();
        if (profile == null) {
            profile = new Profile();
        }
        mProfileSubject = new SerialBehaviorSubject<>(profile);
    }

    @Override
    protected void onProvideComponent(Provider provider) {
        if (isUpdate()) {
            mNewProfileCmd = mSvProvider.get(UpdateProfileCmd.class);
        } else {
            mNewProfileCmd = mSvProvider.get(NewProfileCmd.class);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        initTextWatcher();
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.page_profile_detail, container, false);
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_profile));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_add_profile));
        }
        mAppBarSV.setMenuItemListener(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        EditText nameInput = rootLayout.findViewById(R.id.input_text_name);
        nameInput.setText(mProfileSubject.getValue().name);
        nameInput.addTextChangedListener(mNameTextWatcher);
        EditText aboutInput = rootLayout.findViewById(R.id.input_text_about);
        aboutInput.setText(mProfileSubject.getValue().about);
        aboutInput.addTextChangedListener(mAboutTextWatcher);
        mRxDisposer.add("ProfileDetailPage.createView_onNameValidation",
                mNewProfileCmd.getNameValid()
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(error -> {
                    if (error != null && !error.isEmpty()) {
                        nameInput.setError(error);
                    } else {
                        nameInput.setError(null);
                    }
                }));
        return rootLayout;
    }

    @Override
    protected void onPageDispose(Activity activity) {
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        mNameTextWatcher = null;
        mAboutTextWatcher = null;
    }

    private void initTextWatcher() {
        if (mNameTextWatcher == null) {
            mNameTextWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    mProfileSubject.getValue().name = editable.toString();
                    mNewProfileCmd.valid(mProfileSubject.getValue());
                }
            };
        }
        if (mAboutTextWatcher == null) {
            mAboutTextWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    mProfileSubject.getValue().about = editable.toString();
                    mNewProfileCmd.valid(mProfileSubject.getValue());
                }
            };
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            Profile editedProfile = mProfileSubject.getValue();
            if (mNewProfileCmd.valid(editedProfile)) {
                Context context = mSvProvider.getContext();
                boolean isUpdate = isUpdate();
                mRxDisposer.add("ProfileDetailPage.onMenuItemClick_newProfileCmd.execute",
                        mNewProfileCmd.execute(editedProfile)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((profile, throwable) -> {
                                            String errorMessage;
                                            String successMessage;
                                            if (isUpdate) {
                                                errorMessage = context.getString(R.string.error_failed_to_update_profile);
                                                successMessage = context.getString(R.string.success_updating_profile, editedProfile.name);
                                            } else {
                                                errorMessage = context.getString(R.string.error_failed_to_add_profile);
                                                successMessage = context.getString(R.string.success_adding_new_profile, editedProfile.name);
                                            }
                                            if (throwable != null) {
                                                mLogger.e(TAG, errorMessage, throwable);
                                            } else {
                                                mLogger.i(TAG, successMessage);
                                                mNavigator.pop(Result.withProfile(profile));
                                            }
                                        }));
            } else {
                String error = mNewProfileCmd.getValidationError();
                mLogger.i(TAG, error);
            }
        }
        return false;
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.isUpdate();
        }
        return false;
    }

    private Profile getProfile() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.getProfile();
        }
        return null;
    }

    public static class Result implements Serializable {
        private static Result withProfile(Profile profile) {
            Result result = new Result();
            result.mProfile = profile;
            return result;
        }

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private Profile mProfile;

        public Profile getProfile() {
            return mProfile;
        }
    }

    public static class Args implements Serializable {
        public static Args forUpdate(Profile profile) {
            Args args = new Args();
            args.mProfile = profile;
            args.mOperation = 1;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private Profile mProfile;
        private byte mOperation;

        private Args() {
        }

        public Profile getProfile() {
            return mProfile;
        }

        public boolean isUpdate() {
            return mOperation == 1;
        }
    }
}
