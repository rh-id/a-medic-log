package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.NoteTag;

@Dao
public abstract class NoteTagDao {

    @Insert
    public abstract long insert(NoteTag noteTag);

    @Delete
    public abstract void delete(NoteTag noteTag);

    @Query("SELECT * FROM note_tag WHERE note_id = :noteId")
    public abstract List<NoteTag> findNoteTagsByNoteId(long noteId);

    @Query("SELECT * FROM note_tag WHERE tag LIKE '%'||:search||'%' " +
            "ORDER BY tag ASC")
    public abstract List<NoteTag> searchNoteTag(String search);
}