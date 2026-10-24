package m.co.rh.id.a_medic_log.app.receiver;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderAlarmScheduler;
import m.co.rh.id.a_medic_log.app.workmanager.Keys;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Fires when a medicine reminder alarm goes off: checks the reminder is
 * still valid for today, posts its notification and schedules the next
 * day's occurrence. All the work runs on the shared executor through
 * {@link #goAsync()} (Room has no allowMainThreadQueries).
 */
public class MedicineReminderAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = MedicineReminderAlarmReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        Provider provider = BaseApplication.of(context).getProvider();
        ExecutorService executorService = provider.get(ExecutorService.class);
        executorService.execute(() -> {
            try {
                handleMedicineReminderAlarm(provider, context, intent);
            } catch (Exception e) {
                ILogger logger = provider.get(ILogger.class);
                logger.e(TAG, "Failed to handle medicine reminder alarm", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void handleMedicineReminderAlarm(Provider provider, Context context, Intent intent) {
        long medicineReminderId = intent.getLongExtra(Keys.LONG_MEDICINE_REMINDER_ID, -1);
        MedicineReminder medicineReminder = provider.get(MedicineReminderDao.class)
                .findMedicineReminderById(medicineReminderId);
        if (medicineReminder == null) {
            // the reminder was deleted while the alarm was pending
            return;
        }
        if (Boolean.FALSE.equals(medicineReminder.reminderEnabled)) {
            // the reminder was disabled while the alarm was pending
            return;
        }
        // the day check only gates today's notification, the recurrence chain
        // itself must continue on every day of the week so the next selected
        // day still fires (mirrors the replaced MedicineReminderNotificationWorker)
        if (medicineReminder.reminderDays != null
                && medicineReminder.reminderDays.contains(getCurrentDay())
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
            appNotificationHandler.cancelNotificationSync(medicineReminder);
            appNotificationHandler.postMedicineReminder(medicineReminder);
        }
        // recurrence: schedule the next day's occurrence from the reminder's start date
        provider.get(MedicineReminderAlarmScheduler.class).schedule(medicineReminder, false);
    }

    private static int getCurrentDay() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.DAY_OF_WEEK);
    }
}
