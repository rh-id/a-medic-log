package m.co.rh.id.a_medic_log.base.repository;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class MedicineRepository {

    private final ProviderValue<AppDatabase> mAppDatabase;
    private final ProviderValue<MedicineDao> mMedicineDao;
    private final ProviderValue<MedicineReminderDao> mMedicineReminderDao;

    public MedicineRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
    }

    public MedicineState findMedicineStateByMedicineId(long medicineId) {
        MedicineState medicineState = new MedicineState();
        Medicine medicine = mMedicineDao.get().findMedicineById(medicineId);
        List<MedicineReminder> medicineReminders =
                mMedicineReminderDao.get().findMedicineRemindersByMedicineId(medicineId);
        medicineState.updateMedicine(medicine);
        medicineState.updateMedicineReminderList(medicineReminders);
        return medicineState;
    }

    public void insertMedicine(Medicine medicine, List<MedicineReminder> medicineReminders) {
        mAppDatabase.get().runInTransaction(() -> {
            long medsId = mMedicineDao.get().insert(medicine);
            medicine.id = medsId;
            if (medicineReminders != null && !medicineReminders.isEmpty()) {
                for (MedicineReminder medicineReminder : medicineReminders) {
                    medicineReminder.medicineId = medsId;
                    insertMedicineReminder(medicineReminder);
                }
            }
        });
    }

    public void updateMedicine(Medicine medicine, ArrayList<MedicineReminder> medicineReminders) {
        mAppDatabase.get().runInTransaction(() -> {
            mMedicineDao.get().update(medicine);
            long medsId = medicine.id;
            if (medicineReminders != null && !medicineReminders.isEmpty()) {
                for (MedicineReminder medicineReminder : medicineReminders) {
                    medicineReminder.medicineId = medsId;
                    if (medicineReminder.id == null) {
                        insertMedicineReminder(medicineReminder);
                    } else {
                        mMedicineReminderDao.get().update(medicineReminder);
                    }
                }
            }
        });
    }

    public void deleteMedicineByMedicineId(long medicineId) {
        mAppDatabase.get().runInTransaction(() -> {
            mMedicineDao.get().deleteMedicineById(medicineId);
        });
    }

    public void insertMedicineReminder(MedicineReminder medicineReminder) {
        mAppDatabase.get().runInTransaction(() -> {
            long id = mMedicineReminderDao.get().insert(medicineReminder);
            medicineReminder.id = id;
        });
    }
}