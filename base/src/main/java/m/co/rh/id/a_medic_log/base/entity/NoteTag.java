package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "note_tag")
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
        if (tag != null && !tag.isEmpty()) {
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

        return tag != null ? tag.equals(noteTag.tag) : noteTag.tag == null;
    }

    @Override
    public int hashCode() {
        return tag != null ? tag.hashCode() : 0;
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
