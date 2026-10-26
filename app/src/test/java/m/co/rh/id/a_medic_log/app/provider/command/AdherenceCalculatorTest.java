package m.co.rh.id.a_medic_log.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Profile;

public class AdherenceCalculatorTest {

    // 2026-09-07 is a Monday, used as the anchor for the fixed test ranges
    private static final int YEAR = 2026;
    private static final int MONTH = Calendar.SEPTEMBER;

    private static Date date(int dayOfMonth, int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(YEAR, MONTH, dayOfMonth, hourOfDay, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static MedicineDoseOccurrence occurrence(long reminderId, long medicineId,
                                                     Date scheduledDateTime) {
        MedicineDoseOccurrence doseOccurrence = new MedicineDoseOccurrence();
        doseOccurrence.reminderId = reminderId;
        doseOccurrence.medicineId = medicineId;
        doseOccurrence.scheduledDateTime = scheduledDateTime;
        return doseOccurrence;
    }

    private static MedicineIntake intake(long medicineId, Date takenDateTime) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.takenDateTime = takenDateTime;
        return medicineIntake;
    }

    private static Medicine medicine(long medicineId, long noteId, String name) {
        Medicine medicine = new Medicine();
        medicine.id = medicineId;
        medicine.noteId = noteId;
        medicine.name = name;
        return medicine;
    }

    /**
     * Single-medicine fixture: medicine 1 belongs to profile 1
     */
    private static AdherenceReport calculate(long profileId, int rangeDays, Date now,
                                             List<MedicineDoseOccurrence> occurrences,
                                             List<MedicineIntake> intakes) {
        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "test profile";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine(1L, 1L, "test medicine"));
        return AdherenceCalculator.calculate(profileId, rangeDays, now,
                occurrences, intakes, medicineMap, profile);
    }

    @Test
    public void intakeWithinWindow_countsTaken() {
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 8, 0)));

        // 5 minutes after the scheduled time
        AdherenceReport takenReport = calculate(1L, 7, now, occurrences,
                Collections.singletonList(intake(1L, date(7, 8, 5))));
        assertEquals(1, takenReport.totalTaken);
        assertEquals(0, takenReport.totalMissed);
        assertEquals(0, takenReport.totalExtra);
        assertEquals(100, takenReport.adherencePercent);
        assertEquals(1, takenReport.perMedicine.size());
        assertEquals(1, takenReport.perMedicine.get(0).taken);
        assertEquals(0, takenReport.perMedicine.get(0).extra);
        assertEquals(100, takenReport.perMedicine.get(0).adherencePercent);
        assertEquals("test medicine", takenReport.perMedicine.get(0).medicineName);
        assertEquals("test profile", takenReport.perMedicine.get(0).profileName);

        // no intake at all: missed
        AdherenceReport missedReport = calculate(1L, 7, now, occurrences,
                Collections.emptyList());
        assertEquals(0, missedReport.totalTaken);
        assertEquals(1, missedReport.totalMissed);
        assertEquals(0, missedReport.totalExtra);
        assertEquals(0, missedReport.adherencePercent);
    }

    @Test
    public void intakeExactlyThreeHoursAway_countsTaken_windowIsInclusive() {
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 8, 0)));

        AdherenceReport report = calculate(1L, 7, now, occurrences,
                Collections.singletonList(intake(1L, date(7, 11, 0))));

        assertEquals(1, report.totalTaken);
        assertEquals(0, report.totalMissed);
    }

    @Test
    public void intakeThreeHoursAndOneMinuteAway_countsMissed_intakeBecomesExtra() {
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 8, 0)));

        AdherenceReport report = calculate(1L, 7, now, occurrences,
                Collections.singletonList(intake(1L, date(7, 11, 1))));

        assertEquals(0, report.totalTaken);
        assertEquals(1, report.totalMissed);
        assertEquals(0, report.adherencePercent);
        // the intake happened but matched nothing, it is reported as extra
        // without changing the percentage
        assertEquals(1, report.totalExtra);
        assertEquals(1, report.daily.get(0).missed);
        assertEquals(1, report.daily.get(0).extra);
    }

    @Test
    public void windowOverload_narrowWindowRejectsIntakeTheDefaultWouldAccept() {
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 8, 0)));
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(1L, date(7, 9, 0))); // one hour after the dose
        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "test profile";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine(1L, 1L, "test medicine"));

        AdherenceReport defaultWindow = AdherenceCalculator.calculate(1L, 7, now,
                occurrences, intakes, medicineMap, profile);
        assertEquals(1, defaultWindow.totalTaken);

        AdherenceReport narrowWindow = AdherenceCalculator.calculate(1L, 7, now,
                occurrences, intakes, medicineMap, profile, 30 * 60 * 1000L);
        assertEquals(0, narrowWindow.totalTaken);
        assertEquals(1, narrowWindow.totalMissed);
        assertEquals(1, narrowWindow.totalExtra);
    }

    @Test
    public void crossMidnight_takenNextDay_countsForTheOccurrencesDay_notExtra() {
        // occurrence Monday Sep 7 23:00 taken at 01:00 on Tuesday Sep 8
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 23, 0)));
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(1L, date(8, 1, 0)));

        AdherenceReport report = calculate(1L, 7, now, occurrences, intakes);

        assertEquals(1, report.totalTaken);
        assertEquals(0, report.totalMissed);
        // the intake is consumed by the match, so nothing is extra
        assertEquals(0, report.totalExtra);
        // attributed to the OCCURRENCE's calendar day (Sep 7, the first day)
        assertEquals(1, report.daily.get(0).taken);
        assertEquals(0, report.daily.get(0).extra);
        assertEquals(0, report.daily.get(1).taken);
        assertEquals(0, report.daily.get(1).extra);
    }

    @Test
    public void nearestIntakeWins_whenTwoOccurrencesCompete() {
        // occurrences 08:00 and 09:00, single intake 08:50 is much closer to
        // the 09:00 occurrence, so 09:00 is taken and 08:00 is missed
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, 1L, date(7, 8, 0)),
                occurrence(1L, 1L, date(7, 9, 0)));
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(1L, date(7, 8, 50)));

        AdherenceReport report = calculate(1L, 7, now, occurrences, intakes);

        assertEquals(1, report.totalTaken);
        assertEquals(1, report.totalMissed);
        assertEquals(50, report.adherencePercent);
        assertEquals(0, report.daily.get(0).extra);
        assertEquals(1, report.perMedicine.get(0).taken);
        assertEquals(1, report.perMedicine.get(0).missed);
    }

    @Test
    public void occurrenceAfterNow_notYetDue_excludedFromTakenAndMissed() {
        // Monday Sep 7 noon, the 20:00 occurrence of the same day is not due yet
        Date now = date(7, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 20, 0)));

        AdherenceReport report = calculate(1L, 1, now, occurrences, Collections.emptyList());

        assertEquals(0, report.totalTaken);
        assertEquals(0, report.totalMissed);
        assertEquals(0, report.adherencePercent);
        assertTrue(report.isEmpty());
        // the not-yet-due occurrence still belongs to a scoped medicine with an
        // occurrence but produced no counts, so no per-medicine row is needed
        assertTrue(report.perMedicine.isEmpty());
    }

    @Test
    public void extraIntakes_countedInclPrnMedicine_neverAffectPercent() {
        // medicine 1 has one taken dose, medicine 2 is PRN (zero occurrences)
        // with two unscheduled intakes
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 8, 0)));
        List<MedicineIntake> intakes = Arrays.asList(
                intake(1L, date(7, 8, 10)),
                intake(2L, date(8, 15, 0)),
                intake(2L, date(9, 18, 0)));
        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "test profile";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine(1L, 1L, "scheduled medicine"));
        medicineMap.put(2L, medicine(2L, 1L, "prn medicine"));

        AdherenceReport report = AdherenceCalculator.calculate(1L, 7, now,
                occurrences, intakes, medicineMap, profile);

        assertEquals(1, report.totalTaken);
        assertEquals(0, report.totalMissed);
        assertEquals(2, report.totalExtra);
        assertEquals(100, report.adherencePercent);
        assertEquals(2, report.perMedicine.size());
        AdherenceReport.MedicineAdherence prn = report.perMedicine.get(1);
        assertEquals(2L, prn.medicineId);
        assertEquals("prn medicine", prn.medicineName);
        assertEquals(0, prn.taken);
        assertEquals(0, prn.missed);
        assertEquals(2, prn.extra);
        assertEquals(0, prn.adherencePercent);
    }

    @Test
    public void extraIntake_attributedToTheIntakesOwnDay() {
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 23, 0)));
        List<MedicineIntake> intakes = Arrays.asList(
                intake(1L, date(7, 23, 10)), // matches the occurrence
                intake(1L, date(9, 15, 0))); // unscheduled, extra

        AdherenceReport report = calculate(1L, 7, now, occurrences, intakes);

        assertEquals(1, report.totalTaken);
        assertEquals(1, report.totalExtra);
        // taken on the occurrence's day (Sep 7, index 0)...
        assertEquals(1, report.daily.get(0).taken);
        assertEquals(0, report.daily.get(0).extra);
        // ...extra on the intake's own day (Sep 9, index 2)
        assertEquals(1, report.daily.get(2).extra);
        assertEquals(0, report.daily.get(2).taken);
    }

    @Test
    public void extraIntake_onlyInsideTheActualRange_widenedBoundariesNeverCount() {
        // last day of the range: occurrence Sep 13 23:00 taken at 01:00 on
        // Sep 14 (outside the range, present because the fetch was widened)
        Date now = date(13, 23, 30);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(13, 23, 0)));
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(1L, date(14, 1, 0)));

        AdherenceReport report = calculate(1L, 7, now, occurrences, intakes);

        assertEquals(1, report.totalTaken);
        assertEquals(0, report.totalMissed);
        assertEquals(0, report.totalExtra);
        assertEquals(100, report.adherencePercent);
    }

    @Test
    public void rangeBoundary_intakeBeforeRangeStart_matchesOccurrenceOfFirstRangeDay() {
        // occurrence Sep 7 00:30 (first day of the range) matched by the
        // intake logged at 22:00 on Sep 6, only visible because the fetch
        // was widened backwards by the window
        Date now = date(13, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, 1L, date(7, 0, 30)));
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(1L, date(6, 22, 0)));

        AdherenceReport report = calculate(1L, 7, now, occurrences, intakes);

        assertEquals(1, report.totalTaken);
        assertEquals(0, report.totalMissed);
        // the consumed intake lies before the range, it must not count as extra
        assertEquals(0, report.totalExtra);
        assertEquals(1, report.daily.get(0).taken);
    }

    @Test
    public void occurrencesAcrossTheRange_countedOnTheirOwnDays() {
        // replaces the reminder-derived scenarios: the start/created cutoffs
        // live in MedicineRepository's occurrence generation cores, the
        // calculator counts exactly the occurrences the snapshot table hands it
        // range Sep 7 .. Sep 21 contains the Mondays Sep 7, Sep 14 and Sep 21
        Date now = date(21, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, 1L, date(7, 8, 0)),
                occurrence(1L, 1L, date(14, 8, 0)),
                occurrence(1L, 1L, date(21, 8, 0)));

        AdherenceReport report = calculate(1L, 15, now, occurrences, Collections.emptyList());

        assertEquals(15, report.rangeDays);
        assertEquals(15, report.daily.size());
        assertEquals(3, report.totalMissed);
        assertEquals(0, report.totalTaken);
        assertEquals(0, report.adherencePercent);
    }

    @Test
    public void percentMath_zeroDosesIsZeroPercent_noDivisionByZero() {
        Date now = date(13, 12, 0);

        AdherenceReport emptyReport = calculate(1L, 7, now,
                Collections.emptyList(), Collections.emptyList());
        assertEquals(0, emptyReport.totalTaken);
        assertEquals(0, emptyReport.totalMissed);
        assertEquals(0, emptyReport.adherencePercent);
        assertTrue(emptyReport.isEmpty());
        assertEquals(7, emptyReport.daily.size());
        for (AdherenceReport.DailyCount dailyCount : emptyReport.daily) {
            assertEquals(0, dailyCount.taken);
            assertEquals(0, dailyCount.missed);
            assertEquals(0, dailyCount.extra);
        }
        assertTrue(emptyReport.perMedicine.isEmpty());

        // 3 taken out of 4 doses = 75%
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, 1L, date(7, 8, 0)),
                occurrence(1L, 1L, date(8, 8, 0)),
                occurrence(1L, 1L, date(9, 8, 0)),
                occurrence(1L, 1L, date(10, 8, 0)));
        List<MedicineIntake> intakes = Arrays.asList(
                intake(1L, date(7, 9, 0)),
                intake(1L, date(8, 9, 0)),
                intake(1L, date(9, 9, 0)));
        AdherenceReport report = calculate(1L, 7, now, occurrences, intakes);
        assertEquals(3, report.totalTaken);
        assertEquals(1, report.totalMissed);
        assertEquals(0, report.totalExtra);
        assertEquals(75, report.adherencePercent);
    }

    @Test
    public void intakeOnlyReport_isStillEmpty_evenWithExtras() {
        // extras are informational: no expected dose means the report stays
        // "empty" and the no-data hint keeps showing
        Date now = date(13, 12, 0);
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(1L, date(7, 9, 0)));

        AdherenceReport report = calculate(1L, 7, now, Collections.emptyList(), intakes);

        assertTrue(report.isEmpty());
        assertFalse(report.perMedicine.isEmpty());
        assertEquals(1, report.totalExtra);
        assertEquals(0, report.adherencePercent);
    }

    @Test
    public void calculateForProfileA_excludesOtherProfilesData() {
        // profile A owns medicine 1 (note 1), profile B owns medicine 2 (note 2),
        // both have an occurrence and an intake in the same range; the command
        // pre-scopes the medicine map, so B's rows must be ignored entirely
        Date now = date(13, 12, 0);
        Profile profileA = new Profile();
        profileA.id = 1L;
        profileA.name = "profile A";
        Profile profileB = new Profile();
        profileB.id = 2L;
        profileB.name = "profile B";
        Map<Long, Medicine> medicineMapA = new HashMap<>();
        medicineMapA.put(1L, medicine(1L, 1L, "medicine A"));
        Map<Long, Medicine> medicineMapB = new HashMap<>();
        medicineMapB.put(2L, medicine(2L, 2L, "medicine B"));
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, 1L, date(7, 8, 0)),
                occurrence(2L, 2L, date(7, 8, 0)));
        List<MedicineIntake> intakes = Arrays.asList(
                intake(1L, date(7, 9, 0)),
                intake(2L, date(7, 9, 0)));

        AdherenceReport reportA = AdherenceCalculator.calculate(1L, 7, now,
                occurrences, intakes, medicineMapA, profileA);
        // only A's occurrence/intake counted, B's are ignored entirely
        assertEquals(1, reportA.totalTaken);
        assertEquals(0, reportA.totalMissed);
        assertEquals(0, reportA.totalExtra);
        assertEquals(100, reportA.adherencePercent);
        assertEquals(1, reportA.daily.get(0).taken);
        assertEquals(0, reportA.daily.get(0).missed);
        assertEquals(1, reportA.perMedicine.size());
        assertEquals(1L, reportA.perMedicine.get(0).medicineId);
        assertEquals("medicine A", reportA.perMedicine.get(0).medicineName);
        assertEquals("profile A", reportA.perMedicine.get(0).profileName);

        // and the other way around for profile B
        AdherenceReport reportB = AdherenceCalculator.calculate(2L, 7, now,
                occurrences, intakes, medicineMapB, profileB);
        assertEquals(1, reportB.totalTaken);
        assertEquals(0, reportB.totalMissed);
        assertEquals(1, reportB.perMedicine.size());
        assertEquals(2L, reportB.perMedicine.get(0).medicineId);
        assertEquals("medicine B", reportB.perMedicine.get(0).medicineName);
        assertEquals("profile B", reportB.perMedicine.get(0).profileName);
    }
}
