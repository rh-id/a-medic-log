package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;

public class QueryMedicineCmd {
    protected ExecutorService mExecutorService;
    protected MedicineDao mMedicineDao;

    public QueryMedicineCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mMedicineDao = provider.get(MedicineDao.class);
    }

    public Single<MedicineIntake> lastMedicineIntake(long medicineId) {
        return Single.fromFuture(mExecutorService.submit(() ->
                mMedicineDao.findLastMedicineIntake(medicineId)));
    }

    public Single<LinkedHashSet<String>> searchMedicineIntakeDescription(String search) {
        return Single.fromFuture(mExecutorService.submit(() ->
        {
            LinkedHashSet<String> descLinkedHashSet = new LinkedHashSet<>();
            List<MedicineIntake> medicineIntakes = mMedicineDao.searchMedicineIntake(search);
            if (!medicineIntakes.isEmpty()) {
                for (MedicineIntake medicineIntake : medicineIntakes) {
                    descLinkedHashSet.add(medicineIntake.description);
                }
            }
            return descLinkedHashSet;
        }));
    }
}
