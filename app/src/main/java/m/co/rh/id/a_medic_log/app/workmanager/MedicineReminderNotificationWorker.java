package m.co.rh.id.a_medic_log.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.Date;

import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class MedicineReminderNotificationWorker extends Worker {
    private ProviderValue<AppNotificationHandler> mAppNotificationHandler;
    private ProviderValue<MedicineDao> mMedicineDao;

    public MedicineReminderNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Provider provider = BaseApplication.of(context).getProvider();
        mAppNotificationHandler = provider.lazyGet(AppNotificationHandler.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
    }

    @NonNull
    @Override
    public Result doWork() {
        long medicineReminderId = getInputData().getLong(Keys.LONG_MEDICINE_REMINDER_ID, -1);
        MedicineReminder medicineReminder = mMedicineDao.get().findMedicineReminderById(medicineReminderId);
        if (medicineReminder != null) {
            Date today = new Date();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(today);
            int currentDay = calendar.get(Calendar.DAY_OF_WEEK);
            if (medicineReminder.reminderDays.contains(currentDay)) {
                AppNotificationHandler appNotificationHandler = mAppNotificationHandler.get();
                appNotificationHandler.cancelNotificationSync(medicineReminder);
                appNotificationHandler.postMedicineReminder(medicineReminder);
            }
        }

        return Result.success();
    }
}
