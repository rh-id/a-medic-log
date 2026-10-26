package m.co.rh.id.a_medic_log.base.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;

/**
 * Expected dose occurrence audit record.
 * Every scheduled dose is recorded here so that disabling/deleting a reminder
 * later cannot rewrite adherence history.
 * There is deliberately no foreign key to medicine_reminder so deleting a
 * reminder preserves the expectation history, the medicine foreign key mirrors
 * MedicineIntake's cascade so deleting a medicine wipes its expectation history
 * along with its intakes.
 * The unique composite index on (reminder_id, scheduled_date_time) makes
 * occurrence generation idempotent.
 */
@Entity(tableName = "medicine_dose_occurrence", indices = {
        @Index(value = "reminder_id"),
        @Index(value = "medicine_id"),
        @Index(value = "scheduled_date_time"),
        @Index(value = {"reminder_id", "scheduled_date_time"}, unique = true)
}, foreignKeys = {
        @ForeignKey(entity = Medicine.class,
                parentColumns = "id",
                childColumns = "medicine_id",
                onDelete = ForeignKey.CASCADE)
})
public class MedicineDoseOccurrence implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * MedicineReminder.id that this expected dose is tied to
     */
    @ColumnInfo(name = "reminder_id")
    public long reminderId;

    /**
     * Medicine.id that this tied to
     */
    @ColumnInfo(name = "medicine_id")
    public long medicineId;

    /**
     * The date time that this dose is expected to be taken
     */
    @NonNull
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "scheduled_date_time")
    public Date scheduledDateTime;

    @NonNull
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public MedicineDoseOccurrence() {
        Date date = new Date();
        createdDateTime = date;
        scheduledDateTime = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MedicineDoseOccurrence that = (MedicineDoseOccurrence) o;

        if (id != that.id) return false;
        if (reminderId != that.reminderId) return false;
        if (medicineId != that.medicineId) return false;
        if (!scheduledDateTime.equals(that.scheduledDateTime)) return false;
        return createdDateTime.equals(that.createdDateTime);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (int) (reminderId ^ (reminderId >>> 32));
        result = 31 * result + (int) (medicineId ^ (medicineId >>> 32));
        result = 31 * result + scheduledDateTime.hashCode();
        result = 31 * result + createdDateTime.hashCode();
        return result;
    }

    @NonNull
    @Override
    public MedicineDoseOccurrence clone() {
        try {
            return (MedicineDoseOccurrence) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
