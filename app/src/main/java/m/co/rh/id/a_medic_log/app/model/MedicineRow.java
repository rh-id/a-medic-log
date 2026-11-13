package m.co.rh.id.a_medic_log.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

import m.co.rh.id.a_medic_log.base.entity.Medicine;

/**
 * One aggregated row of the medicines page: medicines sharing the same
 * name (case/whitespace insensitive) are grouped under their newest entry.
 * The reminder and intake figures are summed over every member of the group,
 * the adherence figures come from the 7-day report ({@code adherencePercent}
 * is null when the medicine had no expected dose in the window, never 0%).
 */
public class MedicineRow implements Serializable {
    /**
     * newest medicine of the group, its name is the display name
     */
    public Medicine medicine;
    /**
     * ids of every grouped medicine, the representative id comes first
     */
    public ArrayList<Long> medicineIds;
    public int remindersOn;
    public int remindersOff;
    /**
     * latest intake across the group, null when never taken
     */
    public Date lastIntakeDateTime;
    public int takenCount;
    public int missedCount;
    /**
     * null means no data for the 7-day window
     */
    public Integer adherencePercent;
}
