package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.state.MedicineState;

@Dao
public abstract class MedicineDao {

    @Query("SELECT * FROM medicine WHERE note_id = :noteId ORDER BY created_date_time")
    public abstract List<Medicine> findMedicinesByNoteId(long noteId);

    @Query("SELECT * FROM medicine_reminder WHERE medicine_id = :medicineId ORDER BY created_date_time DESC")
    public abstract List<MedicineReminder> findMedicineRemindersByMedicineId(long medicineId);

    @Query("SELECT * FROM medicine_reminder WHERE id = :id")
    public abstract MedicineReminder findMedicineReminderById(long id);

    @Query("SELECT * FROM medicine WHERE id = :id")
    public abstract Medicine findMedicineById(long id);

    @Query("SELECT * FROM medicine_intake WHERE medicine_id = :medicineId ORDER BY taken_date_time DESC " +
            "LIMIT 1")
    public abstract MedicineIntake findLastMedicineIntake(long medicineId);

    @Query("SELECT * FROM medicine_intake WHERE id = :id")
    public abstract MedicineIntake findMedicineIntakeById(long id);

    @Query("SELECT * FROM medicine_intake ORDER BY created_date_time DESC LIMIT :limit")
    public abstract List<MedicineIntake> findMedicineIntakesByWithLimit(int limit);

    @Query("SELECT * FROM medicine_intake WHERE medicine_id = :medicineId ORDER BY created_date_time DESC LIMIT :limit")
    public abstract List<MedicineIntake> findMedicineIntakesByMedicineIdWithLimit(long medicineId, int limit);

    @Query("SELECT * FROM medicine_intake WHERE description LIKE '%'||:search||'%' ORDER BY created_date_time DESC")
    public abstract List<MedicineIntake> searchMedicineIntakeDescription(String search);

    @Query("SELECT * FROM medicine WHERE name LIKE '%'||:search||'%' ORDER BY created_date_time DESC")
    public abstract List<Medicine> searchMedicineName(String search);

    @Transaction
    public MedicineState findMedicineStateByMedicineId(long medicineId) {
        MedicineState medicineState = new MedicineState();
        Medicine medicine = findMedicineById(medicineId);
        List<MedicineReminder> medicineReminders = findMedicineRemindersByMedicineId(medicineId);
        medicineState.updateMedicine(medicine);
        medicineState.updateMedicineReminderList(medicineReminders);
        return medicineState;
    }

    @Transaction
    public void insertMedicine(Medicine medicine, List<MedicineReminder> medicineReminders) {
        long medsId = insert(medicine);
        medicine.id = medsId;
        if (medicineReminders != null && !medicineReminders.isEmpty()) {
            for (MedicineReminder medicineReminder : medicineReminders) {
                medicineReminder.medicineId = medsId;
                insertMedicineReminder(medicineReminder);
            }
        }
    }

    @Transaction
    public void updateMedicine(Medicine medicine, ArrayList<MedicineReminder> medicineReminders) {
        update(medicine);
        long medsId = medicine.id;
        if (medicineReminders != null && !medicineReminders.isEmpty()) {
            for (MedicineReminder medicineReminder : medicineReminders) {
                medicineReminder.medicineId = medsId;
                if (medicineReminder.id == null) {
                    insertMedicineReminder(medicineReminder);
                } else {
                    update(medicineReminder);
                }
            }
        }
    }

    @Transaction
    public void deleteMedicineByMedicineId(long medicineId) {
        deleteMedicineById(medicineId);
        deleteMedicineReminderByMedicineId(medicineId);
        deleteMedicineIntakeByMedicineId(medicineId);
    }


    @Insert
    protected abstract long insert(Medicine medicine);

    @Update
    public abstract void update(Medicine medicine);

    @Query("DELETE FROM medicine WHERE id = :id")
    protected abstract void deleteMedicineById(long id);

    @Transaction
    public void insertMedicineReminder(MedicineReminder medicineReminder) {
        medicineReminder.id = insert(medicineReminder);
    }

    @Insert
    protected abstract long insert(MedicineReminder medicineReminder);

    @Update
    public abstract void update(MedicineReminder medicineReminder);

    @Delete
    public abstract void delete(MedicineReminder medicineReminder);

    @Query("DELETE FROM medicine_reminder WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineReminderByMedicineId(long medicineId);

    @Insert
    public abstract void insert(MedicineIntake medicineIntake);

    @Update
    public abstract void update(MedicineIntake medicineIntake);

    @Delete
    public abstract void delete(MedicineIntake medicineIntake);

    @Query("DELETE FROM medicine_intake WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineIntakeByMedicineId(long medicineId);
}
