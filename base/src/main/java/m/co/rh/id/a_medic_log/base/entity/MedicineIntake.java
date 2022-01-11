package m.co.rh.id.a_medic_log.base.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;

/**
 * Medicine intake history
 */
@Entity(tableName = "medicine_intake")
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
