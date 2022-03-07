package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
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

    @Query("SELECT * FROM note WHERE id in (:noteIds)")
    public abstract List<Note> findNoteByIds(Collection<Long> noteIds);

    @Query("SELECT COUNT(*) FROM note")
    public abstract int countNote();

    @Query("SELECT COUNT(*) FROM note_tag")
    public abstract int countNoteTag();

    @Transaction
    public void insertNote(NoteState noteState) {
        Note note = noteState.getNote();
        Set<NoteTag> noteTags = noteState.getNoteTagSet();
        List<NoteAttachmentState> noteAttachmentStates = noteState.getNoteAttachmentStates();
        List<MedicineState> medicineStates = noteState.getMedicineList();
        long noteId = insert(note);
        note.id = noteId;
        if (noteTags != null && !noteTags.isEmpty()) {
            for (NoteTag noteTag : noteTags) {
                noteTag.noteId = noteId;
                noteTag.id = insert(noteTag);
            }
        }
        if (noteAttachmentStates != null && !noteAttachmentStates.isEmpty()) {
            for (NoteAttachmentState noteAttachmentState : noteAttachmentStates) {
                NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
                noteAttachment.noteId = noteId;
                insertNoteAttachment(noteAttachmentState);
            }
        }
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
        // only updating note information, medicine and others are updated,deleted,inserted fom UI
        Note note = noteState.getNote();
        update(note);
    }

    @Transaction
    public void deleteNote(NoteState noteState) {
        Note note = noteState.getNote();
        delete(note);
        long noteId = note.id;
        deleteNoteTagByNoteId(noteId);
        List<NoteAttachment> noteAttachments = findNoteAttachmentsByNoteId(noteId);
        if (noteAttachments != null && !noteAttachments.isEmpty()) {
            deleteNoteAttachmentsByNoteId(noteId);
            for (NoteAttachment noteAttachment : noteAttachments) {
                deleteNoteAttachmentFilesByAttachmentId(noteAttachment.id);
            }
        }
        List<Medicine> medicines = findMedicinesByNoteId(noteId);
        if (medicines != null && !medicines.isEmpty()) {
            deleteMedicinesByNoteId(noteId);
            for (Medicine medicine : medicines) {
                long medicineId = medicine.id;
                deleteMedicineReminderByMedicineId(medicineId);
                deleteMedicineIntakeByMedicineId(medicineId);
            }
        }
    }

    @Transaction
    public void insertNoteTag(NoteTag noteTag) {
        noteTag.id = insert(noteTag);
    }

    @Transaction
    public void insertNoteAttachment(NoteAttachmentState noteAttachmentState) {
        NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
        long noteAttachmentId = insert(noteAttachment);
        noteAttachment.id = noteAttachmentId;
        Collection<NoteAttachmentFile> noteAttachmentFiles = noteAttachmentState.getNoteAttachmentFiles();
        if (noteAttachmentFiles != null && !noteAttachmentFiles.isEmpty()) {
            for (NoteAttachmentFile noteAttachmentFile : noteAttachmentFiles) {
                noteAttachmentFile.attachmentId = noteAttachmentId;
                noteAttachmentFile.id = insert(noteAttachmentFile);
            }
        }
    }

    @Transaction
    public void deleteNoteAttachment(NoteAttachmentState noteAttachmentState) {
        NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
        Long noteAttachmentId = noteAttachment.id;
        if (noteAttachmentId != null) {
            deleteNoteAttachmentFilesByAttachmentId(noteAttachmentId);
            delete(noteAttachment);
        }
    }

    @Insert
    protected abstract long insert(Note note);

    @Update
    protected abstract void update(Note note);

    @Delete
    protected abstract void delete(Note note);

    @Insert
    protected abstract long insert(NoteTag noteTag);

    @Update
    protected abstract void update(NoteTag noteTag);

    @Delete
    public abstract void delete(NoteTag noteTag);

    @Query("DELETE FROM note_tag WHERE note_id = :noteId")
    protected abstract void deleteNoteTagByNoteId(long noteId);

    @Query("DELETE FROM note_tag WHERE id = :id")
    protected abstract void deleteNoteTagById(long id);

    @Query("SELECT * FROM note_tag WHERE note_id = :noteId")
    public abstract List<NoteTag> findNoteTagsByNoteId(long noteId);

    @Query("SELECT * FROM note_tag WHERE tag LIKE '%'||:search||'%' " +
            "ORDER BY tag ASC")
    public abstract List<NoteTag> searchNoteTag(String search);

    @Insert
    protected abstract long insert(NoteAttachment noteAttachment);

    @Update
    public abstract void update(NoteAttachment noteAttachment);

    @Delete
    protected abstract void delete(NoteAttachment noteAttachment);

    @Query("SELECT * FROM note_attachment LIMIT :limit")
    public abstract List<NoteAttachment> findNoteAttachmentsWithLimit(int limit);

    @Query("SELECT * FROM note_attachment WHERE note_id = :noteId LIMIT :limit")
    public abstract List<NoteAttachment> findNoteAttachmentsByNoteIdWithLimit(long noteId, int limit);

    @Query("SELECT * FROM note_attachment WHERE note_id = :noteId")
    public abstract List<NoteAttachment> findNoteAttachmentsByNoteId(long noteId);

    @Query("DELETE FROM note_attachment WHERE note_id = :noteId")
    protected abstract void deleteNoteAttachmentsByNoteId(long noteId);

    @Query("DELETE FROM note_attachment WHERE id = :id")
    protected abstract void deleteNoteAttachmentById(long id);

    @Insert
    public abstract long insert(NoteAttachmentFile noteAttachmentFile);

    @Update
    protected abstract void update(NoteAttachmentFile noteAttachmentFile);

    @Delete
    public abstract void delete(NoteAttachmentFile noteAttachmentFile);

    @Query("SELECT * FROM note_attachment_file LIMIT :limit")
    public abstract List<NoteAttachmentFile> findNoteAttachmentFilesWithLimit(int limit);

    @Query("SELECT * FROM note_attachment_file WHERE attachment_id = :attachmentId LIMIT :limit")
    public abstract List<NoteAttachmentFile> findNoteAttachmentFilesByAttachmentIdWithLimit(long attachmentId, int limit);

    @Query("SELECT * FROM note_attachment_file WHERE attachment_id = :attachmentId")
    public abstract List<NoteAttachmentFile> findNoteAttachmentFilesByAttachmentId(long attachmentId);

    @Query("SELECT * FROM note_attachment_file WHERE file_name = :fileName")
    public abstract NoteAttachmentFile findNoteAttachmentFileByFileName(String fileName);

    @Query("DELETE FROM note_attachment_file WHERE attachment_id = :attachmentId")
    protected abstract void deleteNoteAttachmentFilesByAttachmentId(long attachmentId);

    @Insert
    protected abstract long insert(Medicine medicine);

    @Update
    protected abstract void update(Medicine medicine);

    @Query("SELECT * FROM medicine WHERE note_id = :noteId")
    protected abstract List<Medicine> findMedicinesByNoteId(long noteId);

    @Query("DELETE FROM medicine WHERE note_id = :noteId")
    protected abstract void deleteMedicinesByNoteId(long noteId);

    @Query("DELETE FROM medicine WHERE id = :id")
    protected abstract void deleteMedicineById(long id);

    @Insert
    protected abstract long insert(MedicineReminder medicineReminder);

    @Update
    protected abstract void update(MedicineReminder medicineReminder);

    @Query("DELETE FROM medicine_reminder WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineReminderByMedicineId(long medicineId);

    @Query("DELETE FROM medicine_intake WHERE medicine_id = :medicineId")
    protected abstract void deleteMedicineIntakeByMedicineId(long medicineId);
}
