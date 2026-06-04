package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;

@Dao
public abstract class MedicineReminderDao {

    @Query("SELECT * FROM medicine_reminder WHERE medicine_id = :medicineId ORDER BY created_date_time DESC")
    public abstract List<MedicineReminder> findMedicineRemindersByMedicineId(long medicineId);

    @Query("SELECT * FROM medicine_reminder WHERE id = :id")
    public abstract MedicineReminder findMedicineReminderById(long id);

    @Query("SELECT * FROM medicine_reminder WHERE message LIKE '%'||:search||'%' ORDER BY created_date_time DESC")
    public abstract List<MedicineReminder> searchMedicineReminderMessage(String search);

    @Insert
    public abstract long insert(MedicineReminder medicineReminder);

    @Update
    public abstract void update(MedicineReminder medicineReminder);

    @Delete
    public abstract void delete(MedicineReminder medicineReminder);
}