package m.co.rh.id.a_medic_log.app.provider.component;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
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
import m.co.rh.id.a_medic_log.app.workmanager.Keys;
import m.co.rh.id.a_medic_log.app.workmanager.MedicineReminderNotificationWorker;
import m.co.rh.id.a_medic_log.app.workmanager.Tags;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class MedicineReminderEventHandler implements ProviderDisposable {
    private final ExecutorService mExecutorService;
    private final WorkManager mWorkManager;
    private final NoteChangeNotifier mNoteChangeNotifier;
    private final MedicineChangeNotifier mMedicineChangeNotifier;
    private final MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private final CompositeDisposable mCompositeDisposable;
    private final ReentrantLock mLock;

    public MedicineReminderEventHandler(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mNoteChangeNotifier = provider.get(NoteChangeNotifier.class);
        mMedicineChangeNotifier = provider.get(MedicineChangeNotifier.class);
        mMedicineReminderChangeNotifier = provider.get(MedicineReminderChangeNotifier.class);
        mCompositeDisposable = new CompositeDisposable();
        mLock = new ReentrantLock(); // lock to prevent inconsistency when scheduling worker
        init();
    }

    private void init() {
        handleNoteEvents();
        handleMedicineEvents();
        handleMedicineReminderEvents();
    }

    private void handleNoteEvents() {
        mCompositeDisposable.add(
                mNoteChangeNotifier.getAddedNote()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(noteState ->
                        {
                            mLock.lock();
                            try {
                                startMedicineReminderNotificationWork(getMedicineReminders(noteState));
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
                                    startMedicineReminderNotificationWork(medicineRemindersAfter);
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
                                startMedicineReminderNotificationWork(medicineState.getMedicineReminderList());
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
                                    startMedicineReminderNotificationWork(medicineRemindersAfter);
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
                                startMedicineReminderNotificationWork(Collections.singletonList(medicineReminder));
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
                                startMedicineReminderNotificationWork(medicineRemindersAfter);
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
                                        cancelMedicineReminderNotificationWork(Collections.singletonList(medicineReminder));
                                    } finally {
                                        mLock.unlock();
                                    }
                                }
                        )
        );
    }

    private void startMedicineReminderNotificationWork(List<MedicineReminder> medicineReminders) {
        if (!medicineReminders.isEmpty()) {
            for (MedicineReminder medicineReminder : medicineReminders) {
                if (medicineReminder.reminderEnabled) {
                    long initialDelay = calculateInitialDelayMs(medicineReminder.startDateTime);
                    String tag = calculateTag(medicineReminder);
                    PeriodicWorkRequest notificationWorkRequest =
                            new PeriodicWorkRequest.Builder(MedicineReminderNotificationWorker.class,
                                    1, TimeUnit.DAYS)
                                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                                    .addTag(tag)
                                    .setInputData(calculateInputData(medicineReminder))
                                    .build();
                    mWorkManager.enqueueUniquePeriodicWork(tag,
                            ExistingPeriodicWorkPolicy.REPLACE, notificationWorkRequest);
                }
            }
        }
    }

    public void cancelMedicineReminderNotificationWork(List<MedicineReminder> medicineReminders) {
        if (!medicineReminders.isEmpty()) {
            for (MedicineReminder medicineReminder : medicineReminders) {
                mWorkManager.cancelAllWorkByTag(calculateTag(medicineReminder));
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

    private Data calculateInputData(MedicineReminder medicineReminder) {
        return new Data.Builder().putLong(Keys.LONG_MEDICINE_REMINDER_ID, medicineReminder.id)
                .build();
    }

    private String calculateTag(MedicineReminder medicineReminder) {
        return Tags.MEDICINE_REMINDER_TAG + medicineReminder.id;
    }

    private long calculateInitialDelayMs(Date startDate) {
        long result = 0;
        if (startDate != null) {
            long currentDateTime = System.currentTimeMillis();
            long startDateTime = startDate.getTime();
            result = startDateTime - currentDateTime;
            while (result < 0) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(currentDateTime);
                calendar.add(Calendar.DATE, 1);
                currentDateTime = calendar.getTimeInMillis();
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
