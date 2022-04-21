package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteMedicineReminderCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<MedicineDao> mMedicineDao;
    protected ProviderValue<MedicineReminderChangeNotifier> mMedicineReminderChangeNotifier;

    public DeleteMedicineReminderCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderChangeNotifier = provider.lazyGet(MedicineReminderChangeNotifier.class);
    }

    public Single<MedicineReminder> execute(MedicineReminder medicineReminder) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mMedicineDao.get().delete(medicineReminder);
            mMedicineReminderChangeNotifier.get().medicineReminderDeleted(medicineReminder.clone());
            return medicineReminder;
        }));
    }
}
