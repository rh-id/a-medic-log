package m.co.rh.id.a_medic_log.app.provider.component;

import android.content.Context;

import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.workmanager.Tags;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.a_medic_log.base.state.ProfileState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderValue;

public class MedicineReminderEventHandler implements ProviderDisposable {
    private static final String TAG = MedicineReminderEventHandler.class.getName();
    // grace covers the lag between the form's pre-filled "now" start time and the actual save;
    // the recurrence path passes false so the daily recurrence stays exact
    private static final long CATCH_UP_GRACE_PERIOD_MS = TimeUnit.MINUTES.toMillis(5);
    private final ExecutorService mExecutorService;
    private final WorkManager mWorkManager;
    private final MedicineDao mMedicineDao;
    private final MedicineReminderDao mMedicineReminderDao;
    private final ProviderValue<AppSharedPreferences> mAppSharedPreferences;
    private final ProviderValue<MedicineReminderAlarmScheduler> mMedicineReminderAlarmScheduler;
    private final ProviderValue<MedicineRepository> mMedicineRepository;
    private final ProviderValue<ILogger> mLogger;
    private final NoteChangeNotifier mNoteChangeNotifier;
    private final MedicineChangeNotifier mMedicineChangeNotifier;
    private final MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private final ProfileChangeNotifier mProfileChangeNotifier;
    private final CompositeDisposable mCompositeDisposable;
    private final ReentrantLock mLock;

    public MedicineReminderEventHandler(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mMedicineDao = provider.get(MedicineDao.class);
        mMedicineReminderDao = provider.get(MedicineReminderDao.class);
        mAppSharedPreferences = provider.lazyGet(AppSharedPreferences.class);
        mMedicineReminderAlarmScheduler = provider.lazyGet(MedicineReminderAlarmScheduler.class);
        mMedicineRepository = provider.lazyGet(MedicineRepository.class);
        mLogger = provider.lazyGet(ILogger.class);
        mNoteChangeNotifier = provider.get(NoteChangeNotifier.class);
        mMedicineChangeNotifier = provider.get(MedicineChangeNotifier.class);
        mMedicineReminderChangeNotifier = provider.get(MedicineReminderChangeNotifier.class);
        mProfileChangeNotifier = provider.get(ProfileChangeNotifier.class);
        mCompositeDisposable = new CompositeDisposable();
        mLock = new ReentrantLock(); // lock to prevent inconsistency when scheduling alarm
        init();
        migrateWorkManagerRemindersIfNeeded();
        backfillDoseOccurrencesIfNeeded();
    }

    private void init() {
        handleNoteEvents();
        handleMedicineEvents();
        handleMedicineReminderEvents();
        handleProfileEvents();
    }

    private void handleNoteEvents() {
        mCompositeDisposable.add(
                mNoteChangeNotifier.getAddedNote()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(noteState ->
                        {
                            mLock.lock();
                            try {
                                scheduleMedicineReminderAlarms(getMedicineReminders(noteState));
                            } finally {
                                mLock.unlock();
                            }
                        })
        );
        mCompositeDisposable.add(
                mNoteChangeNotifier.getUpdatedNote()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(noteUpdatedEvent -> {
                            mLock.lock();
                            try {
                                List<MedicineReminder> medicineRemindersBefore = getMedicineReminders(noteUpdatedEvent.getBefore());
                                List<MedicineReminder> medicineRemindersAfter = getMedicineReminders(noteUpdatedEvent.getAfter());
                                if (!medicineRemindersBefore.isEmpty()) {
                                    cancelMedicineReminderNotificationWork(medicineRemindersBefore);
                                }
                                if (!medicineRemindersAfter.isEmpty()) {
                                    scheduleMedicineReminderAlarms(medicineRemindersAfter);
                                }
                            } finally {
                                mLock.unlock();
                            }
                        })
        );
        mCompositeDisposable.add(
                mNoteChangeNotifier.getDeletedNote()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(noteState ->
                                {
                                    mLock.lock();
                                    try {
                                        cancelMedicineReminderNotificationWork(getMedicineReminders(noteState));
                                    } finally {
                                        mLock.unlock();
                                    }
                                }
                        )
        );
    }

    private void handleMedicineEvents() {
        mCompositeDisposable.add(
                mMedicineChangeNotifier.getAddedMedicine()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineState ->
                        {
                            mLock.lock();
                            try {
                                scheduleMedicineReminderAlarms(medicineState.getMedicineReminderList());
                            } finally {
                                mLock.unlock();
                            }
                        })
        );
        mCompositeDisposable.add(
                mMedicineChangeNotifier.getUpdatedMedicine()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineUpdatedEvent -> {
                            mLock.lock();
                            try {
                                List<MedicineReminder> medicineRemindersBefore = medicineUpdatedEvent.getBefore().getMedicineReminderList();
                                List<MedicineReminder> medicineRemindersAfter = medicineUpdatedEvent.getAfter().getMedicineReminderList();
                                if (!medicineRemindersBefore.isEmpty()) {
                                    cancelMedicineReminderNotificationWork(medicineRemindersBefore);
                                }
                                if (!medicineRemindersAfter.isEmpty()) {
                                    scheduleMedicineReminderAlarms(medicineRemindersAfter);
                                }
                            } finally {
                                mLock.unlock();
                            }
                        })
        );
        mCompositeDisposable.add(
                mMedicineChangeNotifier.getDeletedMedicine()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineState ->
                                {
                                    mLock.lock();
                                    try {
                                        cancelMedicineReminderNotificationWork(medicineState.getMedicineReminderList());
                                    } finally {
                                        mLock.unlock();
                                    }
                                }
                        )
        );
    }

    private void handleMedicineReminderEvents() {
        mCompositeDisposable.add(
                mMedicineReminderChangeNotifier.getAddedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder ->
                        {
                            mLock.lock();
                            try {
                                scheduleMedicineReminderAlarms(Collections.singletonList(medicineReminder));
                            } finally {
                                mLock.unlock();
                            }
                        })
        );
        mCompositeDisposable.add(
                mMedicineReminderChangeNotifier.getUpdatedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminderUpdatedEvent -> {
                            mLock.lock();
                            try {
                                List<MedicineReminder> medicineRemindersBefore = Collections.singletonList(medicineReminderUpdatedEvent.getBefore());
                                List<MedicineReminder> medicineRemindersAfter = Collections.singletonList(medicineReminderUpdatedEvent.getAfter());
                                cancelMedicineReminderNotificationWork(medicineRemindersBefore);
                                scheduleMedicineReminderAlarms(medicineRemindersAfter);
                            } finally {
                                mLock.unlock();
                            }
                        })
        );
        mCompositeDisposable.add(
                mMedicineReminderChangeNotifier.getDeletedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder ->
                                {
                                    mLock.lock();
                                    try {
                                        // the repository already truncated the
                                        // deleted reminder's future occurrences
                                        // inside its delete transaction, only
                                        // the alarm cancellation is left here
                                        cancelMedicineReminderNotificationWork(Collections.singletonList(medicineReminder));
                                    } finally {
                                        mLock.unlock();
                                    }
                                }
                        )
        );
    }

    private void handleProfileEvents() {
        mCompositeDisposable.add(
                mProfileChangeNotifier.getDeletedProfile()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(profileState ->
                                {
                                    mLock.lock();
                                    try {
                                        List<MedicineReminder> medicineReminders = extractMedicineReminders(profileState);
                                        if (!medicineReminders.isEmpty()) {
                                            // cancel from the event payload, the cascade has
                                            // already removed the rows when this event fires
                                            cancelMedicineReminderNotificationWork(medicineReminders);
                                        }
                                    } finally {
                                        mLock.unlock();
                                    }
                                }
                        )
        );
    }

    /**
     * One-time migration from the previous inexact WorkManager scheduling to
     * the exact AlarmManager scheduling: every reminder row may still carry a
     * legacy work from the previous app version, so the legacy tag/unique work
     * is cancelled for ALL rows (not just enabled ones) before the alarms are
     * scheduled, then the flag is set. Idempotent, the snooze work is untouched.
     */
    public void migrateWorkManagerRemindersIfNeeded() {
        mExecutorService.execute(() -> {
            AppSharedPreferences appSharedPreferences = mAppSharedPreferences.get();
            if (appSharedPreferences.isMedicineReminderAlarmMigrated()) {
                return;
            }
            mLock.lock();
            try {
                // re-check under lock, the migration may have completed while
                // waiting for it
                if (appSharedPreferences.isMedicineReminderAlarmMigrated()) {
                    return;
                }
                List<MedicineReminder> allReminders = mMedicineReminderDao.findMedicineRemindersAll();
                for (MedicineReminder medicineReminder : allReminders) {
                    // the legacy unique work name is the same string as the tag
                    // (see the deleted MedicineReminderNotificationWorker cleanup),
                    // cancelling both covers the tagged one-shots and any
                    // leftover periodic unique work
                    String legacyWorkName = calculateTag(medicineReminder);
                    mWorkManager.cancelAllWorkByTag(legacyWorkName);
                    mWorkManager.cancelUniqueWork(legacyWorkName);
                }
                mMedicineReminderAlarmScheduler.get().scheduleAll();
                appSharedPreferences.setMedicineReminderAlarmMigrated(true);
            } catch (Exception e) {
                mLogger.get().e(TAG, "Failed to migrate medicine reminders to AlarmManager", e);
            } finally {
                mLock.unlock();
            }
        });
    }

    /**
     * One-time backfill of the medicine_dose_occurrence snapshot table after
     * the upgrade that introduced occurrence tracking: every ENABLED reminder's
     * expected doses from creation to end of today are recorded. Disabled
     * reminders are skipped entirely by the repository, without a recorded
     * disable time their history is unknowable. Idempotent, the insert is
     * conflict-IGNORE and the AppSharedPreferences flag makes it run once.
     * No local lock is taken: occurrence correctness is the repository's
     * encompassing transaction's job, this method only guards the once-only
     * flag.
     */
    public void backfillDoseOccurrencesIfNeeded() {
        mExecutorService.execute(() -> {
            AppSharedPreferences appSharedPreferences = mAppSharedPreferences.get();
            if (appSharedPreferences.isDoseOccurrenceBackfilled()) {
                return;
            }
            try {
                // the repository re-reads the authoritative reminder list
                // inside its transaction, no local snapshot is passed
                mMedicineRepository.get().backfillOccurrences(new Date());
                appSharedPreferences.setDoseOccurrenceBackfilled(true);
            } catch (Exception e) {
                mLogger.get().e(TAG, "Failed to backfill dose occurrences", e);
            }
        });
    }

    /**
     * Walks the profile state's nested children (note ⊃ medicine ⊃ reminder)
     * and collects every reminder whose alarm needs cancellation
     */
    static List<MedicineReminder> extractMedicineReminders(ProfileState profileState) {
        List<MedicineReminder> result = new ArrayList<>();
        if (profileState != null) {
            ArrayList<NoteState> noteStates = profileState.getNoteList();
            if (noteStates != null && !noteStates.isEmpty()) {
                for (NoteState noteState : noteStates) {
                    if (noteState == null) {
                        continue;
                    }
                    ArrayList<MedicineState> medicineStates = noteState.getMedicineList();
                    if (medicineStates != null && !medicineStates.isEmpty()) {
                        for (MedicineState medicineState : medicineStates) {
                            if (medicineState == null) {
                                continue;
                            }
                            List<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                            if (medicineReminders != null && !medicineReminders.isEmpty()) {
                                result.addAll(medicineReminders);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public void scheduleMedicineReminderAlarms(List<MedicineReminder> medicineReminders) {
        scheduleMedicineReminderAlarms(medicineReminders, true);
    }

    public void scheduleMedicineReminderAlarms(List<MedicineReminder> medicineReminders, boolean catchUpIfJustPassed) {
        if (!medicineReminders.isEmpty()) {
            for (MedicineReminder medicineReminder : medicineReminders) {
                if (medicineReminder.reminderEnabled) {
                    mMedicineReminderAlarmScheduler.get().schedule(medicineReminder, catchUpIfJustPassed);
                }
            }
        }
    }

    public void cancelMedicineReminderNotificationWork(List<MedicineReminder> medicineReminders) {
        if (!medicineReminders.isEmpty()) {
            for (MedicineReminder medicineReminder : medicineReminders) {
                mMedicineReminderAlarmScheduler.get().cancel(medicineReminder);
            }
        }
    }

    private List<MedicineReminder> getMedicineReminders(NoteState noteState) {
        List<MedicineReminder> result = new ArrayList<>();
        if (noteState != null) {
            List<MedicineState> medicineStates = noteState.getMedicineList();
            if (!medicineStates.isEmpty()) {
                for (MedicineState medicineState : medicineStates) {
                    List<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                    if (!medicineReminders.isEmpty()) {
                        result.addAll(medicineReminders);
                    }
                }
            }
        }
        return result;
    }

    static String calculateTag(MedicineReminder medicineReminder) {
        return Tags.MEDICINE_REMINDER_TAG + medicineReminder.id;
    }

    static long calculateInitialDelayMs(Date startDate, boolean catchUpIfJustPassed) {
        long result = 0;
        if (startDate != null) {
            long currentDateTime = System.currentTimeMillis();
            long startDateTime = startDate.getTime();
            result = startDateTime - currentDateTime;
            if (catchUpIfJustPassed && result < 0 && result >= -CATCH_UP_GRACE_PERIOD_MS) {
                return 0;
            }
            while (result < 0) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(startDateTime);
                calendar.add(Calendar.DATE, 1);
                startDateTime = calendar.getTimeInMillis();
                result = startDateTime - currentDateTime;
            }
        }
        return result;
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
