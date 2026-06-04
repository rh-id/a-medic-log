package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.aprovider.Provider;

public class QueryMedicineCmd {
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
        return Single.fromCallable(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<MedicineIntake> medicineIntakes = mMedicineIntakeDao.searchMedicineIntakeDescription(search);
            if (!medicineIntakes.isEmpty()) {
                for (MedicineIntake medicineIntake : medicineIntakes) {
                    linkedHashSet.add(medicineIntake.description);
                }
            }
            return linkedHashSet;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<String>> searchMedicineName(String search) {
        return Single.fromCallable(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<Medicine> medicines = mMedicineDao.searchMedicineName(search);
            if (!medicines.isEmpty()) {
                for (Medicine medicine : medicines) {
                    linkedHashSet.add(medicine.name);
                }
            }
            return linkedHashSet;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<String>> searchMedicineReminderMessage(String search) {
        return Single.fromCallable(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<MedicineReminder> medicineReminders = mMedicineReminderDao.searchMedicineReminderMessage(search);
            if (!medicineReminders.isEmpty()) {
                for (MedicineReminder medicineReminder : medicineReminders) {
                    linkedHashSet.add(medicineReminder.message);
                }
            }
            return linkedHashSet;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
