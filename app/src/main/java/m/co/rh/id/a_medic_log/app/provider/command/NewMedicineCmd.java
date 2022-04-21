package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewMedicineCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<MedicineDao> mMedicineDao;
    protected ProviderValue<MedicineChangeNotifier> mMedicineChangeNotifier;
    protected BehaviorSubject<String> mNameValidSubject;

    public NewMedicineCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineChangeNotifier = provider.lazyGet(MedicineChangeNotifier.class);
        mNameValidSubject = BehaviorSubject.create();
    }

    public Single<MedicineState> execute(MedicineState medicineState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mMedicineDao.get().insertMedicine(medicineState.getMedicine(), medicineState.getMedicineReminderList());
            mMedicineChangeNotifier.get().medicineAdded(medicineState.clone());
            return medicineState;
        }));
    }

    public boolean valid(MedicineState medicineState) {
        boolean isValid = false;
        if (medicineState != null) {
            Medicine medicine = medicineState.getMedicine();
            boolean nameValid = false;
            if (medicine.name != null && !medicine.name.isEmpty()) {
                nameValid = true;
                mNameValidSubject.onNext("");
            } else {
                mNameValidSubject.onNext(mAppContext.getString(R.string.name_is_required));
            }
            isValid = nameValid;
        }
        return isValid;
    }

    public Flowable<String> getNameValid() {
        return Flowable.fromObservable(mNameValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String nameValid = mNameValidSubject.getValue();
        if (nameValid != null && !nameValid.isEmpty()) {
            return nameValid;
        }
        return "";
    }
}
