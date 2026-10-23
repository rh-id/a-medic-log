package m.co.rh.id.a_medic_log.app.ui.component.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.component.NotificationPermissionGate;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings row for the Android 13+ notification permission: a live status
 * summary plus the manual recovery entry points. One row, two recoveries:
 * while the system still shows the permission dialog (first ask, or a denial
 * where "ask again" is alive) the tap requests in place; once the system
 * would silently ignore a request (denied with "don't ask again") the tap
 * opens this app's notification settings screen instead.
 */
public class NotificationMenuSV extends StatefulView<Activity>
        implements View.OnClickListener, NavOnActivityResult<Activity>,
        NavOnRequestPermissionResult<Activity> {

    /**
     * Request code of the app notification settings round-trip, next to
     * {@link NotificationPermissionGate#REQUEST_POST_NOTIFICATIONS} and
     * deliberately outside the navigator's request-code range (kept
     * component-local like SecurityMenuSV's app lock settings code). The
     * result refreshes the summary, the permission may have been re-enabled
     * while the user was in the system settings.
     */
    private static final int REQUEST_CODE_NOTIFICATION_SETTINGS = 4203;

    @NavInject
    private transient Provider mProvider;
    private transient Activity mActivity;
    private transient View mView;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mActivity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.menu_notification, container, false);
        mView = view;
        view.findViewById(R.id.container_notification).setOnClickListener(this);
        updateSummary();
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mActivity = null;
        mView = null;
    }

    private void updateSummary() {
        View view = mView;
        Activity activity = mActivity;
        if (view == null || activity == null) {
            return;
        }
        TextView textSummary = view.findViewById(R.id.text_notification_summary);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // below Android 13 notifications are granted at install time,
            // there is no permission to talk about
            textSummary.setText(R.string.settings_notifications_summary_not_required);
        } else if (mProvider.get(NotificationPermissionGate.class)
                .isPermissionGranted(activity)) {
            textSummary.setText(R.string.settings_notifications_summary_granted);
        } else {
            textSummary.setText(R.string.settings_notifications_summary_denied);
        }
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.container_notification) {
            handleRowClick();
        }
    }

    private void handleRowClick() {
        Activity activity = mActivity;
        if (activity == null) {
            return;
        }
        NotificationPermissionGate gate = mProvider.get(NotificationPermissionGate.class);
        NotificationPermissionGate.Decision decision = gate.decide(activity);
        switch (decision) {
            case REQUEST:
                // result is routed back here through MainActivity ->
                // navigator -> SettingsPage (NavOnRequestPermissionResult)
                gate.requestPermission(activity);
                break;
            case OPEN_SETTINGS:
                // the OPEN_SETTINGS decision only ever exists on Tiramisu+,
                // the explicit guard keeps the @RequiresApi call lint-clean;
                // launched for result like SecurityMenuSV's settings deep
                // link so the summary refreshes on return (routed back here
                // through MainActivity -> navigator -> SettingsPage's
                // NavOnActivityResult forwarding)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.startActivityForResult(
                            gate.createAppNotificationSettingsIntent(activity),
                            REQUEST_CODE_NOTIFICATION_SETTINGS);
                }
                break;
            case NONE:
            default:
                // nothing to fix, refresh the label in case it went stale
                updateSummary();
                break;
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator,
                                 int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_NOTIFICATION_SETTINGS) {
            // the permission may have been re-enabled while the user was in
            // the system settings
            updateSummary();
        }
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity,
                                           INavigator navigator, int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == NotificationPermissionGate.REQUEST_POST_NOTIFICATIONS) {
            // the grant state may have changed while the system dialog was up
            updateSummary();
        }
    }
}
