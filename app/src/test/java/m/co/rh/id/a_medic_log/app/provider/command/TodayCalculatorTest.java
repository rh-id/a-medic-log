package m.co.rh.id.a_medic_log.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;

public class TodayCalculatorTest {

    // 2026-09-07 is a Monday, used as the anchor for "today" in the tests
    private static final int YEAR = 2026;
    private static final int MONTH = Calendar.SEPTEMBER;

    private static Date date(int dayOfMonth, int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(YEAR, MONTH, dayOfMonth, hourOfDay, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static MedicineDoseOccurrence occurrence(long medicineId, Date scheduledDateTime) {
        MedicineDoseOccurrence doseOccurrence = new MedicineDoseOccurrence();
        doseOccurrence.reminderId = 1L;
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

    private static Note note(long noteId, long profileId) {
        Note note = new Note();
        note.id = noteId;
        note.profileId = profileId;
        return note;
    }

    /**
     * Single-medicine fixture: medicine 1 belongs to profile 1
     */
    private static List<TodayStatus> compute(Date now, List<MedicineDoseOccurrence> occurrences,
                                             List<MedicineIntake> intakes) {
        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "test profile";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine(1L, 1L, "test medicine"));
        Map<Long, Note> noteMap = new HashMap<>();
        noteMap.put(1L, note(1L, 1L));
        return TodayCalculator.compute(now, occurrences, intakes,
                medicineMap, noteMap, Collections.singletonList(profile));
    }

    @Test
    public void noOccurrencesToday_profileOmitted() {
        // the command only feeds occurrences from the snapshot table, a profile
        // whose medicine has no occurrence today (e.g. PRN-only) is omitted
        Date now = date(7, 9, 0);

        List<TodayStatus> statuses = compute(now,
                Collections.emptyList(), Collections.emptyList());

        assertTrue(statuses.isEmpty());
    }

    @Test
    public void allDosesTaken_statusDone() {
        Date now = date(7, 21, 0);
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, date(7, 8, 0)),
                occurrence(1L, date(7, 20, 0)));

        List<TodayStatus> statuses = compute(now, occurrences,
                Arrays.asList(
                        intake(1L, date(7, 8, 5)),
                        intake(1L, date(7, 20, 5))));

        assertEquals(1, statuses.size());
        TodayStatus status = statuses.get(0);
        assertEquals(TodayStatus.STATUS_DONE, status.status);
        assertEquals(2, status.totalDoses);
        assertEquals(2, status.takenDoses);
        assertEquals(0, status.missedCount);
        assertNull(status.missedMedicineName);
        assertNull(status.missedDoseTime);
        assertNull(status.nextDoseTime);
    }

    @Test
    public void pastDoseUntaken_statusMissedWithDetail() {
        // 08:00 dose, ±3h take-window closes at 11:00; at 11:00 the window is
        // fully elapsed (inclusive boundary) so the dose counts as missed
        Date now = date(7, 11, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, date(7, 8, 0)));

        List<TodayStatus> statuses = compute(now, occurrences, Collections.emptyList());

        assertEquals(1, statuses.size());
        TodayStatus status = statuses.get(0);
        assertEquals(TodayStatus.STATUS_MISSED, status.status);
        assertEquals(1, status.totalDoses);
        assertEquals(0, status.takenDoses);
        assertEquals(1, status.missedCount);
        assertEquals("test medicine", status.missedMedicineName);
        assertEquals(date(7, 8, 0), status.missedDoseTime);
        assertNull(status.nextDoseTime);
    }

    @Test
    public void futureDoseUntaken_statusInProgressWithNextTime() {
        Date now = date(7, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, date(7, 8, 0)),
                occurrence(1L, date(7, 20, 0)));

        List<TodayStatus> statuses = compute(now, occurrences,
                Collections.singletonList(intake(1L, date(7, 8, 5))));

        assertEquals(1, statuses.size());
        TodayStatus status = statuses.get(0);
        assertEquals(TodayStatus.STATUS_IN_PROGRESS, status.status);
        assertEquals(2, status.totalDoses);
        assertEquals(1, status.takenDoses);
        assertEquals(0, status.missedCount);
        assertNull(status.missedMedicineName);
        assertNull(status.missedDoseTime);
        assertEquals(date(7, 20, 0), status.nextDoseTime);
    }

    @Test
    public void intakeOutsideWindow_doesNotMarkDoseTaken() {
        // 08:00 dose, intake logged at 11:01 (window is 3 hours): the dose is
        // missed and the intake never matches
        Date now = date(7, 12, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, date(7, 8, 0)));

        List<TodayStatus> statuses = compute(now, occurrences,
                Collections.singletonList(intake(1L, date(7, 11, 1))));

        assertEquals(1, statuses.size());
        TodayStatus status = statuses.get(0);
        assertEquals(TodayStatus.STATUS_MISSED, status.status);
        assertEquals(0, status.takenDoses);
        assertEquals(1, status.missedCount);

        // exactly 3 hours is still inside the window
        List<TodayStatus> boundaryStatuses = compute(now, occurrences,
                Collections.singletonList(intake(1L, date(7, 11, 0))));
        assertEquals(TodayStatus.STATUS_DONE, boundaryStatuses.get(0).status);
        assertEquals(1, boundaryStatuses.get(0).takenDoses);
    }

    @Test
    public void crossMidnight_intakeShortlyAfterMidnight_marksPreviousEveningDoseTaken() {
        // the command feeds occurrences/intakes around today's boundaries, so
        // an intake at 00:30 can still mark yesterday's 23:50 dose taken even
        // though the occurrence's day has passed
        Date now = date(7, 1, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, date(6, 23, 50)));
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(1L, date(7, 0, 30)));

        List<TodayStatus> statuses = compute(now, occurrences, intakes);

        assertEquals(1, statuses.size());
        TodayStatus status = statuses.get(0);
        assertEquals(TodayStatus.STATUS_DONE, status.status);
        assertEquals(1, status.takenDoses);
        assertEquals(0, status.missedCount);
    }

    @Test
    public void openTakeWindow_dueDoseNotMissedYet_inProgressThenMissedAfterWindow() {
        // yesterday 23:50 dose: at 00:00 its time has passed but the ±3h
        // take-window is still open, so the dose is due yet still takeable
        // (not missed, card shows in-progress); the same dose turns missed
        // once the window has fully elapsed (02:50, inclusive boundary)
        Date now = date(7, 0, 0);
        List<MedicineDoseOccurrence> occurrences = Collections.singletonList(
                occurrence(1L, date(6, 23, 50)));

        List<TodayStatus> openWindowStatuses = compute(now, occurrences, Collections.emptyList());

        assertEquals(1, openWindowStatuses.size());
        TodayStatus openWindowStatus = openWindowStatuses.get(0);
        assertEquals(TodayStatus.STATUS_IN_PROGRESS, openWindowStatus.status);
        assertEquals(1, openWindowStatus.totalDoses);
        assertEquals(0, openWindowStatus.takenDoses);
        assertEquals(0, openWindowStatus.missedCount);
        assertNull(openWindowStatus.missedMedicineName);
        assertNull(openWindowStatus.missedDoseTime);
        assertNull(openWindowStatus.nextDoseTime);

        List<TodayStatus> closedWindowStatuses = compute(date(7, 2, 50), occurrences,
                Collections.emptyList());

        assertEquals(1, closedWindowStatuses.size());
        TodayStatus closedWindowStatus = closedWindowStatuses.get(0);
        assertEquals(TodayStatus.STATUS_MISSED, closedWindowStatus.status);
        assertEquals(1, closedWindowStatus.missedCount);
        assertEquals(date(6, 23, 50), closedWindowStatus.missedDoseTime);
    }

    @Test
    public void multiMedicine_missedHighlightIsGenuinelyEarliestAcrossMedicines() {
        // the per-medicine groups come from a HashMap, so the merged unmatched
        // list must be re-sorted before the missed scan: medicine one's group
        // (HashMap bucket of 1 < 2) is always iterated first and its 14:00 dose
        // is the LATER missed one, medicine two's 06:00 dose is the earliest
        Date now = date(7, 20, 0);
        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "test profile";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine(1L, 1L, "medicine one"));
        medicineMap.put(2L, medicine(2L, 1L, "medicine two"));
        Map<Long, Note> noteMap = new HashMap<>();
        noteMap.put(1L, note(1L, 1L));
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, date(7, 14, 0)),
                occurrence(2L, date(7, 6, 0)));

        List<TodayStatus> statuses = TodayCalculator.compute(now, occurrences,
                Collections.emptyList(), medicineMap, noteMap,
                Collections.singletonList(profile));

        assertEquals(1, statuses.size());
        TodayStatus status = statuses.get(0);
        assertEquals(TodayStatus.STATUS_MISSED, status.status);
        assertEquals(2, status.totalDoses);
        assertEquals(2, status.missedCount);
        // the missed highlight is medicine two's 06:00 one, NOT the first
        // iterated group's 14:00 dose
        assertEquals("medicine two", status.missedMedicineName);
        assertEquals(date(7, 6, 0), status.missedDoseTime);
    }

    @Test
    public void crossMedicineIsolation_intakeOfMedicineB_neverMarksMedicineA() {
        // profile has medicine A (08:00, no intake) and medicine B (08:10,
        // intake at 08:20); pooling the profile's intakes would let B's intake
        // mark A's 08:00 dose taken (20 minutes, inside the window) and report
        // medicine B as the missed one
        Date now = date(7, 12, 0);
        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "test profile";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine(1L, 1L, "medicine A"));
        medicineMap.put(2L, medicine(2L, 1L, "medicine B"));
        Map<Long, Note> noteMap = new HashMap<>();
        noteMap.put(1L, note(1L, 1L));
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, date(7, 8, 0)),
                occurrence(2L, date(7, 8, 10)));
        List<MedicineIntake> intakes = Collections.singletonList(
                intake(2L, date(7, 8, 20)));

        List<TodayStatus> statuses = TodayCalculator.compute(now, occurrences, intakes,
                medicineMap, noteMap, Collections.singletonList(profile));

        assertEquals(1, statuses.size());
        TodayStatus status = statuses.get(0);
        assertEquals(2, status.totalDoses);
        assertEquals(1, status.takenDoses);
        assertEquals(1, status.missedCount);
        // the missed dose is medicine A's 08:00 one, NOT medicine B's
        assertEquals("medicine A", status.missedMedicineName);
        assertEquals(date(7, 8, 0), status.missedDoseTime);
    }

    @Test
    public void multipleProfiles_isolatedAndSorted() {
        // profile A (missed), B (in-progress) and C (done) share the same name so
        // the order must come purely from the status, profile D has no
        // occurrence today and is omitted
        Date now = date(7, 12, 0);
        Profile profileA = new Profile();
        profileA.id = 1L;
        profileA.name = "same name";
        Profile profileB = new Profile();
        profileB.id = 2L;
        profileB.name = "same name";
        Profile profileC = new Profile();
        profileC.id = 3L;
        profileC.name = "same name";
        Profile profileD = new Profile();
        profileD.id = 4L;
        profileD.name = "same name";
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicine(1L, 1L, "medicine A"));
        medicineMap.put(2L, medicine(2L, 2L, "medicine B"));
        medicineMap.put(3L, medicine(3L, 3L, "medicine C"));
        medicineMap.put(4L, medicine(4L, 4L, "medicine D"));
        Map<Long, Note> noteMap = new HashMap<>();
        noteMap.put(1L, note(1L, 1L));
        noteMap.put(2L, note(2L, 2L));
        noteMap.put(3L, note(3L, 3L));
        noteMap.put(4L, note(4L, 4L));
        // A: 08:00 dose untaken -> missed, B: 08:00 taken + 20:00 upcoming,
        // C: 08:00 taken -> done, D: no occurrence today -> omitted
        List<MedicineDoseOccurrence> occurrences = Arrays.asList(
                occurrence(1L, date(7, 8, 0)),
                occurrence(2L, date(7, 8, 0)),
                occurrence(2L, date(7, 20, 0)),
                occurrence(3L, date(7, 8, 0)));
        List<MedicineIntake> intakes = Arrays.asList(
                intake(2L, date(7, 8, 5)),
                intake(3L, date(7, 8, 5)));

        List<TodayStatus> statuses = TodayCalculator.compute(now, occurrences, intakes,
                medicineMap, noteMap, Arrays.asList(profileA, profileB, profileC, profileD));

        assertEquals(3, statuses.size());
        assertEquals(TodayStatus.STATUS_MISSED, statuses.get(0).status);
        assertEquals(1L, statuses.get(0).profileId);
        assertEquals("medicine A", statuses.get(0).missedMedicineName);
        assertEquals(TodayStatus.STATUS_IN_PROGRESS, statuses.get(1).status);
        assertEquals(2L, statuses.get(1).profileId);
        assertEquals(date(7, 20, 0), statuses.get(1).nextDoseTime);
        assertEquals(TodayStatus.STATUS_DONE, statuses.get(2).status);
        assertEquals(3L, statuses.get(2).profileId);
    }
}
