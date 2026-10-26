package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;

@Dao
public abstract class MedicineDoseOccurrenceDao {

    /**
     * Inserts with IGNORE so generation is idempotent, the returned long is the
     * new row id or -1 when the occurrence already existed
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract List<Long> insertAll(List<MedicineDoseOccurrence> medicineDoseOccurrences);

    @Query("SELECT * FROM medicine_dose_occurrence WHERE scheduled_date_time BETWEEN :from AND :to " +
            "ORDER BY scheduled_date_time ASC")
    public abstract List<MedicineDoseOccurrence> findMedicineDoseOccurrencesByScheduledDateTimeBetween(long from, long to);

    @Query("SELECT * FROM medicine_dose_occurrence WHERE reminder_id = :reminderId " +
            "ORDER BY scheduled_date_time DESC LIMIT 1")
    public abstract MedicineDoseOccurrence findLatestMedicineDoseOccurrenceByReminderId(long reminderId);

    @Query("DELETE FROM medicine_dose_occurrence WHERE reminder_id = :reminderId " +
            "AND scheduled_date_time > :time")
    public abstract void deleteByReminderIdAndScheduledDateTimeAfter(long reminderId, long time);

    @Query("DELETE FROM medicine_dose_occurrence WHERE reminder_id = :reminderId")
    public abstract void deleteAllByReminderId(long reminderId);
}
