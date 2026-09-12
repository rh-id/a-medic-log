package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.Date;

/**
 * Today's dose status for a single profile, computed from its enabled
 * medicine reminders. Unlike the adherence report, upcoming doses of
 * today are included so the card can show what is still ahead.
 */
public class TodayStatus {
    public static final int STATUS_MISSED = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_DONE = 2;

    public long profileId;
    public String profileName;
    public int status;
    public int totalDoses;
    public int takenDoses;
    public int missedCount;
    public String missedMedicineName; // earliest missed occurrence, null when none
    public Date missedDoseTime;       // its time, null when none
    public Date nextDoseTime;         // earliest upcoming, null when none
}
