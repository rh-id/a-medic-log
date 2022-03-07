package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;

@Entity(tableName = "note_attachment")
public class NoteAttachment implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    @ColumnInfo(name = "note_id")
    public Long noteId;

    /**
     * Attachment name
     */
    @ColumnInfo(name = "name")
    public String name;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public NoteAttachment() {
        createdDateTime = new Date();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NoteAttachment that = (NoteAttachment) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (noteId != null ? !noteId.equals(that.noteId) : that.noteId != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return createdDateTime != null ? createdDateTime.equals(that.createdDateTime) : that.createdDateTime == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (noteId != null ? noteId.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public NoteAttachment clone() {
        try {
            return (NoteAttachment) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
