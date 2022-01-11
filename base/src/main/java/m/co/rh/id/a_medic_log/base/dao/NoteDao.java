package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;

@Dao
public abstract class NoteDao {

    @Query("SELECT * FROM note where profile_id = :profileId " +
            "ORDER BY entry_date_time DESC LIMIT :limit")
    public abstract List<Note> loadNotesWithLimit(long profileId, int limit);

    @Query("SELECT * FROM note WHERE content LIKE '%'||:search||'%' " +
            "ORDER BY entry_date_time DESC")
    public abstract List<Note> searchNote(String search);

    @Query("SELECT * FROM note WHERE id = :noteId")
    public abstract Note findNoteById(Long noteId);

    @Transaction
    public void insertNote(NoteState noteState) {
        Note note = noteState.getNote();
        List<MedicineState> medicineStates = noteState.getMedicineList();
        long noteId = insert(note);
        note.id = noteId;
        if (medicineStates != null && !medicineStates.isEmpty()) {
            for (MedicineState medicineState : medicineStates) {
                Medicine medicine = medicineState.getMedicine();
                medicine.noteId = noteId;
                long medicineId = insert(medicine);
                medicine.id = medicineId;
                List<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                if (medicineReminders != null && !medicineReminders.isEmpty()) {
                    for (MedicineReminder medicineReminder : medicineReminders) {
                        medicineReminder.medicineId = medicineId;
                        medicineReminder.id = insert(medicineReminder);
                    }
                }
            }
        }
    }

    @Transaction
    public void updateNote(NoteState noteState) {
        Note note = noteState.getNote();
        update(note);
        long noteId = note.id;
        List<Medicine> medicines = findMedicineByNoteId(noteId);
        if (medicines != null && medicines.isEmpty()) {
            deleteMedicineByNoteId(noteId);
            for (Medicine medicine : medicines) {
                long medicineId = medicine.id;
                deleteMedicineReminderByMedicineId(medicineId);
                deleteMedicineIntakeByMedicineId(medicineId);
            }
        }
        List<MedicineState> medicineStates = noteState.getMedicineList();
        if (medicineStates != null && !medicineStates.isEmpty()) {
            for (MedicineState medicineState : medicineStates) {
                Medicine medicine = medicineState.getMedicine();
                medicine.noteId = noteId;
                long medicineId;
                if (medicine.id != null) {
                    medicineId = medicine.id;
                    update(medicine);
                } else {
                    medicineId = insert(medicine);
                }
                medicine.id = medicineId;
                List<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                if (medicineReminders != null && !medicineReminders.isEmpty()) {
                    for (MedicineReminder medicineReminder : medicineReminders) {
                        medicineReminder.medicineId = medicineId;
                        if (medicineReminder.id != null) {
                            update(medicineReminder);
                        } else {
                            medicineReminder.id = insert(medicineReminder);
                        }
                    }
                }
            }
        }
    }

    @Transaction
    public void deleteNote(NoteState noteState) {
        Note note = noteState.getNote();
        delete(note);
        List<Medicine> medicines = findMedicineByNoteId(note.id);
        if (medicines != null && medicines.isEmpty()) {
            deleteMedicineByNoteId(note.id);
            for (Medicine medicine : medicines) {
                long medicineId = medicine.id;
                deleteMedicineReminderByMedicineId(medicineId);
                deleteMedicineIntakeByMedicineId(medicineId);
            }
        }
    }

    @Insert
    protected abstract long insert(Note note);

    @Update
    protected abstract void update(Note note);

    @Delete
    protected abstract void delete(Note note);

    @Insert
    protected abstract long insert(Medicine medicine);

    @Update
    protected abstract void update(Medicine medicine);

    @Query("SELECT * FROM medicine WHERE note_id = :noteId")
    protected abstract List<Medicine> findMedicineByNoteId(long noteId);

    @Query("DELETE FROM medicine WHERE note_id = :noteId")
    protected abstract void deleteMedicineByNoteId(long noteId);

    @Insert
    protected abstract long insert(MedicineReminder medicineReminder);

    @Update
    protected abstract void update(MedicineReminder medicineReminder);


    @Query("DELETE FROM medicine_reminder WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineReminderByMedicineId(long medicineId);

    @Query("DELETE FROM medicine_intake WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineIntakeByMedicineId(long medicineId);
}
