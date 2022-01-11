package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewMedicineReminderCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<MedicineDao> mMedicineDao;
    protected ProviderValue<MedicineReminderChangeNotifier> mMedicineReminderChangeNotifier;
    protected BehaviorSubject<String> mStartDateTimeValidSubject;
    protected BehaviorSubject<String> mMessageValidSubject;
    protected BehaviorSubject<String> mReminderDaysValidSubject;

    public NewMedicineReminderCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderChangeNotifier = provider.lazyGet(MedicineReminderChangeNotifier.class);
        mStartDateTimeValidSubject = BehaviorSubject.create();
        mMessageValidSubject = BehaviorSubject.create();
        mReminderDaysValidSubject = BehaviorSubject.create();
    }

    public Single<MedicineReminder> execute(MedicineReminder medicineReminder) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mMedicineDao.get().insertMedicineReminder(medicineReminder);
            mMedicineReminderChangeNotifier.get().medicineReminderAdded(medicineReminder.clone());
            return medicineReminder;
        }));
    }

    public boolean valid(MedicineReminder medicineReminder) {
        boolean isValid = false;
        if (medicineReminder != null) {
            boolean startDateTimeValid = false;
            if (medicineReminder.startDateTime != null) {
                startDateTimeValid = true;
                mStartDateTimeValidSubject.onNext("");
            } else {
                mStartDateTimeValidSubject.onNext(mAppContext.getString(R.string.start_date_time_is_required));
            }
            boolean messageValid = false;
            if (medicineReminder.message != null && !medicineReminder.message.isEmpty()) {
                messageValid = true;
                mMessageValidSubject.onNext("");
            } else {
                mMessageValidSubject.onNext(mAppContext.getString(R.string.message_is_required));
            }
            boolean reminderDaysValid = false;
            if (medicineReminder.reminderDays != null && !medicineReminder.reminderDays.isEmpty()) {
                reminderDaysValid = true;
                mReminderDaysValidSubject.onNext("");
            } else {
                mReminderDaysValidSubject.onNext(mAppContext.getString(R.string.reminder_days_is_required));
            }
            isValid = startDateTimeValid && messageValid && reminderDaysValid;
        }
        return isValid;
    }

    public Flowable<String> getStartDateTimeValid() {
        return Flowable.fromObservable(mStartDateTimeValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getMessageValid() {
        return Flowable.fromObservable(mMessageValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getReminderDaysValid() {
        return Flowable.fromObservable(mReminderDaysValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String startDateTimeValid = mStartDateTimeValidSubject.getValue();
        if (startDateTimeValid != null && !startDateTimeValid.isEmpty()) {
            return startDateTimeValid;
        }
        String messageValid = mMessageValidSubject.getValue();
        if (messageValid != null && !messageValid.isEmpty()) {
            return messageValid;
        }
        String reminderDaysValid = mReminderDaysValidSubject.getValue();
        if (reminderDaysValid != null && !reminderDaysValid.isEmpty()) {
            return reminderDaysValid;
        }
        return "";
    }
}
