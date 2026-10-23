package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;

@Dao
public abstract class NoteAttachmentDao {

    @Insert
    public abstract long insert(NoteAttachment noteAttachment);

    @Update
    public abstract void update(NoteAttachment noteAttachment);

    @Delete
    public abstract void delete(NoteAttachment noteAttachment);

    @Query("SELECT * FROM note_attachment WHERE note_id = :noteId")
    public abstract List<NoteAttachment> findNoteAttachmentsByNoteId(long noteId);

    @Query("SELECT COUNT(*) FROM note_attachment INNER JOIN note " +
            "ON note_attachment.note_id = note.id WHERE note.profile_id = :profileId")
    public abstract int countNoteAttachmentsByProfileId(long profileId);
}