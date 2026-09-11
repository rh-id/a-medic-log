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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
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

    private static MedicineReminder reminder(long medicineId, Date startDateTime,
                                             Date createdDateTime, Integer... days) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.medicineId = medicineId;
        medicineReminder.startDateTime = startDateTime;
        medicineReminder.createdDateTime = createdDateTime;
        medicineReminder.reminderDays = new LinkedHashSet<>(Arrays.asList(days));
        return medicineReminder;
    }

    private static MedicineIntake intake(long medicineId, Date takenDateTime) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.takenDateTime = takenDateTime;
        return medicineIntake;
    }

    private static AdherenceReport calculate(long profileId, int rangeDays, Date now,
                                             List<MedicineIntake> intakes,
                                             List<MedicineReminder> reminders) {
        Medicine medicine = new Medicine();
        medicine.id = 1L;
        medicine.noteId = 1L;
        medicine.name = "test medicine";
        Note note = new Note();
        note.id = 1L;
        note.profileId = 1L;
        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "test profile";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine);
        Map<Long, Note> noteMap = new HashMap<>();
        noteMap.put(1L, note);
        Map<Long, Profile> profileMap = new HashMap<>();
        profileMap.put(1L, profile);
        return AdherenceCalculator.calculate(profileId, rangeDays, now, intakes, reminders,
                medicineMap, noteMap, profileMap);
    }

    @Test
    public void reminderOnMonday_producesExactlyOneOccurrencePerMonday() {
        // range Sep 7 .. Sep 21 contains the Mondays Sep 7, Sep 14 and Sep 21
        Date now = date(21, 12, 0);
        MedicineReminder mondayReminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY);

        AdherenceReport report = calculate(1L, 15, now,
                Collections.emptyList(), Collections.singletonList(mondayReminder));

        assertEquals(15, report.rangeDays);
        assertEquals(15, report.daily.size());
        assertEquals(3, report.totalMissed);
        assertEquals(0, report.totalTaken);
        assertEquals(0, report.adherencePercent);
    }

    @Test
    public void occurrenceBeforeStartDateTime_notCounted() {
        Date now = date(13, 12, 0);
        // starts Thursday Sep 10, so the Monday Sep 7 occurrence is before start
        MedicineReminder reminder = reminder(1L,
                date(10, 8, 0), date(1, 8, 0),
                Calendar.MONDAY, Calendar.THURSDAY);

        AdherenceReport report = calculate(1L, 7, now,
                Collections.emptyList(), Collections.singletonList(reminder));

        assertEquals(1, report.totalMissed);
        assertEquals(1, report.daily.get(3).missed); // Thursday Sep 10 is the 4th day
    }

    @Test
    public void occurrenceBeforeCreatedDateTime_notCounted() {
        Date now = date(13, 12, 0);
        // created Wednesday Sep 9, so the Monday Sep 7 occurrence is before creation
        MedicineReminder reminder = reminder(1L,
                date(1, 8, 0), date(9, 8, 0),
                Calendar.MONDAY, Calendar.THURSDAY);

        AdherenceReport report = calculate(1L, 7, now,
                Collections.emptyList(), Collections.singletonList(reminder));

        assertEquals(1, report.totalMissed);
        assertEquals(1, report.daily.get(3).missed); // Thursday Sep 10 is the 4th day
    }

    @Test
    public void intakeSameDay_countsTaken_noIntake_countsMissed() {
        Date now = date(13, 12, 0);
        MedicineReminder reminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY);

        AdherenceReport takenReport = calculate(1L, 7, now,
                Collections.singletonList(intake(1L, date(7, 9, 30))),
                Collections.singletonList(reminder));
        assertEquals(1, takenReport.totalTaken);
        assertEquals(0, takenReport.totalMissed);
        assertEquals(100, takenReport.adherencePercent);
        assertEquals(1, takenReport.perMedicine.size());
        assertEquals(1, takenReport.perMedicine.get(0).taken);
        assertEquals("test medicine", takenReport.perMedicine.get(0).medicineName);
        assertEquals("test profile", takenReport.perMedicine.get(0).profileName);

        AdherenceReport missedReport = calculate(1L, 7, now,
                Collections.emptyList(), Collections.singletonList(reminder));
        assertEquals(0, missedReport.totalTaken);
        assertEquals(1, missedReport.totalMissed);
        assertEquals(0, missedReport.adherencePercent);
    }

    @Test
    public void greedyNearestMatching_twoOccurrencesOneIntake_oneTakenOneMissed() {
        Date now = date(13, 12, 0);
        MedicineReminder morningReminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY);
        MedicineReminder eveningReminder = reminder(1L,
                date(1, 20, 0), date(1, 20, 0), Calendar.MONDAY);
        // 21:00 intake is nearest to the 20:00 occurrence
        MedicineIntake eveningIntake = intake(1L, date(7, 21, 0));

        AdherenceReport report = calculate(1L, 7, now,
                Collections.singletonList(eveningIntake),
                Arrays.asList(morningReminder, eveningReminder));

        assertEquals(1, report.totalTaken);
        assertEquals(1, report.totalMissed);
        assertEquals(50, report.adherencePercent);
        assertEquals(1, report.perMedicine.get(0).taken);
        assertEquals(1, report.perMedicine.get(0).missed);
        assertEquals(50, report.perMedicine.get(0).adherencePercent);
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
        }
        assertTrue(emptyReport.perMedicine.isEmpty());

        // 3 taken out of 4 doses = 75%
        MedicineReminder reminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0),
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY);
        AdherenceReport report = calculate(1L, 7, now,
                Arrays.asList(
                        intake(1L, date(7, 9, 0)),
                        intake(1L, date(8, 9, 0)),
                        intake(1L, date(9, 9, 0))),
                Collections.singletonList(reminder));
        assertEquals(3, report.totalTaken);
        assertEquals(1, report.totalMissed);
        assertEquals(75, report.adherencePercent);
    }

    @Test
    public void occurrenceAfterNow_notCounted() {
        // Monday Sep 7 noon, reminder fires Monday 20:00 -> future dose excluded
        Date now = date(7, 12, 0);
        MedicineReminder reminder = reminder(1L,
                date(1, 20, 0), date(1, 20, 0), Calendar.MONDAY);

        AdherenceReport report = calculate(1L, 1, now,
                Collections.emptyList(), Collections.singletonList(reminder));

        assertEquals(0, report.totalMissed);
        assertEquals(0, report.totalTaken);
    }

    @Test
    public void calculateForProfileA_excludesOtherProfilesData() {
        // profile A owns medicine 1 (note 1), profile B owns medicine 2 (note 2),
        // both have a Monday reminder and a Monday intake in the same range
        Date now = date(13, 12, 0);
        Profile profileA = new Profile();
        profileA.id = 1L;
        profileA.name = "profile A";
        Profile profileB = new Profile();
        profileB.id = 2L;
        profileB.name = "profile B";
        Medicine medicineA = new Medicine();
        medicineA.id = 1L;
        medicineA.noteId = 1L;
        medicineA.name = "medicine A";
        Medicine medicineB = new Medicine();
        medicineB.id = 2L;
        medicineB.noteId = 2L;
        medicineB.name = "medicine B";
        Note noteA = new Note();
        noteA.id = 1L;
        noteA.profileId = 1L;
        Note noteB = new Note();
        noteB.id = 2L;
        noteB.profileId = 2L;
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicineA);
        medicineMap.put(2L, medicineB);
        Map<Long, Note> noteMap = new HashMap<>();
        noteMap.put(1L, noteA);
        noteMap.put(2L, noteB);
        Map<Long, Profile> profileMap = new HashMap<>();
        profileMap.put(1L, profileA);
        profileMap.put(2L, profileB);
        List<MedicineIntake> intakes = Arrays.asList(
                intake(1L, date(7, 9, 0)),
                intake(2L, date(7, 9, 0)));
        List<MedicineReminder> reminders = Arrays.asList(
                reminder(1L, date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY),
                reminder(2L, date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY));

        AdherenceReport reportA = AdherenceCalculator.calculate(1L, 7, now,
                intakes, reminders, medicineMap, noteMap, profileMap);
        // only A's reminder/intake counted, B's are ignored entirely
        assertEquals(1, reportA.totalTaken);
        assertEquals(0, reportA.totalMissed);
        assertEquals(100, reportA.adherencePercent);
        assertEquals(1, reportA.daily.get(0).taken);
        assertEquals(0, reportA.daily.get(0).missed);
        assertEquals(1, reportA.perMedicine.size());
        assertEquals(1, reportA.perMedicine.get(0).medicineId);
        assertEquals("medicine A", reportA.perMedicine.get(0).medicineName);
        assertEquals("profile A", reportA.perMedicine.get(0).profileName);

        // and the other way around for profile B
        AdherenceReport reportB = AdherenceCalculator.calculate(2L, 7, now,
                intakes, reminders, medicineMap, noteMap, profileMap);
        assertEquals(1, reportB.totalTaken);
        assertEquals(0, reportB.totalMissed);
        assertEquals(1, reportB.perMedicine.size());
        assertEquals(2, reportB.perMedicine.get(0).medicineId);
        assertEquals("medicine B", reportB.perMedicine.get(0).medicineName);
        assertEquals("profile B", reportB.perMedicine.get(0).profileName);
    }
}
