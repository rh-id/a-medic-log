package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;

public class UpdateMedicineIntakeCmd extends NewMedicineIntakeCmd {

    public UpdateMedicineIntakeCmd(Context context, Provider provider) {
        super(context, provider);
    }

    public Single<MedicineIntake> execute(MedicineIntake medicineIntake) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            MedicineIntake before =
                    mMedicineDao.get().findMedicineIntakeById(medicineIntake.id);
            mMedicineDao.get().update(medicineIntake);
            mMedicineIntakeChangeNotifier.get().medicineIntakeUpdated(before,
                    medicineIntake.clone());
            return medicineIntake;
        }));
    }
}
