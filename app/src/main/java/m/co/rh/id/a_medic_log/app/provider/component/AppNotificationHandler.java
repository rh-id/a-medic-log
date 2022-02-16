package m.co.rh.id.a_medic_log.app.provider.component;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.MainActivity;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_medic_log.app.receiver.NotificationDisableMedicineReminderReceiver;
import m.co.rh.id.a_medic_log.app.receiver.NotificationTakeMedicineReceiver;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.AndroidNotification;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.repository.AndroidNotificationRepo;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler {
    private static final String GROUP_KEY_MEDICINE_REMINDER = "GROUP_KEY_MEDICINE_REMINDER";
    private static final String KEY_INT_REQUEST_ID = "KEY_INT_REQUEST_ID";
    private static final String CHANNEL_ID_MEDICINE_REMINDER = "CHANNEL_ID_MEDICINE_REMINDER";
    private static final String TAG = AppNotificationHandler.class.getName();

    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<AndroidNotificationRepo> mAndroidNotificationRepo;
    private final ProviderValue<MedicineDao> mMedicineDao;
    private final ProviderValue<NoteDao> mNoteDao;
    private final ProviderValue<ProfileDao> mProfileDao;
    private final ProviderValue<NewMedicineIntakeCmd> mNewMedicineIntakeCmd;
    private final ProviderValue<UpdateMedicineReminderCmd> mUpdateMedicineReminderCmd;
    private final ProviderValue<MedicineReminderEventHandler> mMedicineReminderEventHandler;
    private final ReentrantLock mLock;
    private ReplaySubject<MedicineReminder> mMedicineReminderSubject;

    public AppNotificationHandler(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mAndroidNotificationRepo = provider.lazyGet(AndroidNotificationRepo.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mNewMedicineIntakeCmd = provider.lazyGet(NewMedicineIntakeCmd.class);
        mUpdateMedicineReminderCmd = provider.lazyGet(UpdateMedicineReminderCmd.class);
        mMedicineReminderEventHandler = provider.lazyGet(MedicineReminderEventHandler.class);
        mLock = new ReentrantLock();
        mMedicineReminderSubject = ReplaySubject.create();
    }

    public void postMedicineReminder(MedicineReminder medicineReminder) {
        mLock.lock();
        try {
            createMedicineReminderNotificationChannel();
            AndroidNotification androidNotification = new AndroidNotification();
            androidNotification.groupKey = GROUP_KEY_MEDICINE_REMINDER;
            androidNotification.refId = medicineReminder.id;
            mAndroidNotificationRepo.get().insertNotification(androidNotification);
            Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
            receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            int intentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intentFlag = PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, androidNotification.requestId, receiverIntent,
                    intentFlag);
            Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
            deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, deleteIntent,
                    intentFlag);
            Medicine medicine = mMedicineDao.get().findMedicineById(medicineReminder.medicineId);
            Note note = mNoteDao.get().findNoteById(medicine.noteId);
            Profile profile = mProfileDao.get().findProfileById(note.profileId);
            String title = mAppContext.getString(R.string.notification_title_medicine_reminder, profile.name, medicine.name);
            String content = medicineReminder.message;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_MEDICINE_REMINDER)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColorized(true)
                    .setColor(mAppContext.getResources().getColor(R.color.indigo_500))
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setDeleteIntent(deletePendingIntent)
                    .setGroup(GROUP_KEY_MEDICINE_REMINDER)
                    .setAutoCancel(false);
            Intent intentTakeMedicine = new Intent(mAppContext, NotificationTakeMedicineReceiver.class);
            intentTakeMedicine.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            PendingIntent pendingIntentTakeMedicine = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, intentTakeMedicine,
                    intentFlag);
            builder.addAction(R.drawable.ic_check_black, mAppContext.getString(R.string.take_medicine), pendingIntentTakeMedicine);
            Intent intentDisableMedicineReminder = new Intent(mAppContext, NotificationDisableMedicineReminderReceiver.class);
            intentDisableMedicineReminder.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            PendingIntent pendingIntentDisableMedicineReminder = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, intentDisableMedicineReminder,
                    intentFlag);
            builder.addAction(R.drawable.ic_timer_off_black, mAppContext.getString(R.string.disable_reminder), pendingIntentDisableMedicineReminder);

            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
            notificationManagerCompat.notify(GROUP_KEY_MEDICINE_REMINDER,
                    androidNotification.requestId,
                    builder.build());
        } catch (Exception e) {
            mLogger.get().d(TAG, "Failed to post medicine reminder: " + e.getMessage(), e);
        } finally {
            mLock.unlock();
        }
    }

    private void createMedicineReminderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = mAppContext.getString(R.string.notification_channel_name_medicine_reminder);
            String description = mAppContext.getString(R.string.notification_channel_description_medicine_reminder);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_MEDICINE_REMINDER,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = mAppContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void removeNotification(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
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
            AndroidNotification androidNotification = mAndroidNotificationRepo.get().findByGroupTagAndRefId(GROUP_KEY_MEDICINE_REMINDER, medicineReminder.id);
            if (androidNotification != null) {
                NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
                notificationManagerCompat.cancel(GROUP_KEY_MEDICINE_REMINDER,
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
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_MEDICINE_REMINDER)) {
                        MedicineReminder medicineReminder = mMedicineDao.get().findMedicineReminderById(androidNotification.refId);
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
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_MEDICINE_REMINDER)) {
                        MedicineReminder medicineReminder = mMedicineDao.get().findMedicineReminderById(androidNotification.refId);
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
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_MEDICINE_REMINDER)) {
                        MedicineReminder medicineReminder = mMedicineDao.get().findMedicineReminderById(androidNotification.refId);
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

    public void clearNotificationBuffer() {
        mMedicineReminderSubject.cleanupBuffer();
        mMedicineReminderSubject.onComplete();
        mMedicineReminderSubject = ReplaySubject.create();
    }
}
