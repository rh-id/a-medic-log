package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;

@Dao
public abstract class MedicineIntakeDao {

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

    @Insert
    public abstract long insert(MedicineIntake medicineIntake);

    @Update
    public abstract void update(MedicineIntake medicineIntake);

    @Delete
    public abstract void delete(MedicineIntake medicineIntake);
}