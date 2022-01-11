package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.aprovider.Provider;

public class UpdateMedicineCmd extends NewMedicineCmd {

    public UpdateMedicineCmd(Context context, Provider provider) {
        super(context, provider);
    }

    public Single<MedicineState> execute(MedicineState medicineState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            MedicineState beforeUpdate = mMedicineDao.get().findMedicineStateByMedicineId(medicineState.getMedicineId());
            mMedicineDao.get().updateMedicine(medicineState.getMedicine(), medicineState.getMedicineReminderList());
            mMedicineChangeNotifier.get().medicineUpdated(beforeUpdate, medicineState.clone());
            return medicineState;
        }));
    }
}
