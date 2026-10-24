package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.provider.BaseProviderModule;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Instrumented test of the real {@link MedicineReminderAlarmScheduler} against
 * the device AlarmManager: a reminder scheduled seven days out must surface as
 * the device's next alarm clock at exactly its start time (a far-future unique
 * trigger time, so the test's alarm is the next one on a clean test device),
 * and the test must not leave the alarm behind.
 */
@RunWith(AndroidJUnit4.class)
public class MedicineReminderAlarmSchedulerTest {

    /**
     * Tolerance for the trigger time assertion: covers the scheduler's two
     * internal clock reads (see the test comment), far below the ~24 h shift
     * a wrong daily roll would produce.
     */
    private static final long TRIGGER_TOLERANCE_MS = 1000;

    private Context mContext;
    private Provider mProvider;
    private AppDatabase mAppDatabase;
    private MedicineReminderDao mMedicineReminderDao;
    private MedicineReminderAlarmScheduler mScheduler;
    private MedicineReminder mScheduledReminder;

    @Before
    public void init() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(mContext, new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                providerRegistry.registerModule(new BaseProviderModule());
                providerRegistry.registerAsync(AppDatabase.class, () ->
                        Room.inMemoryDatabaseBuilder(provider.getContext(), AppDatabase.class)
                                .addTypeConverter(new LinkedHashSetConverter(provider.get(ILogger.class)))
                                .build());
                providerRegistry.registerAsync(MedicineReminderDao.class, () ->
                        provider.get(AppDatabase.class).medicineReminderDao());
                providerRegistry.registerAsync(MedicineReminderAlarmScheduler.class, () ->
                        new MedicineReminderAlarmScheduler(provider));
            }
        });
        mAppDatabase = mProvider.get(AppDatabase.class);
        mMedicineReminderDao = mProvider.get(MedicineReminderDao.class);
        mScheduler = mProvider.get(MedicineReminderAlarmScheduler.class);
    }

    @After
    public void cleanup() {
        // never leave a live alarm behind, even after a failed assertion
        if (mScheduler != null && mScheduledReminder != null) {
            mScheduler.cancel(mScheduledReminder);
        }
        if (mAppDatabase != null) {
            mAppDatabase.close();
        }
        if (mProvider != null) {
            mProvider.dispose();
        }
    }

    @Test
    public void schedule_farFutureReminder_becomesTheNextAlarmClock() {
        // arrange: a reminder starting exactly +7 days from a single captured
        // "now", a far-future unique trigger time so no other alarm clock is
        // scheduled between now and it
        long now = System.currentTimeMillis();
        MedicineReminder reminder = new MedicineReminder();
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTimeInMillis(now);
        startCalendar.add(Calendar.DAY_OF_YEAR, 7);
        reminder.startDateTime = startCalendar.getTime();
        reminder.reminderDays = new LinkedHashSet<>(Collections.singletonList(
                startCalendar.get(Calendar.DAY_OF_WEEK)));
        reminder.id = mMedicineReminderDao.insert(reminder);
        mScheduledReminder = reminder;
        // the expected trigger derives from the same captured start value
        long expectedTriggerTime = startCalendar.getTimeInMillis();
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo alarmClockBefore = alarmManager.getNextAlarmClock();

        // act
        mScheduler.schedule(reminder, false);

        // assert: the trigger lands on the reminder's start time (a future
        // start needs no daily rolling). The scheduler reads the clock twice
        // internally (once in schedule, once inside the delay calculation),
        // so the trigger can land a clock-tick or two away from the start;
        // the 1 s tolerance is negligible against the ~24 h shift a wrong
        // daily roll would produce
        AlarmManager.AlarmClockInfo nextAlarmClock = alarmManager.getNextAlarmClock();
        assertNotNull("the scheduled reminder must be registered as the next alarm clock",
                nextAlarmClock);
        assertTrue("the next alarm clock must trigger at the reminder's start time (got "
                        + nextAlarmClock.getTriggerTime() + ", expected " + expectedTriggerTime + ")",
                Math.abs(nextAlarmClock.getTriggerTime() - expectedTriggerTime)
                        <= TRIGGER_TOLERANCE_MS);

        // act: cancel
        mScheduler.cancel(reminder);

        // assert: the alarm clock is gone again, the device is back to its
        // pre-test state
        AlarmManager.AlarmClockInfo alarmClockAfter = alarmManager.getNextAlarmClock();
        if (alarmClockBefore == null) {
            assertNull("the cancelled reminder must not stay the next alarm clock",
                    alarmClockAfter);
        } else {
            assertEquals("the pre-test next alarm clock must be restored",
                    alarmClockBefore.getTriggerTime(), alarmClockAfter.getTriggerTime());
        }
    }
}
