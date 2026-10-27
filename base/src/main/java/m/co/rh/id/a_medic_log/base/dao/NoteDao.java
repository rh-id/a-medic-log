package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.Note;

@Dao
public abstract class NoteDao {

    @Query("SELECT * FROM note where profile_id = :profileId " +
            "ORDER BY entry_date_time DESC LIMIT :limit")
    public abstract List<Note> findNotesByProfileIdWithLimit(long profileId, int limit);

    @Query("SELECT * FROM note where profile_id = :profileId " +
            "ORDER BY entry_date_time DESC")
    public abstract List<Note> findNotesByProfileId(long profileId);

    @Query("SELECT * FROM note " +
            "ORDER BY entry_date_time DESC LIMIT :limit")
    public abstract List<Note> findNotesWithLimit(int limit);

    @Query("SELECT * FROM note WHERE profile_id = :profileId " +
            "AND (content LIKE '%'||:search||'%' " +
            "OR EXISTS (SELECT 1 FROM note_tag " +
            "WHERE note_tag.note_id = note.id AND tag LIKE '%'||:search||'%') " +
            "OR EXISTS (SELECT 1 FROM medicine " +
            "WHERE medicine.note_id = note.id " +
            "AND (name LIKE '%'||:search||'%' OR description LIKE '%'||:search||'%')) " +
            "OR EXISTS (SELECT 1 FROM medicine " +
            "JOIN medicine_intake ON medicine_intake.medicine_id = medicine.id " +
            "WHERE medicine.note_id = note.id " +
            "AND medicine_intake.description LIKE '%'||:search||'%')) " +
            "ORDER BY entry_date_time DESC, id DESC LIMIT :limit")
    public abstract List<Note> searchNotesWithProfileLimit(long profileId, String search, int limit);

    @Query("SELECT * FROM note WHERE (content LIKE '%'||:search||'%' " +
            "OR EXISTS (SELECT 1 FROM note_tag " +
            "WHERE note_tag.note_id = note.id AND tag LIKE '%'||:search||'%') " +
            "OR EXISTS (SELECT 1 FROM medicine " +
            "WHERE medicine.note_id = note.id " +
            "AND (name LIKE '%'||:search||'%' OR description LIKE '%'||:search||'%')) " +
            "OR EXISTS (SELECT 1 FROM medicine " +
            "JOIN medicine_intake ON medicine_intake.medicine_id = medicine.id " +
            "WHERE medicine.note_id = note.id " +
            "AND medicine_intake.description LIKE '%'||:search||'%')) " +
            "ORDER BY entry_date_time DESC, id DESC LIMIT :limit")
    public abstract List<Note> searchNotesWithLimit(String search, int limit);

    @Query("SELECT * FROM note ORDER BY entry_date_time DESC")
    public abstract List<Note> findAllNotes();

    @Query("SELECT COUNT(*) FROM note WHERE profile_id = :profileId")
    public abstract int countNotesByProfileId(long profileId);

    @Query("SELECT * FROM note WHERE id = :noteId")
    public abstract Note findNoteById(Long noteId);

    @Insert
    public abstract long insert(Note note);

    @Update
    public abstract void update(Note note);

    @Delete
    public abstract void delete(Note note);
}