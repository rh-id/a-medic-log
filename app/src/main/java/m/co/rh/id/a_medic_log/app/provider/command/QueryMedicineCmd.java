package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;

public class QueryMedicineCmd {
    private static final int SEARCH_LIMIT = 10;

    protected ExecutorService mExecutorService;
    protected MedicineDao mMedicineDao;
    protected MedicineIntakeDao mMedicineIntakeDao;
    protected MedicineReminderDao mMedicineReminderDao;

    public QueryMedicineCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mMedicineDao = provider.get(MedicineDao.class);
        mMedicineIntakeDao = provider.get(MedicineIntakeDao.class);
        mMedicineReminderDao = provider.get(MedicineReminderDao.class);
    }

    public Single<MedicineIntake> lastMedicineIntake(long medicineId) {
        return Single.fromCallable(() ->
                mMedicineIntakeDao.findLastMedicineIntake(medicineId)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<String>> searchMedicineIntakeDescription(String search) {
        return Single.fromCallable(() -> new LinkedHashSet<>(
                mMedicineIntakeDao.searchDistinctMedicineIntakeDescriptions(search, SEARCH_LIMIT)))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<String>> searchMedicineName(String search) {
        return Single.fromCallable(() -> new LinkedHashSet<>(
                mMedicineDao.searchDistinctMedicineNames(search, SEARCH_LIMIT)))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<String>> searchMedicineReminderMessage(String search) {
        return Single.fromCallable(() -> new LinkedHashSet<>(
                mMedicineReminderDao.searchDistinctMedicineReminderMessages(search, SEARCH_LIMIT)))
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
