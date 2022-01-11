package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class QueryMedicineCmd {
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<MedicineDao> mMedicineDao;

    public QueryMedicineCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
    }

    public Single<MedicineIntake> lastMedicineIntake(long medicineId) {
        return Single.fromFuture(mExecutorService.get().submit(() ->
                mMedicineDao.get().findLastMedicineIntake(medicineId)));
    }
}
