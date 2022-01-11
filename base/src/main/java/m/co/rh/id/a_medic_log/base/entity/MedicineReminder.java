package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;

import m.co.rh.id.a_medic_log.base.room.converter.Converter;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

@Entity(tableName = "medicine_reminder")
public class MedicineReminder implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Medicine.id that this tied to
     */
    @ColumnInfo(name = "medicine_id")
    public Long medicineId;

    /**
     * Start date of when this reminder should fire
     * and will repeat using the same time
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "start_date_time")
    public Date startDateTime;

    /**
     * Whether reminder enabled or disabled
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "reminder_enabled")
    public Boolean reminderEnabled;

    /**
     * Message to be shown, perhaps show dosage (ex: take 1 pill or 10ml etc..)
     */
    @ColumnInfo(name = "message")
    public String message;

    /**
     * Selected days (mon, tue, wed, etc)
     */
    @TypeConverters({LinkedHashSetConverter.class})
    @ColumnInfo(name = "reminder_days")
    public LinkedHashSet<Integer> reminderDays;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    public MedicineReminder() {
        Date date = new Date();
        createdDateTime = date;
        updatedDateTime = date;
        reminderEnabled = true;
        startDateTime = date;
        reminderDays = new LinkedHashSet<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        reminderDays.add(calendar.get(Calendar.DAY_OF_WEEK));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MedicineReminder that = (MedicineReminder) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (medicineId != null ? !medicineId.equals(that.medicineId) : that.medicineId != null)
            return false;
        if (startDateTime != null ? !startDateTime.equals(that.startDateTime) : that.startDateTime != null)
            return false;
        if (reminderEnabled != null ? !reminderEnabled.equals(that.reminderEnabled) : that.reminderEnabled != null)
            return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (createdDateTime != null ? !createdDateTime.equals(that.createdDateTime) : that.createdDateTime != null)
            return false;
        return updatedDateTime != null ? updatedDateTime.equals(that.updatedDateTime) : that.updatedDateTime == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (medicineId != null ? medicineId.hashCode() : 0);
        result = 31 * result + (startDateTime != null ? startDateTime.hashCode() : 0);
        result = 31 * result + (reminderEnabled != null ? reminderEnabled.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        result = 31 * result + (updatedDateTime != null ? updatedDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public MedicineReminder clone() {
        try {
            return (MedicineReminder) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
