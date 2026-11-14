package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
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

    @Query("SELECT name FROM medicine WHERE name LIKE '%'||:search||'%' " +
            "GROUP BY name ORDER BY MAX(created_date_time) DESC, MAX(id) DESC LIMIT :limit")
    public abstract List<String> searchDistinctMedicineNames(String search, int limit);

    /**
     * Returns all medicines whose id is in the given list (queried in batches; deduplicates and tolerates null/empty input).
     */
    public List<Medicine> findMedicinesByIds(List<Long> medicineIds) {
        return DaoBatchQueryUtil.queryInBatches(medicineIds, this::getMedicinesByIds);
    }

    @Query("SELECT * FROM medicine WHERE id IN (:medicineIds)")
    abstract List<Medicine> getMedicinesByIds(List<Long> medicineIds);

    @Query("SELECT medicine.* FROM medicine INNER JOIN note " +
            "ON medicine.note_id = note.id WHERE note.profile_id = :profileId " +
            "ORDER BY medicine.created_date_time DESC")
    public abstract List<Medicine> findMedicinesByProfileId(long profileId);

    @Query("SELECT COUNT(*) FROM medicine INNER JOIN note " +
            "ON medicine.note_id = note.id WHERE note.profile_id = :profileId")
    public abstract int countMedicinesByProfileId(long profileId);

    @Insert
    public abstract long insert(Medicine medicine);

    @Update
    public abstract void update(Medicine medicine);

    @Query("DELETE FROM medicine WHERE id = :id")
    public abstract void deleteMedicineById(long id);
}
