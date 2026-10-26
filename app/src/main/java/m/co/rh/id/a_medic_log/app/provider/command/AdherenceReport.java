package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Computed adherence report for a date range.
 * <p>
 * Expected doses come from the persistent medicine_dose_occurrence snapshot
 * table (immutable history: disabling or deleting a reminder later cannot
 * rewrite it). A dose counts as TAKEN when an intake of the same medicine is
 * logged within ±{@link AdherenceCalculator#WINDOW_MILLIS} (±3 hours, a common
 * ±2-3h protocol window in adherence literature; fixed constant, matching
 * Consumer reminder-app practice) of the occurrence's scheduled time, otherwise
 * MISSED. A taken dose is attributed to the OCCURRENCE's calendar day, so a
 * cross-midnight pair (23:00 dose taken at 01:00 the next day) still counts for
 * the scheduled day. Every intake inside the range that remained unconsumed
 * after matching counts as EXTRA and is attributed to the INTAKE's calendar
 * day; extras are visible in the report but never affect the percentages.
 */
public class AdherenceReport {
    public int rangeDays;
    public int totalTaken;
    public int totalMissed;
    public int totalExtra;
    public int adherencePercent;
    public List<DailyCount> daily = new ArrayList<>();
    public List<MedicineAdherence> perMedicine = new ArrayList<>();

    /**
     * True when the report contains no expected dose at all
     */
    public boolean isEmpty() {
        return totalTaken + totalMissed == 0;
    }

    public static class DailyCount {
        public Date date;
        public int taken;
        public int missed;
        public int extra;
    }

    public static class MedicineAdherence {
        public long medicineId;
        public String medicineName;
        public String profileName;
        public int taken;
        public int missed;
        public int extra;
        public int adherencePercent;
    }
}
