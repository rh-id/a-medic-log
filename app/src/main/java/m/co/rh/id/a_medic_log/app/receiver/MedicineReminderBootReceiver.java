package m.co.rh.id.a_medic_log.app.receiver;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderAlarmScheduler;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderEventHandler;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Re-schedules every enabled medicine reminder's alarm when the alarms are
 * lost: after a device reboot and after the exact-alarm permission state
 * changed (API 31+). Also runs the one-time migration from the legacy
 * WorkManager scheduling, covering an app update that was never launched:
 * without it WorkManager's own boot receiver would revive the legacy jobs
 * alongside the newly armed alarms. All the work runs on the shared executor
 * through {@link #goAsync()} (Room has no allowMainThreadQueries).
 */
public class MedicineReminderBootReceiver extends BroadcastReceiver {
    private static final String TAG = MedicineReminderBootReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            scheduleAllAlarms(context);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            scheduleAllAlarms(context);
        }
        // intentionally no LOCKED_BOOT_COMPLETED handling: the reminders are
        // scheduled for specific clock times, they do not need to fire before unlock
    }

    private void scheduleAllAlarms(Context context) {
        PendingResult pendingResult = goAsync();
        Provider provider = BaseApplication.of(context).getProvider();
        ExecutorService executorService = provider.get(ExecutorService.class);
        executorService.execute(() -> {
            try {
                // for a device updated but never launched since, this cancels
                // the legacy WorkManager reminder jobs before the alarms are
                // armed; no-op (flag-guarded) once migrated
                provider.get(MedicineReminderEventHandler.class).migrateWorkManagerRemindersIfNeeded();
                provider.get(MedicineReminderAlarmScheduler.class).scheduleAll();
            } catch (Exception e) {
                ILogger logger = provider.get(ILogger.class);
                logger.e(TAG, "Failed to reschedule medicine reminder alarms", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
