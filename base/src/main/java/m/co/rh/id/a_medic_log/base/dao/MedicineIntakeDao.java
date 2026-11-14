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

    @Query("SELECT * FROM medicine_intake WHERE medicine_id = :medicineId ORDER BY created_date_time DESC")
    public abstract List<MedicineIntake> findMedicineIntakesByMedicineId(long medicineId);

    @Query("SELECT description FROM medicine_intake WHERE description LIKE '%'||:search||'%' " +
            "GROUP BY description ORDER BY MAX(created_date_time) DESC, MAX(id) DESC LIMIT :limit")
    public abstract List<String> searchDistinctMedicineIntakeDescriptions(String search, int limit);

    @Query("SELECT * FROM medicine_intake WHERE medicine_id = :medicineId " +
            "AND description LIKE '%'||:search||'%' " +
            "ORDER BY created_date_time DESC, id DESC LIMIT :limit")
    public abstract List<MedicineIntake> searchMedicineIntakeDescriptionWithMedicineLimit(long medicineId,
                                                                                           String search, int limit);

    @Query("SELECT * FROM medicine_intake WHERE description LIKE '%'||:search||'%' " +
            "ORDER BY created_date_time DESC, id DESC LIMIT :limit")
    public abstract List<MedicineIntake> searchMedicineIntakeDescriptionWithLimit(String search, int limit);

    @Query("SELECT * FROM medicine_intake WHERE taken_date_time BETWEEN :from AND :to " +
            "ORDER BY taken_date_time DESC")
    public abstract List<MedicineIntake> findMedicineIntakesByTakenDateTimeBetween(long from, long to);

    @Query("SELECT medicine_intake.* FROM medicine_intake " +
            "INNER JOIN medicine ON medicine_intake.medicine_id = medicine.id " +
            "INNER JOIN note ON medicine.note_id = note.id " +
            "WHERE note.profile_id = :profileId " +
            "AND medicine_intake.taken_date_time BETWEEN :from AND :to " +
            "ORDER BY medicine_intake.taken_date_time DESC")
    public abstract List<MedicineIntake> findMedicineIntakesByProfileIdAndTakenDateTimeBetween(
            long profileId, long from, long to);

    @Query("SELECT COUNT(*) FROM medicine_intake INNER JOIN medicine " +
            "ON medicine_intake.medicine_id = medicine.id WHERE medicine.note_id = :noteId")
    public abstract int countMedicineIntakesByNoteId(long noteId);

    @Query("SELECT COUNT(*) FROM medicine_intake WHERE medicine_id = :medicineId")
    public abstract int countMedicineIntakesByMedicineId(long medicineId);

    @Query("SELECT COUNT(*) FROM medicine_intake INNER JOIN medicine " +
            "ON medicine_intake.medicine_id = medicine.id INNER JOIN note " +
            "ON medicine.note_id = note.id WHERE note.profile_id = :profileId")
    public abstract int countMedicineIntakesByProfileId(long profileId);

    @Query("SELECT medicine_intake.* FROM medicine_intake " +
            "INNER JOIN medicine ON medicine_intake.medicine_id = medicine.id " +
            "INNER JOIN note ON medicine.note_id = note.id " +
            "WHERE note.profile_id = :profileId " +
            "AND medicine_intake.id = (SELECT last_intake.id FROM medicine_intake AS last_intake " +
            "WHERE last_intake.medicine_id = medicine_intake.medicine_id " +
            "ORDER BY last_intake.taken_date_time DESC, last_intake.id DESC LIMIT 1) " +
            "ORDER BY medicine_intake.taken_date_time DESC")
    public abstract List<MedicineIntake> findLastIntakesByProfileId(long profileId);

    @Insert
    public abstract long insert(MedicineIntake medicineIntake);

    @Update
    public abstract void update(MedicineIntake medicineIntake);

    @Delete
    public abstract void delete(MedicineIntake medicineIntake);
}