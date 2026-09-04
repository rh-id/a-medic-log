package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.profile.ProfileListSV;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Full-screen page to select profile(s).
 * In {@link SelectionType#SINGLE} only one profile can be selected using a radio button,
 * in {@link SelectionType#MULTI} multiple profiles can be selected using checkboxes
 * and a select-all checkbox is provided.
 */
public class ProfileSelectPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private static final String TAG = ProfileSelectPage.class.getName();

    /**
     * Selection behaviour of this page
     */
    public enum SelectionType {
        /**
         * Only one profile can be selected using a radio button
         */
        SINGLE,
        /**
         * Multiple profiles can be selected using checkboxes
         */
        MULTI
    }

    @NavInject
    private transient INavigator mNavigator;

    private transient ILogger mLogger;
    private transient StatefulViewProvider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient CheckBox mCheckBoxSelectAll;

    @NavInject
    private AppBarSV mAppBarSV;

    @NavInject
    private ProfileListSV mProfileListSV;

    private SelectionType mSelectionType;

    public ProfileSelectPage() {
        this(SelectionType.SINGLE);
    }

    public ProfileSelectPage(SelectionType selectionType) {
        if (selectionType == null) {
            selectionType = SelectionType.SINGLE;
        }
        mSelectionType = selectionType;
        if (selectionType == SelectionType.MULTI) {
            mProfileListSV = new ProfileListSV(ProfileListSV.ListMode.multiSelectMode());
        } else {
            mProfileListSV = new ProfileListSV(ProfileListSV.ListMode.selectMode());
        }
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mLogger = provider.get(ILogger.class);
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_profile_select, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(mSelectionType == SelectionType.MULTI
                ? R.string.title_select_profiles : R.string.title_select_profile));
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        if (mSelectionType == SelectionType.MULTI) {
            containerContent.addView(createMultiSelectContent(activity, rootLayout));
        } else {
            containerContent.addView(mProfileListSV.buildView(activity, rootLayout));
        }
        Button buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        Button buttonOk = rootLayout.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(this);
        return rootLayout;
    }

    private View createMultiSelectContent(Activity activity, ViewGroup container) {
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        int margin = activity.getResources().getDimensionPixelSize(R.dimen.text_margin);
        LinearLayout.LayoutParams selectAllParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selectAllParams.setMargins(margin, margin, margin, 0);
        mCheckBoxSelectAll = new CheckBox(activity);
        mCheckBoxSelectAll.setText(R.string.select_all);
        // click is used instead of check change to react only to user interaction,
        // programmatic check updates from the selection flow must not re-trigger the selection
        mCheckBoxSelectAll.setOnClickListener(view -> {
            if (mCheckBoxSelectAll.isChecked()) {
                mProfileListSV.selectAll();
            } else {
                mProfileListSV.unSelectAll();
            }
        });
        linearLayout.addView(mCheckBoxSelectAll, selectAllParams);
        linearLayout.addView(mProfileListSV.buildView(activity, container));
        mRxDisposer.add("createView_onSelectionChanged",
                mProfileListSV.getSelectedIdsFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::updateSelectAllChecked));
        return linearLayout;
    }

    private void updateSelectAllChecked(Set<Long> selectedIds) {
        if (mCheckBoxSelectAll == null) {
            return;
        }
        // compare against the currently displayed items only, while a search is active
        // the selection may still hold ids of items hidden by the search filter
        ArrayList<Profile> displayedItems = mProfileListSV.getItems();
        boolean allSelected = !displayedItems.isEmpty();
        for (Profile displayedItem : displayedItems) {
            if (!selectedIds.contains(displayedItem.id)) {
                allSelected = false;
                break;
            }
        }
        if (mCheckBoxSelectAll.isChecked() != allSelected) {
            mCheckBoxSelectAll.setChecked(allSelected);
        }
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.button_cancel) {
            mNavigator.pop();
        } else if (viewId == R.id.button_ok) {
            // resolve the selection through the database so profiles hidden by
            // an active search filter are still included in the result
            mRxDisposer.add("onClick_ok", mProfileListSV.getSelectedProfiles()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(selectedProfiles -> {
                        if (!selectedProfiles.isEmpty()) {
                            mNavigator.pop(Result.selectedProfile(selectedProfiles));
                        } else {
                            mLogger.i(TAG, view.getContext()
                                    .getString(R.string.error_please_select_profile));
                        }
                    }, throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mProfileListSV != null) {
            mProfileListSV.dispose(activity);
            mProfileListSV = null;
        }
        mCheckBoxSelectAll = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mRxDisposer = null;
    }

    /**
     * Result of this page
     */
    public static class Result implements Serializable {
        public static Result selectedProfile(ArrayList<Profile> selected) {
            Result result = new Result();
            result.mSelectedProfile = selected;
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

        private ArrayList<Profile> mSelectedProfile;

        private Result() {
        }

        public ArrayList<Profile> getSelectedProfile() {
            return mSelectedProfile;
        }
    }
}
