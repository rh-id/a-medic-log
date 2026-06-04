package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;

@Dao
public abstract class NoteAttachmentFileDao {

    @Insert
    public abstract long insert(NoteAttachmentFile noteAttachmentFile);

    @Delete
    public abstract void delete(NoteAttachmentFile noteAttachmentFile);

    @Query("SELECT * FROM note_attachment_file WHERE attachment_id = :attachmentId")
    public abstract List<NoteAttachmentFile> findNoteAttachmentFilesByAttachmentId(long attachmentId);

    @Query("SELECT * FROM note_attachment_file WHERE file_name = :fileName")
    public abstract NoteAttachmentFile findNoteAttachmentFileByFileName(String fileName);

    @Query("DELETE FROM note_attachment_file WHERE attachment_id = :attachmentId")
    public abstract void deleteNoteAttachmentFilesByAttachmentId(long attachmentId);
}