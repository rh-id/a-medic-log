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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
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

    private static List<TodayStatus> compute(Date now, List<MedicineIntake> intakes,
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
        return TodayCalculator.compute(now, intakes, reminders,
                medicineMap, noteMap, Collections.singletonList(profile));
    }

    @Test
    public void weekdayNotScheduled_profileOmitted() {
        // today is Monday Sep 7, but the reminder only fires on Thursdays
        Date now = date(7, 9, 0);
        MedicineReminder reminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0), Calendar.THURSDAY);

        List<TodayStatus> statuses = compute(now,
                Collections.emptyList(), Collections.singletonList(reminder));

        assertTrue(statuses.isEmpty());
    }

    @Test
    public void allDosesTaken_statusDone() {
        Date now = date(7, 21, 0);
        MedicineReminder morningReminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY);
        MedicineReminder eveningReminder = reminder(1L,
                date(1, 20, 0), date(1, 20, 0), Calendar.MONDAY);

        List<TodayStatus> statuses = compute(now,
                Arrays.asList(
                        intake(1L, date(7, 8, 5)),
                        intake(1L, date(7, 20, 5))),
                Arrays.asList(morningReminder, eveningReminder));

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
        Date now = date(7, 9, 0);
        MedicineReminder reminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY);

        List<TodayStatus> statuses = compute(now,
                Collections.emptyList(), Collections.singletonList(reminder));

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
        MedicineReminder morningReminder = reminder(1L,
                date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY);
        MedicineReminder eveningReminder = reminder(1L,
                date(1, 20, 0), date(1, 20, 0), Calendar.MONDAY);

        List<TodayStatus> statuses = compute(now,
                Collections.singletonList(intake(1L, date(7, 8, 5))),
                Arrays.asList(morningReminder, eveningReminder));

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
    public void occurrenceBeforeCreatedDateTime_notExpected() {
        // reminder created today at noon, its 08:00 occurrence is before creation
        Date now = date(7, 13, 0);
        MedicineReminder reminder = reminder(1L,
                date(7, 8, 0), date(7, 12, 0), Calendar.MONDAY);

        List<TodayStatus> statuses = compute(now,
                Collections.emptyList(), Collections.singletonList(reminder));

        assertTrue(statuses.isEmpty());
    }

    @Test
    public void occurrenceBeforeStartDateTime_notExpected() {
        // starts Tuesday Sep 8 at 08:00, so the Monday Sep 7 occurrence is before start
        Date now = date(7, 12, 0);
        MedicineReminder reminder = reminder(1L,
                date(8, 8, 0), date(1, 8, 0),
                Calendar.MONDAY, Calendar.TUESDAY);

        List<TodayStatus> statuses = compute(now,
                Collections.emptyList(), Collections.singletonList(reminder));

        assertTrue(statuses.isEmpty());
    }

    @Test
    public void multipleProfiles_isolatedAndSorted() {
        // profile A (missed), B (in-progress) and C (done) share the same name so
        // the order must come purely from the status, profile D is not scheduled
        // today (Thursday) and is omitted
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
        Medicine medicineA = new Medicine();
        medicineA.id = 1L;
        medicineA.noteId = 1L;
        medicineA.name = "medicine A";
        Medicine medicineB = new Medicine();
        medicineB.id = 2L;
        medicineB.noteId = 2L;
        medicineB.name = "medicine B";
        Medicine medicineC = new Medicine();
        medicineC.id = 3L;
        medicineC.noteId = 3L;
        medicineC.name = "medicine C";
        Medicine medicineD = new Medicine();
        medicineD.id = 4L;
        medicineD.noteId = 4L;
        medicineD.name = "medicine D";
        Note noteA = new Note();
        noteA.id = 1L;
        noteA.profileId = 1L;
        Note noteB = new Note();
        noteB.id = 2L;
        noteB.profileId = 2L;
        Note noteC = new Note();
        noteC.id = 3L;
        noteC.profileId = 3L;
        Note noteD = new Note();
        noteD.id = 4L;
        noteD.profileId = 4L;
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicineMap.put(1L, medicineA);
        medicineMap.put(2L, medicineB);
        medicineMap.put(3L, medicineC);
        medicineMap.put(4L, medicineD);
        Map<Long, Note> noteMap = new HashMap<>();
        noteMap.put(1L, noteA);
        noteMap.put(2L, noteB);
        noteMap.put(3L, noteC);
        noteMap.put(4L, noteD);
        // A: 08:00 dose untaken -> missed, B: 08:00 taken + 20:00 upcoming,
        // C: 08:00 taken -> done, D: Thursday only -> omitted
        List<MedicineIntake> intakes = Arrays.asList(
                intake(2L, date(7, 8, 5)),
                intake(3L, date(7, 8, 5)));
        List<MedicineReminder> reminders = Arrays.asList(
                reminder(1L, date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY),
                reminder(2L, date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY),
                reminder(2L, date(1, 20, 0), date(1, 20, 0), Calendar.MONDAY),
                reminder(3L, date(1, 8, 0), date(1, 8, 0), Calendar.MONDAY),
                reminder(4L, date(1, 8, 0), date(1, 8, 0), Calendar.THURSDAY));

        List<TodayStatus> statuses = TodayCalculator.compute(now, intakes, reminders,
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
