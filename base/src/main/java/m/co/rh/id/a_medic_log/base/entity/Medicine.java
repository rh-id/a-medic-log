package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;

@Entity(tableName = "medicine")
public class Medicine implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Note.id that this medicine tied to
     */
    @ColumnInfo(name = "note_id")
    public Long noteId;

    @ColumnInfo(name = "name")
    public String name;

    /**
     * drugs or medicine information and dosage
     */
    @ColumnInfo(name = "description")
    public String description;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    public Medicine() {
        Date date = new Date();
        createdDateTime = date;
        updatedDateTime = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Medicine medicine = (Medicine) o;

        if (id != null ? !id.equals(medicine.id) : medicine.id != null) return false;
        if (noteId != null ? !noteId.equals(medicine.noteId) : medicine.noteId != null)
            return false;
        if (name != null ? !name.equals(medicine.name) : medicine.name != null) return false;
        if (description != null ? !description.equals(medicine.description) : medicine.description != null)
            return false;
        if (createdDateTime != null ? !createdDateTime.equals(medicine.createdDateTime) : medicine.createdDateTime != null)
            return false;
        return updatedDateTime != null ? updatedDateTime.equals(medicine.updatedDateTime) : medicine.updatedDateTime == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (noteId != null ? noteId.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        result = 31 * result + (updatedDateTime != null ? updatedDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public Medicine clone() {
        try {
            return (Medicine) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
