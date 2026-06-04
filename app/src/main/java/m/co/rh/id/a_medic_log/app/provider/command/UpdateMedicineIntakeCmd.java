package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class UpdateMedicineIntakeCmd extends NewMedicineIntakeCmd {
    protected ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;

    public UpdateMedicineIntakeCmd(Provider provider) {
        super(provider);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
    }

    public Single<MedicineIntake> execute(MedicineIntake medicineIntake) {
        return Single.fromCallable(() -> {
            MedicineIntake before =
                    mMedicineIntakeDao.get().findMedicineIntakeById(medicineIntake.id);
            mMedicineIntakeDao.get().update(medicineIntake);
            mMedicineIntakeChangeNotifier.get().medicineIntakeUpdated(before,
                    medicineIntake.clone());
            return medicineIntake;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
