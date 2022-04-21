package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteMedicineCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<MedicineDao> mMedicineDao;
    protected ProviderValue<MedicineChangeNotifier> mMedicineChangeNotifier;

    public DeleteMedicineCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineChangeNotifier = provider.lazyGet(MedicineChangeNotifier.class);
    }

    public Single<MedicineState> execute(MedicineState medicineState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mMedicineDao.get().deleteMedicineByMedicineId(medicineState.getMedicineId());
            mMedicineChangeNotifier.get().medicineDeleted(medicineState.clone());
            return medicineState;
        }));
    }
}
