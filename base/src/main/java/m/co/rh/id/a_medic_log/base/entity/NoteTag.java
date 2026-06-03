package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "note_tag", indices = {
        @Index(value = "note_id"),
        @Index(value = "tag")
})
public class NoteTag implements Comparable<NoteTag>, Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Note.id that this tag tied to
     */
    @ColumnInfo(name = "note_id")
    public Long noteId;

    @ColumnInfo(name = "tag")
    public String tag;


    @Override
    public int compareTo(NoteTag noteTag) {
        if (tag != null && !tag.isEmpty() && noteTag != null) {
            return tag.compareTo(noteTag.tag);
        } else {
            return -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NoteTag noteTag = (NoteTag) o;

        if (id != null ? !id.equals(noteTag.id) : noteTag.id != null) return false;
        if (noteId != null ? !noteId.equals(noteTag.noteId) : noteTag.noteId != null) return false;
        return tag != null ? tag.equals(noteTag.tag) : noteTag.tag == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (noteId != null ? noteId.hashCode() : 0);
        result = 31 * result + (tag != null ? tag.hashCode() : 0);
        return result;
    }

    @Override
    public NoteTag clone() {
        try {
            return (NoteTag) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
