package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.Serializable;
import java.util.ArrayList;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.ui.component.profile.ProfileListSV;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ProfileSelectSVDialog extends StatefulViewDialog<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private static final String TAG = ProfileSelectSVDialog.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient ILogger mLogger;

    @NavInject
    private ProfileListSV mProfileListSV;

    public ProfileSelectSVDialog() {
        mProfileListSV = new ProfileListSV(ProfileListSV.ListMode.selectMode());
    }

    @Override
    public void provideComponent(Provider provider) {
        mLogger = provider.get(ILogger.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.dialog_profile_select, container, false);
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mProfileListSV.buildView(activity, rootLayout));
        Button buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        Button buttonOk = rootLayout.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.button_cancel) {
            getNavigator().pop();
        } else if (viewId == R.id.button_ok) {
            ArrayList<Profile> selectedProfile = mProfileListSV.getSelectedProfile();
            if (!selectedProfile.isEmpty()) {
                getNavigator().pop(Result.selectedProfile(selectedProfile));
            } else {
                mLogger.i(TAG, view.getContext().getString(R.string.error_please_select_profile));
            }
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mProfileListSV != null) {
            mProfileListSV.dispose(activity);
            mProfileListSV = null;
        }
    }

    /**
     * Result of this dialog
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
