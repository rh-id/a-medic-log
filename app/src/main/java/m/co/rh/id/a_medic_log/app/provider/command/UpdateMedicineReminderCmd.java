package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;

public class UpdateMedicineReminderCmd extends NewMedicineReminderCmd {

    public UpdateMedicineReminderCmd(Context context, Provider provider) {
        super(context, provider);
    }

    public Single<MedicineReminder> execute(MedicineReminder medicineReminder) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            MedicineReminder beforeUpdate = mMedicineDao.get().findMedicineReminderById(medicineReminder.id);
            mMedicineDao.get().update(medicineReminder);
            mMedicineReminderChangeNotifier.get().medicineReminderUpdated(beforeUpdate, medicineReminder.clone());
            return medicineReminder;
        }));
    }
}
