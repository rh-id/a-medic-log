package m.co.rh.id.a_medic_log.app.provider.component;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.List;

import m.co.rh.id.a_medic_log.app.MainActivity;
import m.co.rh.id.a_medic_log.app.receiver.MedicineReminderAlarmReceiver;
import m.co.rh.id.a_medic_log.app.workmanager.Keys;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;

/**
 * Schedules one exact {@link AlarmManager} alarm per enabled medicine reminder,
 * replacing the previous inexact WorkManager scheduling. Each alarm fires
 * {@link MedicineReminderAlarmReceiver} which posts the notification and
 * re-schedules the next day's occurrence.
 * <p>
 * The alarm is set with {@link AlarmManager#setAlarmClock} where available:
 * it is always exact, survives Doze, and surfaces as the user-visible alarm
 * clock icon. API 33+ relies on the USE_EXACT_ALARM permission, API 31-32
 * falls back to {@link AlarmManager#setAndAllowWhileIdle} when the user
 * revoked SCHEDULE_EXACT_ALARM, and API 21-22 uses
 * {@link AlarmManager#setExact} (the while-idle fallback needs API 23).
 */
public class MedicineReminderAlarmScheduler {

    /**
     * Request code of the shared alarm-clock show intent. The notification
     * request ids start at 0 and count up, so Integer.MAX_VALUE never
     * collides with them (the show intent would otherwise silently replace
     * a notification's content intent, both target MainActivity).
     */
    private static final int SHOW_INTENT_REQUEST_CODE = Integer.MAX_VALUE;

    private final Context mAppContext;
    private final AlarmManager mAlarmManager;
    private final MedicineReminderDao mMedicineReminderDao;
    private final PendingIntent mShowIntent;

    public MedicineReminderAlarmScheduler(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mAlarmManager = (AlarmManager) mAppContext.getSystemService(Context.ALARM_SERVICE);
        mMedicineReminderDao = provider.get(MedicineReminderDao.class);
        // the alarm clock show intent deep-links into the app, it is identical
        // for every reminder so a single PendingIntent is shared by all alarms
        Intent intent = new Intent(mAppContext, MainActivity.class);
        mShowIntent = PendingIntent.getActivity(mAppContext, SHOW_INTENT_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Schedules the next alarm for the given reminder. The trigger time is
     * now plus the daily-rolling delay of the reminder's start date.
     *
     * @param catchUpIfJustPassed true to fire immediately when the start time
     *                            just passed within the catch-up grace period
     *                            (user-initiated add/update), false to roll
     *                            straight to the next day (migration, boot,
     *                            recurrence)
     */
    public void schedule(MedicineReminder reminder, boolean catchUpIfJustPassed) {
        if (reminder == null || reminder.id == null) {
            return;
        }
        long triggerAt = System.currentTimeMillis()
                + MedicineReminderEventHandler.calculateInitialDelayMs(reminder.startDateTime, catchUpIfJustPassed);
        scheduleInternal(reminder, triggerAt);
    }

    /**
     * Cancels the alarm of the given reminder, if any.
     */
    public void cancel(MedicineReminder reminder) {
        if (reminder == null || reminder.id == null) {
            return;
        }
        mAlarmManager.cancel(createAlarmPendingIntent(reminder.id));
    }

    /**
     * (Re)schedules every enabled reminder in the database, used on boot,
     * on exact-alarm permission state changes and by the one-time migration.
     */
    public void scheduleAll() {
        List<MedicineReminder> medicineReminders = mMedicineReminderDao.findMedicineRemindersAll();
        for (MedicineReminder medicineReminder : medicineReminders) {
            if (Boolean.TRUE.equals(medicineReminder.reminderEnabled)) {
                schedule(medicineReminder, false);
            }
        }
    }

    /**
     * Performs (or, in test doubles, records) the alarm set for one reminder.
     * Separate from {@link #schedule} so tests can observe the computed
     * trigger time without touching the device AlarmManager.
     */
    protected void scheduleInternal(MedicineReminder reminder, long triggerAt) {
        PendingIntent alarmOperation = createAlarmPendingIntent(reminder.id);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // API 21-22: plain setExact, no permission needed; the while-idle
            // variant only exists from API 23
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, alarmOperation);
            return;
        }
        setAlarmApi23Plus(alarmOperation, triggerAt);
    }

    /**
     * The API 23+ alarm path: setAlarmClock wherever the exact-alarm permission
     * allows it, falling back to setAndAllowWhileIdle when the user revoked
     * SCHEDULE_EXACT_ALARM (API 31-32 only). Split from
     * {@link #scheduleInternal} so lint can verify the API levels.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private void setAlarmApi23Plus(PendingIntent alarmOperation, long triggerAt) {
        // re-read here instead of receiving it as a parameter, so lint can
        // relate it to Build.VERSION.SDK_INT (SDK_INT never changes in-process)
        int sdkInt = Build.VERSION.SDK_INT;
        boolean canScheduleExactAlarms = false;
        if (sdkInt >= Build.VERSION_CODES.S) {
            canScheduleExactAlarms = mAlarmManager.canScheduleExactAlarms();
        }
        if (resolveAlarmMethod(sdkInt, canScheduleExactAlarms) == ExactAlarmMethod.ALARM_CLOCK) {
            // always exact and exempt from Doze
            mAlarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, mShowIntent),
                    alarmOperation);
        } else {
            // the only non-ALARM_CLOCK outcome of resolveAlarmMethod on API 23+:
            // SCHEDULE_EXACT_ALARM revoked on API 31-32, deliver while-idle instead
            mAlarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmOperation);
        }
    }

    private PendingIntent createAlarmPendingIntent(long reminderId) {
        Intent intent = new Intent(mAppContext, MedicineReminderAlarmReceiver.class);
        intent.putExtra(Keys.LONG_MEDICINE_REMINDER_ID, reminderId);
        return PendingIntent.getBroadcast(mAppContext, (int) reminderId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * The exact alarm API ladder, package-private static so the unit test
     * can pin every branch without an Android device.
     */
    enum ExactAlarmMethod {
        ALARM_CLOCK,
        EXACT_AND_ALLOW_WHILE_IDLE,
        SET_EXACT
    }

    static ExactAlarmMethod resolveAlarmMethod(int sdkInt, boolean canScheduleExactAlarms) {
        if (sdkInt < Build.VERSION_CODES.M) {
            // API 21-22: setExact, no permission needed
            return ExactAlarmMethod.SET_EXACT;
        }
        if (sdkInt < Build.VERSION_CODES.S) {
            // API 23-30: setAlarmClock is unguarded
            return ExactAlarmMethod.ALARM_CLOCK;
        }
        if (sdkInt > Build.VERSION_CODES.S_V2) {
            // API 33+: permitted by the install-time USE_EXACT_ALARM permission
            return ExactAlarmMethod.ALARM_CLOCK;
        }
        // API 31-32: only setAlarmClock while the user granted SCHEDULE_EXACT_ALARM
        return canScheduleExactAlarms ? ExactAlarmMethod.ALARM_CLOCK
                : ExactAlarmMethod.EXACT_AND_ALLOW_WHILE_IDLE;
    }
}
