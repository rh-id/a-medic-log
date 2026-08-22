package m.co.rh.id.a_medic_log.app.provider.component;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationManagerCompat;

import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import co.rh.id.lib.rx3_utils.subject.QueueSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineReminderCmd;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.AndroidNotification;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.repository.AndroidNotificationRepository;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler {
    private static final String TAG = AppNotificationHandler.class.getName();

    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<AndroidNotificationRepository> mAndroidNotificationRepo;
    private final ProviderValue<MedicineDao> mMedicineDao;
    private final ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private final ProviderValue<NoteDao> mNoteDao;
    private final ProviderValue<ProfileDao> mProfileDao;
    private final ProviderValue<NewMedicineIntakeCmd> mNewMedicineIntakeCmd;
    private final ProviderValue<UpdateMedicineReminderCmd> mUpdateMedicineReminderCmd;
    private final ProviderValue<MedicineReminderEventHandler> mMedicineReminderEventHandler;
    private final MedicineReminderNotificationBuilder mNotificationBuilder;
    private final ReentrantLock mLock;
    private QueueSubject<MedicineReminder> mMedicineReminderSubject;

    public AppNotificationHandler(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mAndroidNotificationRepo = provider.lazyGet(AndroidNotificationRepository.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mNewMedicineIntakeCmd = provider.lazyGet(NewMedicineIntakeCmd.class);
        mUpdateMedicineReminderCmd = provider.lazyGet(UpdateMedicineReminderCmd.class);
        mMedicineReminderEventHandler = provider.lazyGet(MedicineReminderEventHandler.class);
        mNotificationBuilder = new MedicineReminderNotificationBuilder(mAppContext);
        mLock = new ReentrantLock();
        mMedicineReminderSubject = new QueueSubject<>();
    }
    @RequiresPermission("android.permission.POST_NOTIFICATIONS")
    public void postMedicineReminder(MedicineReminder medicineReminder) {
        mLock.lock();
        try {
            mNotificationBuilder.createMedicineReminderNotificationChannel();
            AndroidNotification androidNotification = mNotificationBuilder
                    .createAndroidNotification(medicineReminder);
            mAndroidNotificationRepo.get().insertNotification(androidNotification);
            Medicine medicine = mMedicineDao.get().findMedicineById(medicineReminder.medicineId);
            Note note = mNoteDao.get().findNoteById(medicine.noteId);
            Profile profile = mProfileDao.get().findProfileById(note.profileId);
            String title = mAppContext.getString(R.string.notification_title_medicine_reminder, profile.name, medicine.name);
            String content = medicineReminder.message;
            Notification notification = mNotificationBuilder
                    .buildMedicineReminderNotification(title, content, androidNotification.requestId);

            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
            notificationManagerCompat.notify(androidNotification.groupKey,
                    androidNotification.requestId,
                    notification);
        } catch (Exception e) {
            mLogger.get().d(TAG, "Failed to post medicine reminder: " + e.getMessage(), e);
        } finally {
            mLock.unlock();
        }
    }

    public void removeNotification(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(MedicineReminderNotificationBuilder.KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() ->
            {
                mLock.lock();
                try {
                    mAndroidNotificationRepo.get().deleteNotificationByRequestId((Integer) serializable);
                } catch (Exception e) {
                    mLogger.get().d(TAG, "Failed to post delete notification: " + e.getMessage(), e);
                } finally {
                    mLock.unlock();
                }
            });
        }
    }

    public void cancelNotificationSync(MedicineReminder medicineReminder) {
        mLock.lock();
        try {
            AndroidNotification androidNotification = mAndroidNotificationRepo.get().findByGroupTagAndRefId(MedicineReminderNotificationBuilder.GROUP_KEY_MEDICINE_REMINDER, medicineReminder.id);
            if (androidNotification != null) {
                NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
                notificationManagerCompat.cancel(androidNotification.groupKey,
                        androidNotification.requestId);
                mAndroidNotificationRepo.get().deleteNotification(androidNotification);
            }
        } catch (Exception e) {
            mLogger.get().d(TAG, "Failed to cancel notification: " + e.getMessage(), e);
        } finally {
            mLock.unlock();
        }
    }

    public void takeMedicine(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(MedicineReminderNotificationBuilder.KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null && androidNotification.groupKey.equals(MedicineReminderNotificationBuilder.GROUP_KEY_MEDICINE_REMINDER)) {
                        MedicineReminder medicineReminder = mMedicineReminderDao.get().findMedicineReminderById(androidNotification.refId);
                        MedicineIntake medicineIntake = new MedicineIntake();
                        medicineIntake.medicineId = medicineReminder.medicineId;
                        medicineIntake.description = medicineReminder.message;
                        mNewMedicineIntakeCmd.get().execute(medicineIntake);
                        cancelNotificationSync(medicineReminder);
                    }
                } catch (Exception e) {
                    mLogger.get().d(TAG, "Failed to take medicine: " + e.getMessage(), e);
                } finally {
                    mLock.unlock();
                }
            });
        }
    }

    public void disableMedicineReminder(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(MedicineReminderNotificationBuilder.KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null && androidNotification.groupKey.equals(MedicineReminderNotificationBuilder.GROUP_KEY_MEDICINE_REMINDER)) {
                        MedicineReminder medicineReminder = mMedicineReminderDao.get().findMedicineReminderById(androidNotification.refId);
                        medicineReminder.reminderEnabled = false;
                        medicineReminder = mUpdateMedicineReminderCmd.get().execute(medicineReminder).blockingGet();
                        cancelNotificationSync(medicineReminder);
                        mMedicineReminderEventHandler.get()
                                .cancelMedicineReminderNotificationWork(Collections.singletonList(medicineReminder));
                    }
                } catch (Exception e) {
                    mLogger.get().d(TAG, "Failed to disable medicine reminder: " + e.getMessage(), e);
                } finally {
                    mLock.unlock();
                }
            });
        }
    }

    public void processNotification(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(MedicineReminderNotificationBuilder.KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null && androidNotification.groupKey.equals(MedicineReminderNotificationBuilder.GROUP_KEY_MEDICINE_REMINDER)) {
                        MedicineReminder medicineReminder = mMedicineReminderDao.get().findMedicineReminderById(androidNotification.refId);
                        mMedicineReminderSubject.onNext(medicineReminder);
                        cancelNotificationSync(medicineReminder);
                    }
                } catch (Exception e) {
                    mLogger.get().d(TAG, "Failed to process notification: " + e.getMessage(), e);
                } finally {
                    mLock.unlock();
                }
            });
        }
    }

    public Flowable<MedicineReminder> getMedicineReminderFlow() {
        return Flowable.fromObservable(mMedicineReminderSubject, BackpressureStrategy.BUFFER)
                .subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
