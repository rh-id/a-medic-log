package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.Medicine;

@Dao
public abstract class MedicineDao {

    @Query("SELECT * FROM medicine WHERE note_id = :noteId ORDER BY created_date_time")
    public abstract List<Medicine> findMedicinesByNoteId(long noteId);

    @Query("SELECT * FROM medicine WHERE id = :id")
    public abstract Medicine findMedicineById(long id);

    @Query("SELECT * FROM medicine WHERE name LIKE '%'||:search||'%' ORDER BY created_date_time DESC")
    public abstract List<Medicine> searchMedicineName(String search);

    @Insert
    public abstract long insert(Medicine medicine);

    @Update
    public abstract void update(Medicine medicine);

    @Query("DELETE FROM medicine WHERE id = :id")
    public abstract void deleteMedicineById(long id);
}
