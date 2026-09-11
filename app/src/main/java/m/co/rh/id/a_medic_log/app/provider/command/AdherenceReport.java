package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Computed adherence report for a date range.
 * A dose counts as TAKEN when the medicine has an intake logged on the
 * occurrence's calendar day, otherwise MISSED.
 */
public class AdherenceReport {
    public int rangeDays;
    public int totalTaken;
    public int totalMissed;
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
    }

    public static class MedicineAdherence {
        public long medicineId;
        public String medicineName;
        public String profileName;
        public int taken;
        public int missed;
        public int adherencePercent;
    }
}
