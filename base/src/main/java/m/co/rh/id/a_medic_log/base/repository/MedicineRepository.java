package m.co.rh.id.a_medic_log.base.repository;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
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
    private final ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;

    public MedicineRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
    }

    /**
     * Loads a fresh medicine state with its reminders; the returned state is never shared
     */
    public MedicineState queryMedicineInfo(long medicineId) {
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

    /**
     * Counts the records owned by the given medicine; the reminder count
     * comes from the medicine state, intakes are not part of the state
     * (paged history) so they are counted via a DAO query
     * <p>
     * The state is passed by callers that have just read it, so the
     * reminder count stays consistent with what the UI already shows;
     * unlike queryNoteCounts/queryProfileCounts this takes a state instead
     * of an id because the matched reminders are already in it
     */
    public MedicineCounts queryMedicineCounts(MedicineState medicineState) {
        return new MedicineCounts(
                medicineState.getMedicineReminderList().size(),
                mMedicineIntakeDao.get().countMedicineIntakesByMedicineId(medicineState.getMedicineId()));
    }

    /**
     * Counts of the records owned by a medicine
     */
    public static class MedicineCounts {
        public final int reminderCount;
        public final int intakeCount;

        public MedicineCounts(int reminderCount, int intakeCount) {
            this.reminderCount = reminderCount;
            this.intakeCount = intakeCount;
        }
    }
}
