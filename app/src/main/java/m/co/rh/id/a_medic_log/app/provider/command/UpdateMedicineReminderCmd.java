package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class UpdateMedicineReminderCmd extends NewMedicineReminderCmd {
    protected ProviderValue<MedicineReminderDao> mMedicineReminderDao;

    public UpdateMedicineReminderCmd(Provider provider) {
        super(provider);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
    }

    public Single<MedicineReminder> execute(MedicineReminder medicineReminder) {
        return Single.fromCallable(() -> {
            MedicineReminder beforeUpdate = mMedicineReminderDao.get().findMedicineReminderById(medicineReminder.id);
            mMedicineReminderDao.get().update(medicineReminder);
            mMedicineReminderChangeNotifier.get().medicineReminderUpdated(beforeUpdate, medicineReminder.clone());
            return medicineReminder;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
