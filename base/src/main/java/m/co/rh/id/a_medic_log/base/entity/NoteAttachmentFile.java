package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;

@Entity(tableName = "note_attachment_file")
public class NoteAttachmentFile implements Comparable<NoteAttachmentFile>, Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * NoteAttachment.id
     */
    @ColumnInfo(name = "attachment_id")
    public Long attachmentId;

    /**
     * Unique file name with extension included i.e: xxxx.jpg
     */
    @ColumnInfo(name = "file_name")
    public String fileName;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public NoteAttachmentFile() {
        createdDateTime = new Date();
    }

    @Override
    public int compareTo(NoteAttachmentFile noteAttachmentFile) {
        if (noteAttachmentFile != null && noteAttachmentFile.createdDateTime != null) {
            return noteAttachmentFile.createdDateTime.compareTo(createdDateTime);
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NoteAttachmentFile that = (NoteAttachmentFile) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (attachmentId != null ? !attachmentId.equals(that.attachmentId) : that.attachmentId != null)
            return false;
        if (fileName != null ? !fileName.equals(that.fileName) : that.fileName != null)
            return false;
        return createdDateTime != null ? createdDateTime.equals(that.createdDateTime) : that.createdDateTime == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (attachmentId != null ? attachmentId.hashCode() : 0);
        result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public NoteAttachmentFile clone() {
        try {
            return (NoteAttachmentFile) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
