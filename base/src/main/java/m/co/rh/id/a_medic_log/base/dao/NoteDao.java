package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Collection;
import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.Note;

@Dao
public abstract class NoteDao {

    @Query("SELECT * FROM note where profile_id = :profileId " +
            "ORDER BY entry_date_time DESC LIMIT :limit")
    public abstract List<Note> findNotesByProfileIdWithLimit(long profileId, int limit);

    @Query("SELECT * FROM note " +
            "ORDER BY entry_date_time DESC LIMIT :limit")
    public abstract List<Note> findNotesWithLimit(int limit);

    @Query("SELECT * FROM note WHERE content LIKE '%'||:search||'%' " +
            "ORDER BY entry_date_time DESC")
    public abstract List<Note> searchNote(String search);

    @Query("SELECT * FROM note WHERE id = :noteId")
    public abstract Note findNoteById(Long noteId);

    @Query("SELECT * FROM note WHERE id in (:noteIds)")
    public abstract List<Note> findNoteByIds(Collection<Long> noteIds);

    @Insert
    public abstract long insert(Note note);

    @Update
    public abstract void update(Note note);

    @Delete
    public abstract void delete(Note note);
}