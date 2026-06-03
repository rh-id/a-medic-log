package m.co.rh.id.a_medic_log.base.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;

/**
 * Medicine intake history
 */
@Entity(tableName = "medicine_intake", indices = {
        @Index(value = "medicine_id")
})
public class MedicineIntake implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Medicine.id that this tied to
     */
    @ColumnInfo(name = "medicine_id")
    public Long medicineId;

    /**
     * Message copied from MedicineReminder or any note for example "Take 1 tablet this time because xxx"
     */
    @ColumnInfo(name = "description")
    public String description;

    /**
     * The date time that this medicine is taken
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "taken_date_time")
    public Date takenDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    public MedicineIntake() {
        Date date = new Date();
        createdDateTime = date;
        updatedDateTime = date;
        takenDateTime = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MedicineIntake that = (MedicineIntake) o;

        if (id != that.id) return false;
        if (medicineId != null ? !medicineId.equals(that.medicineId) : that.medicineId != null)
            return false;
        if (description != null ? !description.equals(that.description) : that.description != null)
            return false;
        if (takenDateTime != null ? !takenDateTime.equals(that.takenDateTime) : that.takenDateTime != null)
            return false;
        if (createdDateTime != null ? !createdDateTime.equals(that.createdDateTime) : that.createdDateTime != null)
            return false;
        return updatedDateTime != null ? updatedDateTime.equals(that.updatedDateTime) : that.updatedDateTime == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (medicineId != null ? medicineId.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (takenDateTime != null ? takenDateTime.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        result = 31 * result + (updatedDateTime != null ? updatedDateTime.hashCode() : 0);
        return result;
    }

    @NonNull
    @Override
    public MedicineIntake clone() {
        try {
            return (MedicineIntake) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
