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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.NewProfileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateProfileCmd;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

public class ProfileDetailPage extends StatefulView<Activity> implements RequireNavRoute, RequireComponent<Provider>, Toolbar.OnMenuItemClickListener {

    private static final String TAG = ProfileDetailPage.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    @NavInject
    private AppBarSV mAppBarSv;

    private Profile mProfile;
    private transient Provider mSvProvider;
    private transient NewProfileCmd mNewProfileCmd;
    private transient TextWatcher mNameTextWatcher;
    private transient TextWatcher mAboutTextWatcher;

    public ProfileDetailPage() {
        mAppBarSv = new AppBarSV(R.menu.page_profile_detail);
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = provider.get(StatefulViewProvider.class);
        if (isUpdate()) {
            mNewProfileCmd = mSvProvider.get(UpdateProfileCmd.class);
            if (mProfile == null) {
                mProfile = getProfile();
            }
        } else {
            mNewProfileCmd = mSvProvider.get(NewProfileCmd.class);
            if (mProfile == null) {
                mProfile = new Profile();
            }
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        initTextWatcher();
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.page_profile_detail, container, false);
        if (isUpdate()) {
            mAppBarSv.setTitle(activity.getString(R.string.title_update_profile));
        } else {
            mAppBarSv.setTitle(activity.getString(R.string.title_add_profile));
        }
        mAppBarSv.setMenuItemListener(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSv.buildView(activity, appBarContainer));
        EditText nameInput = rootLayout.findViewById(R.id.input_text_name);
        nameInput.setText(mProfile.name);
        nameInput.addTextChangedListener(mNameTextWatcher);
        EditText aboutInput = rootLayout.findViewById(R.id.input_text_about);
        aboutInput.setText(mProfile.about);
        aboutInput.addTextChangedListener(mAboutTextWatcher);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onNameValidation",
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
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNameTextWatcher = null;
        mAboutTextWatcher = null;
    }

    private void initTextWatcher() {
        if (mNameTextWatcher == null) {
            mNameTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mProfile.name = editable.toString();
                    mNewProfileCmd.valid(mProfile);
                }
            };
        }
        if (mAboutTextWatcher == null) {
            mAboutTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mProfile.about = editable.toString();
                    mNewProfileCmd.valid(mProfile);
                }
            };
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (mNewProfileCmd.valid(mProfile)) {
                Context context = mSvProvider.getContext();
                mSvProvider.get(RxDisposer.class)
                        .add("onMenuItemClick_newProfileCmd.execute",
                                mNewProfileCmd.execute(mProfile)
                                        .subscribe((profile, throwable) -> {
                                            String errorMessage;
                                            String successMessage;
                                            if (isUpdate()) {
                                                errorMessage = context.getString(R.string.error_failed_to_update_profile);
                                                successMessage = context.getString(R.string.success_updating_new_profile, mProfile.name);
                                            } else {
                                                errorMessage = context.getString(R.string.error_failed_to_add_profile);
                                                successMessage = context.getString(R.string.success_adding_new_profile, mProfile.name);
                                            }
                                            if (throwable != null) {
                                                mSvProvider.get(ILogger.class)
                                                        .e(TAG, errorMessage, throwable);
                                                mNavigator.pop();
                                            } else {
                                                mSvProvider.get(ILogger.class)
                                                        .i(TAG, successMessage);
                                                mNavigator.pop(Result.withProfile(profile));
                                            }
                                        }));
            } else {
                String error = mNewProfileCmd.getValidationError();
                mSvProvider.get(ILogger.class).i(TAG, error);
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
