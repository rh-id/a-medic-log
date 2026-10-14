package m.co.rh.id.a_medic_log.app.ui.component.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.component.AppLockAuthHandler;
import m.co.rh.id.a_medic_log.app.provider.component.AppLockManager;
import m.co.rh.id.a_medic_log.app.provider.component.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.aprovider.Provider;

public class SecurityMenuSV extends StatefulView<Activity> implements
        CompoundButton.OnCheckedChangeListener, View.OnClickListener,
        NavOnActivityResult<Activity> {

    private static final int REQUEST_CODE_APP_LOCK_SETTINGS = 4005;

    @NavInject
    private transient Provider mProvider;
    private transient Activity mActivity;
    private transient View mView;
    private transient SwitchMaterial mSwitchAppLock;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mActivity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.menu_security, container, false);
        mView = view;
        SwitchMaterial switchAppLock = view.findViewById(R.id.switch_app_lock);
        mSwitchAppLock = switchAppLock;
        // apply current value before attaching the listener so initialization
        // does not write back to the shared preferences nor prompt for authentication
        switchAppLock.setChecked(mProvider.get(AppSharedPreferences.class).isAppLockEnabled());
        switchAppLock.setOnCheckedChangeListener(this);
        updateAvailability(mProvider.get(AppLockManager.class).checkAvailability(activity));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mActivity = null;
        mView = null;
        mSwitchAppLock = null;
    }

    private void updateAvailability(AppLockManager.LockAvailability availability) {
        View view = mView;
        if (view == null) {
            return;
        }
        TextView textSummary = view.findViewById(R.id.text_app_lock_summary);
        View row = view.findViewById(R.id.container_app_lock);
        int summaryRes;
        if (availability == AppLockManager.LockAvailability.HW_UNAVAILABLE) {
            summaryRes = R.string.settings_app_lock_summary_hw_unavailable;
            row.setOnClickListener(null);
            row.setClickable(false);
        } else if (availability == AppLockManager.LockAvailability.NO_AUTHENTICATION) {
            summaryRes = R.string.settings_app_lock_summary_no_authentication;
            // no authenticator is set up, the row becomes a launch point
            // to set one up in the system settings
            row.setOnClickListener(this);
        } else {
            summaryRes = R.string.settings_app_lock_summary_available;
            row.setOnClickListener(null);
            row.setClickable(false);
        }
        textSummary.setText(summaryRes);
    }

    private void setSwitchCheckedSilently(boolean checked) {
        SwitchMaterial switchAppLock = mSwitchAppLock;
        if (switchAppLock == null) {
            return;
        }
        switchAppLock.setOnCheckedChangeListener(null);
        switchAppLock.setChecked(checked);
        switchAppLock.setOnCheckedChangeListener(this);
    }

    private void setSummaryText(CharSequence text) {
        View view = mView;
        if (view == null) {
            return;
        }
        TextView textSummary = view.findViewById(R.id.text_app_lock_summary);
        textSummary.setText(text);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (isChecked) {
            presentEnableConfirmationPrompt();
        } else {
            mProvider.get(AppSharedPreferences.class).setAppLockEnabled(false);
        }
    }

    /**
     * Enabling the lock requires one successful authentication, so the user
     * cannot lock themselves out of the app by accident. The switch follows the
     * setting: it is only left checked once the authentication succeeded, so a
     * silently canceled prompt (e.g. backgrounding) can never leave it checked
     * while the setting is off.
     */
    private void presentEnableConfirmationPrompt() {
        Activity activity = mActivity;
        if (activity == null) {
            return;
        }
        AppLockManager.LockAvailability availability = mProvider.get(AppLockManager.class)
                .checkAvailability(activity);
        if (availability != AppLockManager.LockAvailability.AVAILABLE) {
            // nothing to confirm with, revert the switch and refresh the summary
            updateAvailability(availability);
            setSwitchCheckedSilently(false);
            return;
        }
        setSwitchCheckedSilently(false);
        // MainActivity (the only activity hosting this menu) extends
        // FragmentActivity which the auth handler's prompt requires
        FragmentActivity fragmentActivity = (FragmentActivity) activity;
        mProvider.get(AppLockAuthHandler.class).forcePresent(fragmentActivity,
                new AppLockAuthHandler.Callbacks() {
                    @Override
                    public void onHint(@NonNull CharSequence hint) {
                        // confirmation failed (lockout, missing authenticator,
                        // error, ...), surface the reason in the row summary,
                        // the switch already reverted before the presentation
                        setSummaryText(hint);
                    }

                    @Override
                    public void onAuthenticated() {
                        mProvider.get(AppSharedPreferences.class).setAppLockEnabled(true);
                        setSwitchCheckedSilently(true);
                        Activity currentActivity = mActivity;
                        if (currentActivity != null) {
                            updateAvailability(mProvider.get(AppLockManager.class)
                                    .checkAvailability(currentActivity));
                        }
                    }
                }, activity.getString(R.string.app_lock_enable_prompt_title));
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.container_app_lock) {
            openSecuritySettings();
        }
    }

    private void openSecuritySettings() {
        Activity activity = mActivity;
        if (activity == null) {
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
            intent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        }
        activity.startActivityForResult(intent, REQUEST_CODE_APP_LOCK_SETTINGS);
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator,
                                 int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_APP_LOCK_SETTINGS) {
            // availability may have changed while the user was in the system settings
            updateAvailability(mProvider.get(AppLockManager.class).checkAvailability(activity));
        }
    }
}
