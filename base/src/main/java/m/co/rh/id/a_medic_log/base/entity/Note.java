package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;

@Entity(tableName = "note")
public class Note implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Profile.id that this note tied to
     */
    @ColumnInfo(name = "profile_id")
    public Long profileId;

    /**
     * Date time entered by user
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "entry_date_time")
    public Date entryDateTime;

    /**
     * Content of the note
     */
    @ColumnInfo(name = "content")
    public String content;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    public Note() {
        Date date = new Date();
        createdDateTime = date;
        updatedDateTime = date;
        entryDateTime = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Note note = (Note) o;

        if (id != null ? !id.equals(note.id) : note.id != null) return false;
        if (profileId != null ? !profileId.equals(note.profileId) : note.profileId != null)
            return false;
        if (entryDateTime != null ? !entryDateTime.equals(note.entryDateTime) : note.entryDateTime != null)
            return false;
        if (content != null ? !content.equals(note.content) : note.content != null) return false;
        if (createdDateTime != null ? !createdDateTime.equals(note.createdDateTime) : note.createdDateTime != null)
            return false;
        return updatedDateTime != null ? updatedDateTime.equals(note.updatedDateTime) : note.updatedDateTime == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (profileId != null ? profileId.hashCode() : 0);
        result = 31 * result + (entryDateTime != null ? entryDateTime.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        result = 31 * result + (updatedDateTime != null ? updatedDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public Note clone() {
        try {
            return (Note) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
