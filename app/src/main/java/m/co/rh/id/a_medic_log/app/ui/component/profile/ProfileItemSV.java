package m.co.rh.id.a_medic_log.app.ui.component.profile;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteProfileCmd;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.page.NoteListPage;
import m.co.rh.id.a_medic_log.app.ui.page.ProfileDetailPage;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ProfileItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = ProfileItemSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private Profile mProfile;
    private transient BehaviorSubject<Profile> mProfileSubject;

    private ListMode mListMode;
    private boolean mIsSelected;
    private transient CompoundButton mSelectedUiButton;
    private transient OnItemSelectListener mOnItemSelectListener;

    public ProfileItemSV() {
    }

    public ProfileItemSV(ListMode listMode) {
        mListMode = listMode;
    }

    @Override
    public void provideComponent(Provider provider) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = provider.get(StatefulViewProvider.class);
        initProfileSubject();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.item_profile, container, false);
        rootLayout.setOnClickListener(this);
        RadioButton radioSelect = rootLayout.findViewById(R.id.radio_select);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        Button buttonDelete = rootLayout.findViewById(R.id.button_delete);
        buttonEdit.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        if (mListMode != null) {
            buttonEdit.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);
            if (mListMode.mSelectMode == ListMode.SELECT_MODE) {
                radioSelect.setVisibility(View.VISIBLE);
                radioSelect.setChecked(mIsSelected);
                mSelectedUiButton = radioSelect;
            }
        } else {
            buttonEdit.setVisibility(View.VISIBLE);
            buttonDelete.setVisibility(View.VISIBLE);
        }
        TextView textName = rootLayout.findViewById(R.id.text_name);
        TextView textAbout = rootLayout.findViewById(R.id.text_about);
        mSvProvider.get(RxDisposer.class).add("createView_onChangeProfile",
                mProfileSubject.observeOn(AndroidSchedulers.mainThread())
                        .subscribe(profile -> {
                            textName.setText(profile.name);
                            textAbout.setText(profile.about);
                        }));
        return rootLayout;
    }

    private void initProfileSubject() {
        if (mProfileSubject == null) {
            if (mProfile != null) {
                mProfileSubject = BehaviorSubject.createDefault(mProfile);
            } else {
                mProfileSubject = BehaviorSubject.create();
            }
        } else {
            mProfileSubject.onNext(mProfile);
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mProfileSubject != null) {
            mProfileSubject.onComplete();
            mProfileSubject = null;
        }
        mProfile = null;
        mSelectedUiButton = null;
        mOnItemSelectListener = null;
    }

    public void setProfile(Profile profile) {
        mProfile = profile;
        initProfileSubject();
    }

    public Profile getProfile() {
        return mProfile;
    }

    public void setOnSelectListener(OnItemSelectListener onItemSelectListener) {
        mOnItemSelectListener = onItemSelectListener;
    }

    public void select() {
        mIsSelected = true;
        if (mSelectedUiButton != null) {
            mSelectedUiButton.setChecked(true);
        }
    }

    public void unSelect() {
        mIsSelected = false;
        if (mSelectedUiButton != null) {
            mSelectedUiButton.setChecked(false);
        }
    }


    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            if (mListMode != null) {
                mIsSelected = true;
                if (mSelectedUiButton != null) {
                    mSelectedUiButton.setChecked(mIsSelected);
                }
                if (mOnItemSelectListener != null) {
                    mOnItemSelectListener.onItemSelect(mProfile, mIsSelected);
                }
            } else {
                mNavigator.push(Routes.NOTES_PAGE,
                        NoteListPage.Args.withProfileId(mProfile.id));
            }
        } else if (id == R.id.button_edit) {
            mNavigator.push(Routes.PROFILE_DETAIL_PAGE, ProfileDetailPage.Args.forUpdate(mProfile.clone()),
                    (navigator, navRoute, activity, currentView) -> {
                        ProfileDetailPage.Result result = ProfileDetailPage.Result.of(navRoute.getRouteResult());
                        if (result != null) {
                            setProfile(result.getProfile());
                        }
                    });
        } else if (id == R.id.button_delete) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_profile, mProfile.name);
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.getRoutePath(NavExtDialogConfig.ROUTE_CONFIRM),
                    navExtDialogConfig.args_confirmDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        Boolean result = provider.get(NavExtDialogConfig.class).result_confirmDialog(navRoute);
                        if (result != null && result) {
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(DeleteProfileCmd.class)
                                    .execute(mProfile)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((card, throwable) -> {
                                        Context deleteContext = provider.getContext();
                                        if (throwable != null) {
                                            provider.get(ILogger.class)
                                                    .e(TAG,
                                                            deleteContext.getString(
                                                                    R.string.error_deleting_profile),
                                                            throwable);
                                        } else {
                                            provider.get(ILogger.class)
                                                    .i(TAG,
                                                            deleteContext.getString(
                                                                    R.string.success_deleting_profile, card.name));
                                        }
                                        compositeDisposable.dispose();
                                    })
                            );
                        }
                    });
        }
    }

    /**
     * Interface to handle if this profile is selected or not event
     */
    public interface OnItemSelectListener {
        void onItemSelect(Profile profile, boolean selected);
    }

    public static class ListMode implements Serializable {
        public static ListMode selectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = SELECT_MODE;
            return listMode;
        }

        /**
         * Selection with radio button or only one selection
         */
        private static final byte SELECT_MODE = 0;

        private byte mSelectMode;

        private ListMode() {
        }

        public int getSelectMode() {
            return mSelectMode;
        }
    }
}
