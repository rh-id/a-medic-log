package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteMedicineIntakeCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<MedicineDao> mMedicineDao;
    protected ProviderValue<MedicineIntakeChangeNotifier> mMedicineIntakeChangeNotifier;

    public DeleteMedicineIntakeCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineIntakeChangeNotifier = provider.lazyGet(MedicineIntakeChangeNotifier.class);
    }

    public Single<MedicineIntake> execute(MedicineIntake medicineIntake) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mMedicineDao.get().delete(medicineIntake);
            mMedicineIntakeChangeNotifier.get().medicineIntakeDeleted(medicineIntake.clone());
            return medicineIntake;
        }));
    }
}
