package m.co.rh.id.a_medic_log.base.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDoseOccurrenceDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.aprovider.Provider;

/**
 * Instrumented tests of {@link MedicineRepository} against the real in-memory
 * Room database (real UNIQUE index, real FK cascades, real nested
 * transactions). This suite owns ALL occurrence coverage: the repository-level
 * bookkeeping merged from the former JVM MedicineRepositoryOccurrenceTest
 * (write-side and bookkeeping must be the same call) and, since the fake-DAO
 * JVM suite was removed, the fixed-date algorithm-core coverage of the former
 * MedicineRepositoryOccurrenceCoreTest on REAL Room. The cores and the pure
 * bookkeeping entry points take every schedule bound as an explicit parameter
 * (never a clock read), so fixed 2026 anchor dates stay deterministic whatever
 * day the suite runs on; fixture rows whose created/start predate the run date
 * are planted through the real {@link MedicineReminderDao} because the public
 * insert entry point stamps and generates around the real clock. The
 * repository captures its operation time with new Date() internally (see the
 * repository's class javadoc), so the clock-aware entry-point tests derive
 * their dates from the real clock and assert clock-aware invariants instead
 * of fixed timestamps.
 */
@RunWith(AndroidJUnit4.class)
public class MedicineRepositoryTest {

    private Provider mProvider;
    private MedicineRepository mMedicineRepository;
    private NoteDao mNoteDao;
    private MedicineDao mMedicineDao;
    private MedicineReminderDao mMedicineReminderDao;
    private MedicineIntakeDao mMedicineIntakeDao;
    private MedicineDoseOccurrenceDao mMedicineDoseOccurrenceDao;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new RepositoryTestProviderModule());
        mNoteDao = mProvider.get(NoteDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mMedicineReminderDao = mProvider.get(MedicineReminderDao.class);
        mMedicineIntakeDao = mProvider.get(MedicineIntakeDao.class);
        mMedicineDoseOccurrenceDao = mProvider.get(MedicineDoseOccurrenceDao.class);
        // package-private test seam wired with the real database and its
        // real DAOs, so the occurrence bookkeeping runs against real SQLite
        mMedicineRepository = new MedicineRepository(mProvider.get(AppDatabase.class),
                mMedicineDao, mMedicineReminderDao, mMedicineIntakeDao,
                mMedicineDoseOccurrenceDao);
    }

    @After
    public void cleanup() {
        mProvider.dispose();
    }

    private static MedicineIntake createMedicineIntake(Long medicineId) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        return medicineIntake;
    }

    /**
     * Inserts a parent note row directly and returns a medicine owned by it,
     * medicine and reminders are inserted through the medicine repository
     */
    private Medicine insertMedicine(String name, int reminderCount) {
        Note note = new Note();
        note.content = "note for " + name;
        note.id = mNoteDao.insert(note);
        Medicine medicine = new Medicine();
        medicine.name = name;
        medicine.noteId = note.id;
        List<MedicineReminder> medicineReminders = new ArrayList<>();
        for (int i = 0; i < reminderCount; i++) {
            MedicineReminder medicineReminder = new MedicineReminder();
            medicineReminder.message = name + " reminder " + i;
            medicineReminders.add(medicineReminder);
        }
        mMedicineRepository.insertMedicine(medicine, medicineReminders);
        return medicine;
    }

    // ---- date helpers for the occurrence tests, all relative to the real "now" ----

    /**
     * @return the current day shifted by dayOffset at the given local time
     */
    private static Date dayAt(int dayOffset, int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, dayOffset);
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * @return a daily reminder (all 7 days) with the given creation date and
     * the occurrence time-of-day taken from the start date's day
     */
    private static MedicineReminder reminder(Long medicineId, Date createdDateTime,
                                             int hourOfDay, int minute) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.id = null;
        medicineReminder.medicineId = medicineId;
        medicineReminder.reminderEnabled = true;
        medicineReminder.createdDateTime = createdDateTime;
        medicineReminder.updatedDateTime = createdDateTime;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(createdDateTime);
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        medicineReminder.startDateTime = calendar.getTime();
        medicineReminder.reminderDays = new LinkedHashSet<>(Arrays.asList(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY));
        return medicineReminder;
    }

    /**
     * Occurrence history of one reminder read from the real table; the DAO's
     * range query already orders by scheduled_date_time ASC so the row order
     * matches the store view of the former JVM fake. The +/-31-day window
     * safely covers every date these tests create (all relative to now).
     */
    private List<MedicineDoseOccurrence> findOccurrencesByReminderId(long reminderId) {
        List<MedicineDoseOccurrence> result = new ArrayList<>();
        for (MedicineDoseOccurrence occurrence : findAllOccurrences()) {
            if (occurrence.reminderId == reminderId) {
                result.add(occurrence);
            }
        }
        return result;
    }

    /**
     * All occurrence rows in the +/-31-day window around now
     */
    private List<MedicineDoseOccurrence> findAllOccurrences() {
        return mMedicineDoseOccurrenceDao
                .findMedicineDoseOccurrencesByScheduledDateTimeBetween(
                        dayAt(-31, 0, 0).getTime(), dayAt(31, 23, 59).getTime());
    }

    // ---- fixed 2026 anchor dates for the ported fixed-date core coverage;
    // the cores and the catch-up/backfill entry points take every range bound
    // as an explicit parameter (never a clock read), so the assertions are
    // deterministic whatever day the suite runs on ----

    private static final int FIXED_YEAR = 2026;

    private static Date fixedDate(int month, int dayOfMonth, int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(FIXED_YEAR, month, dayOfMonth, hourOfDay, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * Plants a reminder row through the REAL reminder DAO (no repository
     * bookkeeping): the fixed-date tests need reminders whose created/start
     * values predate the run date, which the public insert entry point cannot
     * produce (it stamps and generates around the real clock). All bookkeeping
     * in those tests is then driven through the repository's public
     * catch-up/backfill entry points, and only the one documented core call
     * exercises an overlapping regeneration window (see
     * routineCatchUp_isIdempotent_realRoomIgnoresDuplicateProposals).
     */
    private MedicineReminder plantReminder(Long medicineId, Date startDateTime,
                                           Date createdDateTime, boolean enabled,
                                           Integer... days) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.medicineId = medicineId;
        medicineReminder.message = "planted reminder";
        medicineReminder.startDateTime = startDateTime;
        medicineReminder.createdDateTime = createdDateTime;
        medicineReminder.updatedDateTime = createdDateTime;
        medicineReminder.reminderEnabled = enabled;
        medicineReminder.reminderDays = new LinkedHashSet<>(Arrays.asList(days));
        medicineReminder.id = mMedicineReminderDao.insert(medicineReminder);
        return medicineReminder;
    }

    /**
     * All occurrence rows inside the fixed 2026 window, ordered by
     * scheduled_date_time ASC (the DAO's range query order)
     */
    private List<MedicineDoseOccurrence> findFixedWindowOccurrences() {
        return mMedicineDoseOccurrenceDao
                .findMedicineDoseOccurrencesByScheduledDateTimeBetween(
                        fixedDate(Calendar.SEPTEMBER, 1, 0, 0).getTime(),
                        fixedDate(Calendar.DECEMBER, 31, 23, 59).getTime());
    }

    private List<MedicineDoseOccurrence> findFixedWindowOccurrencesByReminderId(long reminderId) {
        List<MedicineDoseOccurrence> result = new ArrayList<>();
        for (MedicineDoseOccurrence occurrence : findFixedWindowOccurrences()) {
            if (occurrence.reminderId == reminderId) {
                result.add(occurrence);
            }
        }
        return result;
    }

    /**
     * The scheduled dates of the given rows in row order; Date equality is
     * value-based so two reads of the real table compare equal
     */
    private static List<Date> scheduledDates(List<MedicineDoseOccurrence> occurrences) {
        List<Date> dates = new ArrayList<>();
        for (MedicineDoseOccurrence occurrence : occurrences) {
            dates.add(occurrence.scheduledDateTime);
        }
        return dates;
    }

    @Test
    public void insertMedicine_backFillsMedicineAndReminderIds() {
        Note note = new Note();
        note.content = "note";
        note.id = mNoteDao.insert(note);
        Medicine medicine = new Medicine();
        medicine.name = "test medicine";
        medicine.noteId = note.id;
        List<MedicineReminder> medicineReminders = new ArrayList<>();
        MedicineReminder medicineReminder0 = new MedicineReminder();
        medicineReminder0.message = "reminder 0";
        MedicineReminder medicineReminder1 = new MedicineReminder();
        medicineReminder1.message = "reminder 1";
        medicineReminders.add(medicineReminder0);
        medicineReminders.add(medicineReminder1);

        mMedicineRepository.insertMedicine(medicine, medicineReminders);

        // ids are back-filled on the passed payloads
        assertNotNull(medicine.id);
        for (MedicineReminder medicineReminder : medicineReminders) {
            assertNotNull(medicineReminder.id);
            assertEquals(medicine.id, medicineReminder.medicineId);
        }
    }

    @Test
    public void queryMedicineInfo_returnsMedicineWithItsReminders() {
        Medicine medicine = insertMedicine("test medicine", 2);

        MedicineState freshMedicineState = mMedicineRepository.queryMedicineInfo(medicine.id);

        assertEquals("test medicine", freshMedicineState.getMedicineName());
        assertEquals(medicine.id, freshMedicineState.getMedicineId());
        assertEquals(2, freshMedicineState.getMedicineReminderList().size());
        List<String> reminderMessages = new ArrayList<>();
        for (MedicineReminder freshReminder : freshMedicineState.getMedicineReminderList()) {
            reminderMessages.add(freshReminder.message);
        }
        assertTrue(reminderMessages.contains("test medicine reminder 0"));
        assertTrue(reminderMessages.contains("test medicine reminder 1"));
    }

    @Test
    public void queryMedicineCounts_countsStateRemindersAndOwnedIntakes() {
        Medicine medicine = insertMedicine("medicine with intake", 2);
        mMedicineIntakeDao.insert(createMedicineIntake(medicine.id));
        mMedicineIntakeDao.insert(createMedicineIntake(medicine.id));
        mMedicineIntakeDao.insert(createMedicineIntake(medicine.id));

        // reminders come from the state, intakes are counted via DAO
        MedicineRepository.MedicineCounts medicineCounts = mMedicineRepository.queryMedicineCounts(
                mMedicineRepository.queryMedicineInfo(medicine.id));
        assertEquals(2, medicineCounts.reminderCount);
        assertEquals(3, medicineCounts.intakeCount);

        // records of another medicine are not counted
        Medicine otherMedicine = insertMedicine("other medicine", 1);
        mMedicineIntakeDao.insert(createMedicineIntake(otherMedicine.id));
        MedicineRepository.MedicineCounts otherMedicineCounts = mMedicineRepository.queryMedicineCounts(
                mMedicineRepository.queryMedicineInfo(otherMedicine.id));
        assertEquals(1, otherMedicineCounts.reminderCount);
        assertEquals(1, otherMedicineCounts.intakeCount);
    }

    @Test
    public void deleteMedicineByMedicineId_cascadesRemindersAndIntakes() {
        Medicine medicine = insertMedicine("medicine to delete", 2);
        mMedicineIntakeDao.insert(createMedicineIntake(medicine.id));
        mMedicineIntakeDao.insert(createMedicineIntake(medicine.id));

        mMedicineRepository.deleteMedicineByMedicineId(medicine.id);

        // medicine and everything it owns is gone (FK ON DELETE CASCADE)
        assertNull(mMedicineDao.findMedicineById(medicine.id));
        assertEquals(0, mMedicineReminderDao.findMedicineRemindersByMedicineId(medicine.id).size());
        assertEquals(0, mMedicineIntakeDao.countMedicineIntakesByMedicineId(medicine.id));
    }

    // ---- repository-level occurrence bookkeeping, run against the real table ----

    @Test
    public void insertMedicineReminder_stampsCreateTimes_andGeneratesTodaysOccurrenceWhenEnabled() {
        Medicine medicine = insertMedicine("occurrence host", 0);
        Date created = dayAt(0, 0, 0);
        MedicineReminder enabled = reminder(medicine.id, created, 8, 0);

        mMedicineRepository.insertMedicineReminder(enabled);

        assertNotNull(enabled.id);
        // create-time stamp: enabled reminder records its enable time
        assertEquals(created, enabled.lastEnabledDateTime);
        assertNull(enabled.lastDisabledDateTime);
        // the table is maintained through end-of-today: exactly today's dose
        List<MedicineDoseOccurrence> occurrences = findOccurrencesByReminderId(enabled.id);
        assertEquals(1, occurrences.size());
        assertEquals(dayAt(0, 8, 0), occurrences.get(0).scheduledDateTime);

        // a disabled reminder records its disable time and generates nothing
        MedicineReminder disabled = reminder(medicine.id, created, 9, 0);
        disabled.reminderEnabled = false;
        mMedicineRepository.insertMedicineReminder(disabled);
        assertEquals(created, disabled.lastDisabledDateTime);
        assertNull(disabled.lastEnabledDateTime);
        assertEquals(1, findAllOccurrences().size());
    }

    @Test
    public void insertMedicine_nestedReminderSave_stampsBothAndGeneratesTodaysOccurrences() {
        Note note = new Note();
        note.content = "note for nested reminder save";
        note.id = mNoteDao.insert(note);
        Medicine medicine = new Medicine();
        medicine.name = "Test medicine";
        medicine.noteId = note.id;
        // medicine id not assigned yet, insertMedicine links it before writing
        Date created = dayAt(0, 0, 0);
        MedicineReminder first = reminder(null, created, 8, 0);
        MedicineReminder second = reminder(null, created, 9, 0);
        List<MedicineReminder> reminders = new ArrayList<>();
        reminders.add(first);
        reminders.add(second);

        mMedicineRepository.insertMedicine(medicine, reminders);

        // all rows written and the nested insertMedicineReminder bookkeeping
        // coalesced into the same real enclosing transaction (same-thread
        // nested transactions join the outermost one on this thread)
        assertNotNull(medicine.id);
        assertNotNull(first.id);
        assertNotNull(second.id);
        assertEquals(created, first.lastEnabledDateTime);
        assertNull(first.lastDisabledDateTime);
        assertEquals(created, second.lastEnabledDateTime);
        assertNull(second.lastDisabledDateTime);
        // exactly today's dose for each reminder (the table is maintained
        // through end-of-today)
        List<MedicineDoseOccurrence> firstOccurrences =
                findOccurrencesByReminderId(first.id);
        assertEquals(1, firstOccurrences.size());
        assertEquals(dayAt(0, 8, 0), firstOccurrences.get(0).scheduledDateTime);
        List<MedicineDoseOccurrence> secondOccurrences =
                findOccurrencesByReminderId(second.id);
        assertEquals(1, secondOccurrences.size());
        assertEquals(dayAt(0, 9, 0), secondOccurrences.get(0).scheduledDateTime);
    }

    @Test
    public void updateMedicineReminder_stampsFromFreshRead_notFromCallerSnapshot() {
        Medicine medicine = insertMedicine("stale snapshot host", 0);
        Date created = dayAt(0, 0, 0);
        MedicineReminder reminder = reminder(medicine.id, created, 8, 0);
        mMedicineRepository.insertMedicineReminder(reminder);

        // stale caller snapshot: it believes the row is disabled and pre-stamps
        // a bogus enable time; there is no transition against the fresh read,
        // so the persisted stamp value must win over the caller's belief
        Date bogusStamp = dayAt(0, 12, 0);
        MedicineReminder editedNoTransition = reminder.clone();
        editedNoTransition.lastEnabledDateTime = bogusStamp;
        mMedicineRepository.updateMedicineReminder(editedNoTransition);
        MedicineReminder stored = mMedicineReminderDao.findMedicineReminderById(reminder.id);
        assertEquals(created, stored.lastEnabledDateTime);
        assertNull(stored.lastDisabledDateTime);

        // stale caller snapshot again: it believes the row is already disabled
        // and stamps nothing, but the fresh read sees enabled=true, so the
        // true -> false transition must still be stamped from the fresh read
        MedicineReminder editedDisable = reminder.clone();
        editedDisable.reminderEnabled = false;
        Date beforeDisable = new Date();
        mMedicineRepository.updateMedicineReminder(editedDisable);
        stored = mMedicineReminderDao.findMedicineReminderById(reminder.id);
        assertNotNull(stored.lastDisabledDateTime);
        assertFalse(stored.lastDisabledDateTime.before(beforeDisable));
        // the enable stamp is never cleared
        assertEquals(created, stored.lastEnabledDateTime);
        // disabled: any surviving occurrence is history (<= now), nothing new
        // was generated for the disabled reminder
        for (MedicineDoseOccurrence occurrence : findOccurrencesByReminderId(reminder.id)) {
            assertFalse(occurrence.scheduledDateTime.after(new Date()));
        }
    }

    @Test
    public void deleteMedicineReminder_truncatesStrictlyAfterNow_keepsPastRows() {
        Medicine medicine = insertMedicine("delete truncation host", 0);
        MedicineReminder reminder = reminder(medicine.id, dayAt(-1, 0, 0), 23, 59);
        mMedicineRepository.insertMedicineReminder(reminder);
        // yesterday 23:59 (history) and today 23:59 (not yet due)
        assertEquals(2, findOccurrencesByReminderId(reminder.id).size());

        Date beforeDelete = new Date();
        mMedicineRepository.deleteMedicineReminder(reminder.id);

        assertNull(mMedicineReminderDao.findMedicineReminderById(reminder.id));
        List<MedicineDoseOccurrence> occurrences = findOccurrencesByReminderId(reminder.id);
        // today's dose is removed because it was scheduled strictly after the
        // (pre-delete captured) operation time; rows <= now are history, so a
        // run in the 23:59 minute legitimately keeps it and only yesterday's
        // dose (always history) plus today's survive
        Date todayDose = dayAt(0, 23, 59);
        boolean todayDoseWasStillDue = todayDose.after(beforeDelete);
        assertEquals(todayDoseWasStillDue ? 1 : 2, occurrences.size());
        // yesterday's dose survives as immutable history either way
        assertEquals(dayAt(-1, 23, 59), occurrences.get(0).scheduledDateTime);
        if (todayDoseWasStillDue) {
            assertEquals(1, occurrences.size());
        } else {
            assertEquals(dayAt(0, 23, 59), occurrences.get(1).scheduledDateTime);
        }
    }

    @Test
    public void updateMedicine_nestedDiff_stampsNew_truncatesAndRegeneratesExisting_truncatesDisabled() {
        Medicine medicine = insertMedicine("Test medicine", 0);

        // two existing reminders inserted through the repository first
        MedicineReminder existingEdited = reminder(medicine.id, dayAt(-1, 0, 0), 23, 59);
        mMedicineRepository.insertMedicineReminder(existingEdited);
        MedicineReminder existingDisabled = reminder(medicine.id, dayAt(-1, 0, 0), 23, 58);
        mMedicineRepository.insertMedicineReminder(existingDisabled);

        // edit: the edited reminder gets a new time-of-day a couple of minutes
        // ahead of the real clock so the regenerated dose is deterministically
        // still due today; the to-be-disabled reminder keeps its schedule
        Date beforeUpdate = new Date();
        Date newTimeToday = plusMinutes(beforeUpdate, 2);
        boolean newTimeStillToday = newTimeToday.getTime() <= endOfToday(beforeUpdate);

        MedicineReminder editedEdited = existingEdited.clone();
        editedEdited.startDateTime = newTimeToday;
        MedicineReminder editedDisabled = existingDisabled.clone();
        editedDisabled.reminderEnabled = false;
        MedicineReminder newReminder = reminder(medicine.id, dayAt(0, 0, 0),
                0, 0);
        newReminder.startDateTime = plusMinutes(beforeUpdate, 5);

        ArrayList<MedicineReminder> editedReminders = new ArrayList<>();
        editedReminders.add(editedEdited);
        editedReminders.add(editedDisabled);
        editedReminders.add(newReminder);
        mMedicineRepository.updateMedicine(medicine, editedReminders);
        Date afterUpdate = new Date();

        // NEW reminder: create-stamped, inserted with an id and generated
        assertNotNull(newReminder.id);
        assertEquals(dayAt(0, 0, 0), newReminder.lastEnabledDateTime);
        assertNull(newReminder.lastDisabledDateTime);
        List<MedicineDoseOccurrence> newOccurrences =
                findOccurrencesByReminderId(newReminder.id);
        if (newReminder.startDateTime.getTime() > endOfToday(beforeUpdate)) {
            // start crossed midnight while building the entity: nothing can be
            // generated inside today, skip the presence assert
            assertEquals(0, newOccurrences.size());
        } else {
            assertEquals(1, newOccurrences.size());
            assertEquals(newReminder.startDateTime, newOccurrences.get(0).scheduledDateTime);
        }

        // EDITED existing reminder: truncate + regenerate, history <= now is
        // immutable, the old today-dose (23:59) is gone when it was still due
        List<MedicineDoseOccurrence> editedOccurrences =
                findOccurrencesByReminderId(existingEdited.id);
        boolean containsHistory = false;
        for (MedicineDoseOccurrence occurrence : editedOccurrences) {
            if (occurrence.scheduledDateTime.equals(dayAt(-1, 23, 59))) {
                containsHistory = true;
            }
            // everything remaining is either old history or the new schedule
            if (occurrence.scheduledDateTime.after(afterUpdate)) {
                assertEquals(newTimeToday, occurrence.scheduledDateTime);
            }
        }
        assertTrue(containsHistory);
        Date oldTodayDose = dayAt(0, 23, 59);
        if (oldTodayDose.after(beforeUpdate)) {
            for (MedicineDoseOccurrence occurrence : editedOccurrences) {
                assertFalse(occurrence.scheduledDateTime.equals(oldTodayDose));
            }
        }
        if (newTimeStillToday) {
            // the new schedule took effect: exactly one regenerated dose
            assertEquals(1, countAt(editedOccurrences, newTimeToday));
        }
        // no transition: the persisted enable stamp survives
        MedicineReminder storedEdited =
                mMedicineReminderDao.findMedicineReminderById(existingEdited.id);
        assertEquals(dayAt(-1, 0, 0), storedEdited.lastEnabledDateTime);
        assertNull(storedEdited.lastDisabledDateTime);

        // DISABLED existing reminder: transition stamped, truncated, not regenerated
        MedicineReminder storedDisabled =
                mMedicineReminderDao.findMedicineReminderById(existingDisabled.id);
        assertFalse(storedDisabled.reminderEnabled);
        assertNotNull(storedDisabled.lastDisabledDateTime);
        assertFalse(storedDisabled.lastDisabledDateTime.before(beforeUpdate));
        assertEquals(dayAt(-1, 0, 0), storedDisabled.lastEnabledDateTime);
        List<MedicineDoseOccurrence> disabledOccurrences =
                findOccurrencesByReminderId(existingDisabled.id);
        Date disabledOldTodayDose = dayAt(0, 23, 58);
        if (disabledOldTodayDose.after(beforeUpdate)) {
            // the dose was still due at the update, the truncation removed it
            assertEquals(1, disabledOccurrences.size());
            assertEquals(dayAt(-1, 23, 58), disabledOccurrences.get(0).scheduledDateTime);
        } else {
            // run inside the 23:58 minute, the dose was already history
            for (MedicineDoseOccurrence occurrence : disabledOccurrences) {
                assertTrue(occurrence.scheduledDateTime.equals(dayAt(-1, 23, 58))
                        || occurrence.scheduledDateTime.equals(disabledOldTodayDose));
            }
        }
    }

    @Test
    public void disableThenReEnable_thenCatchUp_offPeriodNeverRegenerated() {
        Medicine medicine = insertMedicine("off-period host", 0);
        MedicineReminder reminder = reminder(medicine.id, dayAt(-5, 0, 0), 23, 59);
        mMedicineRepository.insertMedicineReminder(reminder);

        // disable: stamp + truncate the not-yet-due doses
        MedicineReminder disabled = reminder.clone();
        disabled.reminderEnabled = false;
        mMedicineRepository.updateMedicineReminder(disabled);
        MedicineReminder storedDisabled = mMedicineReminderDao.findMedicineReminderById(reminder.id);
        Date disableTime = storedDisabled.lastDisabledDateTime;
        assertNotNull(disableTime);
        int occurrencesWhileDisabled = findOccurrencesByReminderId(reminder.id).size();

        // catch-up while disabled is a no-op
        mMedicineRepository.catchUpAllEnabled(dayAt(3, 12, 0));
        assertEquals(occurrencesWhileDisabled,
                findOccurrencesByReminderId(reminder.id).size());

        // re-enable
        MedicineReminder reEnabled = disabled.clone();
        reEnabled.reminderEnabled = true;
        mMedicineRepository.updateMedicineReminder(reEnabled);
        MedicineReminder storedReEnabled = mMedicineReminderDao.findMedicineReminderById(reminder.id);
        Date enableTime = storedReEnabled.lastEnabledDateTime;
        assertNotNull(enableTime);
        assertFalse(enableTime.before(disableTime));

        // catch-up well past the off-period
        mMedicineRepository.catchUpAllEnabled(dayAt(3, 12, 0));

        List<MedicineDoseOccurrence> occurrences = findOccurrencesByReminderId(reminder.id);
        int resumed = 0;
        for (MedicineDoseOccurrence occurrence : occurrences) {
            long scheduledMillis = occurrence.scheduledDateTime.getTime();
            boolean inOffPeriod = scheduledMillis > disableTime.getTime()
                    && scheduledMillis < enableTime.getTime();
            // CRITICAL: zero occurrences strictly inside the off-period
            assertFalse(inOffPeriod);
            if (scheduledMillis >= enableTime.getTime()) {
                resumed++;
            }
        }
        // the history <= disable time survived untouched and the schedule
        // resumed after the re-enable
        assertTrue(occurrences.size() >= occurrencesWhileDisabled);
        assertTrue(resumed >= 3);
    }

    // ---- fixed-date core coverage ported from the former fake-DAO JVM suite
    // (MedicineRepositoryOccurrenceCoreTest), now on REAL Room ----

    @Test
    public void backfillOccurrences_startPredatesCreated_mondayDosesWithinBoundsOnly() {
        Medicine medicine = insertMedicine("backfill bounds host", 0);
        // Monday-only 08:00 reminder that already started Monday Sep 28 08:00
        // but was only created Monday Oct 5 07:00: the backfill must not
        // rewrite the start-to-created stretch
        MedicineReminder reminder = plantReminder(medicine.id,
                fixedDate(Calendar.SEPTEMBER, 28, 8, 0),
                fixedDate(Calendar.OCTOBER, 5, 7, 0), true, Calendar.MONDAY);

        mMedicineRepository.backfillOccurrences(fixedDate(Calendar.OCTOBER, 18, 12, 0));

        List<MedicineDoseOccurrence> occurrences =
                findFixedWindowOccurrencesByReminderId(reminder.id);
        // exactly Monday Oct 5 08:00 (first dose after created) and Monday
        // Oct 12 08:00: nothing before created, nothing past the ceiling
        // (Monday Oct 19 absent)
        assertEquals(2, occurrences.size());
        assertEquals(fixedDate(Calendar.OCTOBER, 5, 8, 0),
                occurrences.get(0).scheduledDateTime);
        assertEquals(fixedDate(Calendar.OCTOBER, 12, 8, 0),
                occurrences.get(1).scheduledDateTime);
        for (MedicineDoseOccurrence occurrence : occurrences) {
            assertTrue(occurrence.scheduledDateTime.after(reminder.createdDateTime));
            assertTrue(occurrence.scheduledDateTime.before(
                    fixedDate(Calendar.OCTOBER, 19, 0, 0)));
        }
    }

    @Test
    public void backfillOccurrences_skipsDisabledReminders_whateverTheDisableHistory() {
        Medicine medicine = insertMedicine("backfill skip host", 0);
        Date created = fixedDate(Calendar.OCTOBER, 5, 7, 0);
        Date start = fixedDate(Calendar.OCTOBER, 5, 8, 0);
        // pre-upgrade style disabled: no disable time is known
        MedicineReminder disabledWithoutTime = plantReminder(medicine.id, start,
                created, false, Calendar.MONDAY);
        // disabled with a recorded disable time
        MedicineReminder disabledWithTime = plantReminder(medicine.id, start,
                created, false, Calendar.MONDAY);
        disabledWithTime.lastDisabledDateTime = fixedDate(Calendar.OCTOBER, 6, 12, 0);
        mMedicineReminderDao.update(disabledWithTime);
        // enabled control with the identical schedule proves the backfill ran
        MedicineReminder control = plantReminder(medicine.id, start, created,
                true, Calendar.MONDAY);

        mMedicineRepository.backfillOccurrences(fixedDate(Calendar.OCTOBER, 18, 12, 0));

        // control: only Oct 12 08:00 - Oct 5 08:00 is exactly its
        // max(created, start) backfill floor and is strictly excluded
        List<MedicineDoseOccurrence> controlOccurrences =
                findFixedWindowOccurrencesByReminderId(control.id);
        assertEquals(1, controlOccurrences.size());
        assertEquals(fixedDate(Calendar.OCTOBER, 12, 8, 0),
                controlOccurrences.get(0).scheduledDateTime);
        // both disabled reminders are skipped entirely
        assertEquals(0, findFixedWindowOccurrencesByReminderId(
                disabledWithoutTime.id).size());
        assertEquals(0, findFixedWindowOccurrencesByReminderId(
                disabledWithTime.id).size());
    }

    @Test
    public void routineCatchUp_isIdempotent_realRoomIgnoresDuplicateProposals() {
        Medicine medicine = insertMedicine("idempotency host", 0);
        // daily 08:00 reminder, enabled since Thursday Oct 1 08:00
        MedicineReminder reminder = plantReminder(medicine.id,
                fixedDate(Calendar.OCTOBER, 1, 8, 0),
                fixedDate(Calendar.OCTOBER, 1, 8, 0), true,
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
                Calendar.SUNDAY);

        // first catch-up: Friday Oct 2 .. Wednesday Oct 7 08:00 (Oct 1 08:00
        // is exactly the created floor and is strictly excluded)
        mMedicineRepository.catchUpAllEnabled(fixedDate(Calendar.OCTOBER, 7, 12, 0));
        List<Date> afterFirst = scheduledDates(
                findFixedWindowOccurrencesByReminderId(reminder.id));
        assertEquals(Arrays.asList(
                fixedDate(Calendar.OCTOBER, 2, 8, 0),
                fixedDate(Calendar.OCTOBER, 3, 8, 0),
                fixedDate(Calendar.OCTOBER, 4, 8, 0),
                fixedDate(Calendar.OCTOBER, 5, 8, 0),
                fixedDate(Calendar.OCTOBER, 6, 8, 0),
                fixedDate(Calendar.OCTOBER, 7, 8, 0)), afterFirst);

        // identical second catch-up: no new rows, the stored schedule is
        // unchanged
        mMedicineRepository.catchUpAllEnabled(fixedDate(Calendar.OCTOBER, 7, 12, 0));
        List<Date> afterSecond = scheduledDates(
                findFixedWindowOccurrencesByReminderId(reminder.id));
        assertEquals(afterFirst, afterSecond);

        // an overlapping regeneration window re-proposes rows that already
        // exist: real Room's UNIQUE(reminder_id, scheduled_date_time) plus
        // IGNORE drops them (row id -1, not counted) and only Oct 8 / Oct 9
        // are inserted. This is the suite's single package-private core call:
        // no public entry point can re-propose an existing row (the catch-up
        // floor is always >= the latest occurrence), so the real UNIQUE +
        // IGNORE behavior is only observable through the core
        int inserted = mMedicineRepository.generateFor(reminder,
                fixedDate(Calendar.OCTOBER, 3, 8, 0).getTime(),
                fixedDate(Calendar.OCTOBER, 9, 23, 59).getTime());
        assertEquals(2, inserted);
        List<Date> afterOverlap = scheduledDates(
                findFixedWindowOccurrencesByReminderId(reminder.id));
        // Oct 3 08:00 re-proposed at the exact lower bound was NOT duplicated,
        // Oct 2 survived untouched, Oct 8 / Oct 9 appended
        assertEquals(Arrays.asList(
                fixedDate(Calendar.OCTOBER, 2, 8, 0),
                fixedDate(Calendar.OCTOBER, 3, 8, 0),
                fixedDate(Calendar.OCTOBER, 4, 8, 0),
                fixedDate(Calendar.OCTOBER, 5, 8, 0),
                fixedDate(Calendar.OCTOBER, 6, 8, 0),
                fixedDate(Calendar.OCTOBER, 7, 8, 0),
                fixedDate(Calendar.OCTOBER, 8, 8, 0),
                fixedDate(Calendar.OCTOBER, 9, 8, 0)), afterOverlap);
    }

    @Test
    public void dstFallback_dailyReminder_keepsLocalTimeOfDay() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        try {
            Medicine medicine = insertMedicine("dst host", 0);
            // daily 08:00 reminder enabled since Friday Oct 30 08:00 EDT,
            // stepping across the US fallback on Sunday Nov 1 2026
            // (2:00 AM EDT -> 1:00 AM EST, a 25 hour day)
            MedicineReminder reminder = plantReminder(medicine.id,
                    fixedDate(Calendar.OCTOBER, 30, 8, 0),
                    fixedDate(Calendar.OCTOBER, 30, 8, 0), true,
                    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
                    Calendar.SUNDAY);

            mMedicineRepository.catchUpAllEnabled(fixedDate(Calendar.NOVEMBER, 2, 12, 0));

            List<MedicineDoseOccurrence> occurrences =
                    findFixedWindowOccurrencesByReminderId(reminder.id);
            // Oct 30 08:00 is exactly the created floor and is strictly
            // excluded: Oct 31, Nov 1, Nov 2 at local 08:00 each day
            assertEquals(3, occurrences.size());
            assertEquals(fixedDate(Calendar.OCTOBER, 31, 8, 0),
                    occurrences.get(0).scheduledDateTime);
            assertEquals(fixedDate(Calendar.NOVEMBER, 1, 8, 0),
                    occurrences.get(1).scheduledDateTime);
            assertEquals(fixedDate(Calendar.NOVEMBER, 2, 8, 0),
                    occurrences.get(2).scheduledDateTime);
            // the fallback day is a 25 hour step, the next a normal 24: the
            // local time-of-day is kept, never plain millis arithmetic
            assertEquals(25 * 60 * 60 * 1000L,
                    occurrences.get(1).scheduledDateTime.getTime()
                            - occurrences.get(0).scheduledDateTime.getTime());
            assertEquals(24 * 60 * 60 * 1000L,
                    occurrences.get(2).scheduledDateTime.getTime()
                            - occurrences.get(1).scheduledDateTime.getTime());
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void dayOfWeekSet_respectedAcrossMultiWeekWindow() {
        Medicine medicine = insertMedicine("weekly host", 0);
        // Monday + Wednesday only, enabled since Thursday Oct 1 08:00; the
        // window covers the two full weeks Oct 5 .. Oct 18
        MedicineReminder reminder = plantReminder(medicine.id,
                fixedDate(Calendar.OCTOBER, 1, 8, 0),
                fixedDate(Calendar.OCTOBER, 1, 8, 0), true,
                Calendar.MONDAY, Calendar.WEDNESDAY);

        mMedicineRepository.catchUpAllEnabled(fixedDate(Calendar.OCTOBER, 18, 12, 0));

        List<MedicineDoseOccurrence> occurrences =
                findFixedWindowOccurrencesByReminderId(reminder.id);
        assertEquals(4, occurrences.size());
        for (MedicineDoseOccurrence occurrence : occurrences) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(occurrence.scheduledDateTime);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            assertTrue(dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.WEDNESDAY);
        }
        // Mondays Oct 5 / Oct 12 and Wednesdays Oct 7 / Oct 14, nothing on the
        // other days and nothing past the ceiling (Oct 19 and later absent)
        assertEquals(fixedDate(Calendar.OCTOBER, 5, 8, 0),
                occurrences.get(0).scheduledDateTime);
        assertEquals(fixedDate(Calendar.OCTOBER, 7, 8, 0),
                occurrences.get(1).scheduledDateTime);
        assertEquals(fixedDate(Calendar.OCTOBER, 12, 8, 0),
                occurrences.get(2).scheduledDateTime);
        assertEquals(fixedDate(Calendar.OCTOBER, 14, 8, 0),
                occurrences.get(3).scheduledDateTime);
    }

    @Test
    public void catchUp_boundaryArithmetic_floorExcluded_ceilingDayIncluded() {
        Medicine medicine = insertMedicine("boundary host", 0);
        MedicineReminder reminder = plantReminder(medicine.id,
                fixedDate(Calendar.OCTOBER, 1, 8, 0),
                fixedDate(Calendar.OCTOBER, 1, 8, 0), true,
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
                Calendar.SUNDAY);

        // ceiling = end of Friday Oct 3: the dose exactly AT the created floor
        // (Oct 1 08:00) is strictly excluded, the ceiling day's dose (Oct 3
        // 08:00) is included
        mMedicineRepository.catchUpAllEnabled(fixedDate(Calendar.OCTOBER, 3, 12, 0));
        List<MedicineDoseOccurrence> occurrences =
                findFixedWindowOccurrencesByReminderId(reminder.id);
        assertEquals(2, occurrences.size());
        assertEquals(fixedDate(Calendar.OCTOBER, 2, 8, 0),
                occurrences.get(0).scheduledDateTime);
        assertEquals(fixedDate(Calendar.OCTOBER, 3, 8, 0),
                occurrences.get(1).scheduledDateTime);

        // resuming moves the floor to the latest occurrence (Oct 3 08:00):
        // the row AT the floor is not regenerated, exactly Oct 4 and Oct 5 are
        // appended
        mMedicineRepository.catchUpAllEnabled(fixedDate(Calendar.OCTOBER, 5, 12, 0));
        occurrences = findFixedWindowOccurrencesByReminderId(reminder.id);
        assertEquals(4, occurrences.size());
        assertEquals(fixedDate(Calendar.OCTOBER, 4, 8, 0),
                occurrences.get(2).scheduledDateTime);
        assertEquals(fixedDate(Calendar.OCTOBER, 5, 8, 0),
                occurrences.get(3).scheduledDateTime);
    }

    @Test
    public void timeOfDay_takenFromStartDateTime_secondsPreserved() {
        Medicine medicine = insertMedicine("seconds host", 0);
        // start today at 08:15:30: the generated dose keeps the start date
        // time's HOUR/MINUTE/SECOND and zeroes the millis
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(dayAt(0, 8, 15));
        startCalendar.set(Calendar.SECOND, 30);
        Date startDateTime = startCalendar.getTime();
        MedicineReminder reminder = reminder(medicine.id, dayAt(0, 0, 0), 8, 15);
        reminder.startDateTime = startDateTime;

        mMedicineRepository.insertMedicineReminder(reminder);

        List<MedicineDoseOccurrence> occurrences = findOccurrencesByReminderId(reminder.id);
        assertEquals(1, occurrences.size());
        Calendar occurrenceCalendar = Calendar.getInstance();
        occurrenceCalendar.setTime(occurrences.get(0).scheduledDateTime);
        assertEquals(8, occurrenceCalendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(15, occurrenceCalendar.get(Calendar.MINUTE));
        assertEquals(30, occurrenceCalendar.get(Calendar.SECOND));
        assertEquals(0, occurrenceCalendar.get(Calendar.MILLISECOND));
        assertEquals(startDateTime, occurrences.get(0).scheduledDateTime);
    }

    // ---- concurrency: the entry points take no lock, serialization is each
    // call's single encompassing transaction (SQLite single writer — a
    // concurrent transaction blocks, then re-reads fresh state). Both races
    // start their threads on a CountDownLatch for overlap and assert only
    // timing-independent invariants, so no interleaving can fail them ----

    /**
     * Starts two threads released by one latch so their transactions overlap,
     * joins both with a timeout (a regression must fail, not hang) and fails
     * on any throwable either thread recorded.
     */
    private static void runRace(Runnable first, Runnable second, long joinTimeoutMillis)
            throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstThread = new Thread(() -> {
            try {
                start.await();
                first.run();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });
        Thread secondThread = new Thread(() -> {
            try {
                start.await();
                second.run();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });
        firstThread.start();
        secondThread.start();
        start.countDown();
        firstThread.join(joinTimeoutMillis);
        secondThread.join(joinTimeoutMillis);
        assertFalse("race threads did not finish in time", firstThread.isAlive());
        assertFalse("race threads did not finish in time", secondThread.isAlive());
        Throwable thrown = failure.get();
        if (thrown != null) {
            throw new AssertionError("race thread failed", thrown);
        }
    }

    private static void assertNoDuplicateScheduled(List<MedicineDoseOccurrence> occurrences) {
        Set<Date> seen = new HashSet<>();
        for (MedicineDoseOccurrence occurrence : occurrences) {
            assertTrue("duplicate occurrence " + occurrence.scheduledDateTime,
                    seen.add(occurrence.scheduledDateTime));
        }
    }

    @Test
    public void concurrency_disableVersusCatchUp_finalStateEqualsDisableWonState()
            throws InterruptedException {
        Medicine medicine = insertMedicine("race disable host", 0);
        // daily dose late in the day so it is not yet due while the test runs
        for (int iteration = 0; iteration < 25; iteration++) {
            MedicineReminder reminder = reminder(medicine.id, dayAt(0, 0, 0), 23, 59);
            mMedicineRepository.insertMedicineReminder(reminder);

            // disable vs catch-up on the same reminder: the insert already
            // generated the only in-window dose, so the racing catch-up can
            // only no-op - it reads the reminder disabled (disable committed
            // first) or its floor has moved to the existing dose. Either
            // commit order must leave the disable-won final state
            Date disableNow = new Date();
            runRace(
                    () -> {
                        MedicineReminder disabled = reminder.clone();
                        disabled.reminderEnabled = false;
                        mMedicineRepository.updateMedicineReminder(disabled);
                    },
                    () -> mMedicineRepository.catchUpAllEnabled(new Date()),
                    15000);

            MedicineReminder stored =
                    mMedicineReminderDao.findMedicineReminderById(reminder.id);
            assertFalse(stored.reminderEnabled);
            List<MedicineDoseOccurrence> occurrences =
                    findOccurrencesByReminderId(reminder.id);
            // zero occurrences strictly after the disable's captured time
            // remain, and the real UNIQUE index means no duplicate pairs
            for (MedicineDoseOccurrence occurrence : occurrences) {
                assertFalse(occurrence.scheduledDateTime.after(disableNow));
            }
            assertNoDuplicateScheduled(occurrences);
        }
    }

    @Test
    public void concurrency_catchUpVersusCatchUp_convergesToFullExpectedSet()
            throws InterruptedException {
        Medicine medicine = insertMedicine("race catch-up host", 0);
        for (int iteration = 0; iteration < 10; iteration++) {
            // planted (no insert-path generation) five days back so a
            // catch-up has real work: created is five days ago at midnight,
            // the time-of-day is 23:59, so the catch-up window yields six
            // doses (five days ago through today, each after the created
            // floor)
            MedicineReminder reminder = plantReminder(medicine.id,
                    dayAt(-5, 23, 59), dayAt(-5, 0, 0), true,
                    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
                    Calendar.SUNDAY);

            runRace(
                    () -> mMedicineRepository.catchUpAllEnabled(new Date()),
                    () -> mMedicineRepository.catchUpAllEnabled(new Date()),
                    15000);

            // the two transactions serialize: the second either no-ops on
            // the moved floor or re-proposes rows the real UNIQUE + IGNORE
            // index dedupes - either way the final set is the complete
            // expected schedule with no duplicates
            List<Date> expected = new ArrayList<>();
            for (int dayOffset = -5; dayOffset <= 0; dayOffset++) {
                expected.add(dayAt(dayOffset, 23, 59));
            }
            List<MedicineDoseOccurrence> occurrences =
                    findOccurrencesByReminderId(reminder.id);
            assertEquals(expected, scheduledDates(occurrences));
            assertNoDuplicateScheduled(occurrences);
        }
    }

    private static int countAt(List<MedicineDoseOccurrence> occurrences, Date scheduledDateTime) {
        int count = 0;
        for (MedicineDoseOccurrence occurrence : occurrences) {
            if (occurrence.scheduledDateTime.equals(scheduledDateTime)) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return the date shifted by minutes with seconds and millis truncated,
     * so the generated occurrence (second-truncated time-of-day) lands exactly
     * on the start time instead of a millisecond before it
     */
    private static Date plusMinutes(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, minutes);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static long endOfToday(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }
}
