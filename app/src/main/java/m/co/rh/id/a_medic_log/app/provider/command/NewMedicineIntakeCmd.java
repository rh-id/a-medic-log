package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewMedicineIntakeCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<MedicineDao> mMedicineDao;
    protected ProviderValue<MedicineIntakeChangeNotifier> mMedicineIntakeChangeNotifier;
    protected BehaviorSubject<String> mTakenDateTimeValidSubject;
    protected BehaviorSubject<String> mDescriptionValidSubject;

    public NewMedicineIntakeCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineIntakeChangeNotifier = provider.lazyGet(MedicineIntakeChangeNotifier.class);
        mTakenDateTimeValidSubject = BehaviorSubject.create();
        mDescriptionValidSubject = BehaviorSubject.create();
    }

    public Single<MedicineIntake> execute(MedicineIntake medicineIntake) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mMedicineDao.get().insert(medicineIntake);
            mMedicineIntakeChangeNotifier.get().medicineIntakeAdded(medicineIntake.clone());
            return medicineIntake;
        }));
    }

    public boolean valid(MedicineIntake medicineIntake) {
        boolean isValid = false;
        if (medicineIntake != null) {
            boolean takenDateTimeValid = false;
            if (medicineIntake.takenDateTime != null) {
                takenDateTimeValid = true;
                mTakenDateTimeValidSubject.onNext("");
            } else {
                mTakenDateTimeValidSubject.onNext(mAppContext.getString(R.string.taken_date_time_is_required));
            }
            boolean descriptionValid = false;
            if (medicineIntake.description != null && !medicineIntake.description.isEmpty()) {
                descriptionValid = true;
                mDescriptionValidSubject.onNext("");
            } else {
                mDescriptionValidSubject.onNext(mAppContext.getString(R.string.description_is_required));
            }
            isValid = takenDateTimeValid && descriptionValid;
        }
        return isValid;
    }

    public Flowable<String> getTakenDateTimeValid() {
        return Flowable.fromObservable(mTakenDateTimeValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getDescriptionValid() {
        return Flowable.fromObservable(mDescriptionValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String startDateTimeValid = mTakenDateTimeValidSubject.getValue();
        if (startDateTimeValid != null && !startDateTimeValid.isEmpty()) {
            return startDateTimeValid;
        }
        String descriptionValid = mDescriptionValidSubject.getValue();
        if (descriptionValid != null && !descriptionValid.isEmpty()) {
            return descriptionValid;
        }
        return "";
    }
}
