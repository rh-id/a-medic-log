package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
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
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<MedicineIntake> medicineIntakes = mMedicineDao.searchMedicineIntakeDescription(search);
            if (!medicineIntakes.isEmpty()) {
                for (MedicineIntake medicineIntake : medicineIntakes) {
                    linkedHashSet.add(medicineIntake.description);
                }
            }
            return linkedHashSet;
        }));
    }

    public Single<LinkedHashSet<String>> searchMedicineName(String search) {
        return Single.fromFuture(mExecutorService.submit(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<Medicine> medicines = mMedicineDao.searchMedicineName(search);
            if (!medicines.isEmpty()) {
                for (Medicine medicine : medicines) {
                    linkedHashSet.add(medicine.name);
                }
            }
            return linkedHashSet;
        }));
    }

    public Single<LinkedHashSet<String>> searchMedicineReminderMessage(String search) {
        return Single.fromFuture(mExecutorService.submit(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<MedicineReminder> medicineReminders = mMedicineDao.searchMedicineReminderMessage(search);
            if (!medicineReminders.isEmpty()) {
                for (MedicineReminder medicineReminder : medicineReminders) {
                    linkedHashSet.add(medicineReminder.message);
                }
            }
            return linkedHashSet;
        }));
    }
}
