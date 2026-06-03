package m.co.rh.id.a_medic_log.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class NotificationDisableMedicineReminderReceiver extends BroadcastReceiver {
    private static final String TAG = NotificationDisableMedicineReminderReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Provider provider = BaseApplication.of(context).getProvider();
            AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
            appNotificationHandler.disableMedicineReminder(intent);
        } catch (Exception e) {
            ILogger logger = BaseApplication.of(context).getProvider().get(ILogger.class);
            logger.e(TAG, "Failed to handle disable medicine reminder action", e);
        }
    }
}
