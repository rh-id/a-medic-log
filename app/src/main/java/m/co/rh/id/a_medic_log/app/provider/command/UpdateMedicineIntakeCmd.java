package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;

public class UpdateMedicineIntakeCmd extends NewMedicineIntakeCmd {

    public UpdateMedicineIntakeCmd(Provider provider) {
        super(provider);
    }

    public Single<MedicineIntake> execute(MedicineIntake medicineIntake) {
        return Single.fromCallable(() -> {
            MedicineIntake before =
                    mMedicineDao.get().findMedicineIntakeById(medicineIntake.id);
            mMedicineDao.get().update(medicineIntake);
            mMedicineIntakeChangeNotifier.get().medicineIntakeUpdated(before,
                    medicineIntake.clone());
            return medicineIntake;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
