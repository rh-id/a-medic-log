package m.co.rh.id.a_medic_log.app.workmanager;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class MedicineReminderSnoozeWorker extends Worker {
    private ProviderValue<AppNotificationHandler> mAppNotificationHandler;
    private ProviderValue<MedicineReminderDao> mMedicineReminderDao;

    public MedicineReminderSnoozeWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Provider provider = BaseApplication.of(context).getProvider();
        mAppNotificationHandler = provider.lazyGet(AppNotificationHandler.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
    }

    @NonNull
    @Override
    public Result doWork() {
        long medicineReminderId = getInputData().getLong(Keys.LONG_MEDICINE_REMINDER_ID, -1);
        MedicineReminder medicineReminder = mMedicineReminderDao.get().findMedicineReminderById(medicineReminderId);
        if (medicineReminder == null || Boolean.FALSE.equals(medicineReminder.reminderEnabled)) {
            return Result.success();
        }
        AppNotificationHandler appNotificationHandler = mAppNotificationHandler.get();
        appNotificationHandler.cancelNotificationSync(medicineReminder);
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            appNotificationHandler.postMedicineReminder(medicineReminder);
        }
        return Result.success();
    }
}
