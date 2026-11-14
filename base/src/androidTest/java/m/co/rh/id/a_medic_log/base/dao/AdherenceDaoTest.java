package m.co.rh.id.a_medic_log.base.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

@RunWith(AndroidJUnit4.class)
public class AdherenceDaoTest {
    private static final String TEST_DB = AdherenceDaoTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    private AppDatabase mAppDatabase;

    @SuppressWarnings("deprecation")
    public AdherenceDaoTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Before
    public void init() {
        mAppDatabase = createAppDb();
    }

    @After
    public void cleanup() {
        deleteAppDb();
    }

    private AppDatabase createAppDb() {
        return Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class,
                TEST_DB)
                .addMigrations(DbMigration.getAll())
                .addTypeConverter(new LinkedHashSetConverter(null))
                .build();
    }

    private void deleteAppDb() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(TEST_DB);
    }

    private static Date date(int year, int month, int dayOfMonth, int hourOfDay, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, dayOfMonth, hourOfDay, minute, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private Medicine createMedicine(String medicineName) {
        NoteDao noteDao = mAppDatabase.noteDao();
        MedicineDao medicineDao = mAppDatabase.medicineDao();
        Note note = new Note();
        note.content = "adherence test note";
        long noteId = noteDao.insert(note);
        Medicine medicine = new Medicine();
        medicine.noteId = noteId;
        medicine.name = medicineName;
        long medicineId = medicineDao.insert(medicine);
        medicine.id = medicineId;
        return medicine;
    }

    private Profile createProfile(String profileName) {
        ProfileDao profileDao = mAppDatabase.profileDao();
        Profile profile = new Profile();
        profile.name = profileName;
        profile.id = profileDao.insert(profile);
        return profile;
    }

    private Medicine createProfiledMedicine(Long profileId, String medicineName) {
        NoteDao noteDao = mAppDatabase.noteDao();
        MedicineDao medicineDao = mAppDatabase.medicineDao();
        Note note = new Note();
        note.profileId = profileId;
        note.content = "adherence profile note for " + medicineName;
        note.id = noteDao.insert(note);
        Medicine medicine = new Medicine();
        medicine.noteId = note.id;
        medicine.name = medicineName;
        medicine.id = medicineDao.insert(medicine);
        return medicine;
    }

    private static MedicineDoseOccurrence createMedicineDoseOccurrence(long medicineId,
                                                                       Date scheduledDateTime) {
        MedicineDoseOccurrence occurrence = new MedicineDoseOccurrence();
        occurrence.medicineId = medicineId;
        occurrence.scheduledDateTime = scheduledDateTime;
        return occurrence;
    }

    private static MedicineIntake createMedicineIntake(long medicineId, String description,
                                                       Date takenDateTime) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.description = description;
        medicineIntake.takenDateTime = takenDateTime;
        return medicineIntake;
    }

    @Test
    public void findMedicineIntakesByTakenDateTimeBetween_inRangeReturnedOutOfRangeExcluded() {
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();
        Medicine medicine = createMedicine("intake range medicine");

        MedicineIntake inRange = new MedicineIntake();
        inRange.medicineId = medicine.id;
        inRange.description = "in range";
        inRange.takenDateTime = date(2026, Calendar.SEPTEMBER, 8, 12, 0, 0);
        medicineIntakeDao.insert(inRange);

        MedicineIntake beforeRange = new MedicineIntake();
        beforeRange.medicineId = medicine.id;
        beforeRange.description = "before range";
        beforeRange.takenDateTime = date(2026, Calendar.SEPTEMBER, 6, 12, 0, 0);
        medicineIntakeDao.insert(beforeRange);

        MedicineIntake afterRange = new MedicineIntake();
        afterRange.medicineId = medicine.id;
        afterRange.description = "after range";
        afterRange.takenDateTime = date(2026, Calendar.SEPTEMBER, 10, 12, 0, 0);
        medicineIntakeDao.insert(afterRange);

        long from = date(2026, Calendar.SEPTEMBER, 7, 0, 0, 0).getTime();
        long to = date(2026, Calendar.SEPTEMBER, 9, 23, 59, 59).getTime()
                + 999; // end-of-day inclusive

        List<MedicineIntake> result = medicineIntakeDao
                .findMedicineIntakesByTakenDateTimeBetween(from, to);
        assertEquals(1, result.size());
        assertEquals("in range", result.get(0).description);
    }

    @Test
    public void findMedicineIntakesByTakenDateTimeBetween_boundaryInclusive() {
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();
        Medicine medicine = createMedicine("intake boundary medicine");

        long from = date(2026, Calendar.SEPTEMBER, 7, 0, 0, 0).getTime();
        long to = date(2026, Calendar.SEPTEMBER, 9, 23, 59, 59).getTime()
                + 999; // end-of-day inclusive

        MedicineIntake atFromBoundary = new MedicineIntake();
        atFromBoundary.medicineId = medicine.id;
        atFromBoundary.description = "at from";
        atFromBoundary.takenDateTime = new Date(from);
        medicineIntakeDao.insert(atFromBoundary);

        MedicineIntake atToBoundary = new MedicineIntake();
        atToBoundary.medicineId = medicine.id;
        atToBoundary.description = "at to";
        atToBoundary.takenDateTime = new Date(to);
        medicineIntakeDao.insert(atToBoundary);

        List<MedicineIntake> result = medicineIntakeDao
                .findMedicineIntakesByTakenDateTimeBetween(from, to);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(intake ->
                intake.takenDateTime.getTime() >= from
                        && intake.takenDateTime.getTime() <= to));
    }

    @Test
    public void findMedicineRemindersEnabled_onlyEnabledReturned() {
        MedicineReminderDao medicineReminderDao = mAppDatabase.medicineReminderDao();
        Medicine medicine = createMedicine("reminder enabled medicine");

        MedicineReminder enabledReminder = new MedicineReminder();
        enabledReminder.medicineId = medicine.id;
        enabledReminder.message = "enabled reminder";
        enabledReminder.reminderEnabled = true;
        medicineReminderDao.insert(enabledReminder);

        MedicineReminder disabledReminder = new MedicineReminder();
        disabledReminder.medicineId = medicine.id;
        disabledReminder.message = "disabled reminder";
        disabledReminder.reminderEnabled = false;
        medicineReminderDao.insert(disabledReminder);

        List<MedicineReminder> result = medicineReminderDao.findMedicineRemindersEnabled();
        assertEquals(1, result.size());
        assertEquals("enabled reminder", result.get(0).message);
        assertEquals(Boolean.TRUE, result.get(0).reminderEnabled);
    }

    @Test
    public void findMedicineDoseOccurrencesByProfileIdAndScheduledDateTimeBetween_scopesToProfileAndAppliesWindow() {
        MedicineDoseOccurrenceDao medicineDoseOccurrenceDao = mAppDatabase.medicineDoseOccurrenceDao();
        Profile profileA = createProfile("occurrence profile A");
        Profile profileB = createProfile("occurrence profile B");
        Medicine medicineA = createProfiledMedicine(profileA.id, "occurrence profile A medicine");
        Medicine medicineB = createProfiledMedicine(profileB.id, "occurrence profile B medicine");

        long from = date(2026, Calendar.SEPTEMBER, 7, 0, 0, 0).getTime();
        long to = date(2026, Calendar.SEPTEMBER, 9, 23, 59, 59).getTime()
                + 999; // end-of-day inclusive

        // arrange: profile A has two occurrences inside the window and one
        // before it, profile B has one occurrence inside the same window
        MedicineDoseOccurrence inRangeEarly = createMedicineDoseOccurrence(medicineA.id,
                date(2026, Calendar.SEPTEMBER, 8, 8, 0, 0));
        MedicineDoseOccurrence inRangeLate = createMedicineDoseOccurrence(medicineA.id,
                date(2026, Calendar.SEPTEMBER, 9, 20, 0, 0));
        MedicineDoseOccurrence beforeRange = createMedicineDoseOccurrence(medicineA.id,
                date(2026, Calendar.SEPTEMBER, 6, 8, 0, 0));
        medicineDoseOccurrenceDao.insertAll(Arrays.asList(inRangeEarly, inRangeLate, beforeRange));
        MedicineDoseOccurrence otherProfileInRange = createMedicineDoseOccurrence(medicineB.id,
                date(2026, Calendar.SEPTEMBER, 8, 12, 0, 0));
        medicineDoseOccurrenceDao.insertAll(Collections.singletonList(otherProfileInRange));

        // assert: the query returns only profile A's in-window occurrences,
        // ordered scheduled_date_time ASC (the before-window one is excluded)
        List<MedicineDoseOccurrence> profileAOccurrences = medicineDoseOccurrenceDao
                .findMedicineDoseOccurrencesByProfileIdAndScheduledDateTimeBetween(profileA.id, from, to);
        assertEquals(2, profileAOccurrences.size());
        assertEquals(inRangeEarly.scheduledDateTime, profileAOccurrences.get(0).scheduledDateTime);
        assertEquals(medicineA.id.longValue(), profileAOccurrences.get(0).medicineId);
        assertEquals(inRangeLate.scheduledDateTime, profileAOccurrences.get(1).scheduledDateTime);
        assertEquals(medicineA.id.longValue(), profileAOccurrences.get(1).medicineId);

        // assert: from the shared window, profile B sees only its own occurrence
        List<MedicineDoseOccurrence> profileBOccurrences = medicineDoseOccurrenceDao
                .findMedicineDoseOccurrencesByProfileIdAndScheduledDateTimeBetween(profileB.id, from, to);
        assertEquals(1, profileBOccurrences.size());
        assertEquals(medicineB.id.longValue(), profileBOccurrences.get(0).medicineId);
    }

    @Test
    public void findMedicineIntakesByProfileIdAndTakenDateTimeBetween_scopesToProfileAndAppliesWindow() {
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();
        Profile profileA = createProfile("intake profile A");
        Profile profileB = createProfile("intake profile B");
        Medicine medicineA = createProfiledMedicine(profileA.id, "intake profile A medicine");
        Medicine medicineB = createProfiledMedicine(profileB.id, "intake profile B medicine");

        long from = date(2026, Calendar.SEPTEMBER, 7, 0, 0, 0).getTime();
        long to = date(2026, Calendar.SEPTEMBER, 9, 23, 59, 59).getTime()
                + 999; // end-of-day inclusive

        // arrange: profile A has two intakes inside the window and one after
        // it, profile B has one intake inside the same window
        MedicineIntake inRangeLate = createMedicineIntake(medicineA.id, "in range late",
                date(2026, Calendar.SEPTEMBER, 9, 20, 0, 0));
        inRangeLate.id = medicineIntakeDao.insert(inRangeLate);
        MedicineIntake inRangeEarly = createMedicineIntake(medicineA.id, "in range early",
                date(2026, Calendar.SEPTEMBER, 8, 8, 0, 0));
        inRangeEarly.id = medicineIntakeDao.insert(inRangeEarly);
        MedicineIntake afterRange = createMedicineIntake(medicineA.id, "after range",
                date(2026, Calendar.SEPTEMBER, 10, 8, 0, 0));
        medicineIntakeDao.insert(afterRange);
        MedicineIntake otherProfileInRange = createMedicineIntake(medicineB.id,
                "other profile in range", date(2026, Calendar.SEPTEMBER, 8, 12, 0, 0));
        otherProfileInRange.id = medicineIntakeDao.insert(otherProfileInRange);

        // assert: the query returns only profile A's in-window intakes,
        // ordered taken_date_time DESC (the after-window one is excluded)
        List<MedicineIntake> profileAIntakes = medicineIntakeDao
                .findMedicineIntakesByProfileIdAndTakenDateTimeBetween(profileA.id, from, to);
        assertEquals(2, profileAIntakes.size());
        assertEquals(inRangeLate.id, profileAIntakes.get(0).id);
        assertEquals("in range late", profileAIntakes.get(0).description);
        assertEquals(inRangeEarly.id, profileAIntakes.get(1).id);
        assertEquals("in range early", profileAIntakes.get(1).description);

        // assert: from the shared window, profile B sees only its own intake
        List<MedicineIntake> profileBIntakes = medicineIntakeDao
                .findMedicineIntakesByProfileIdAndTakenDateTimeBetween(profileB.id, from, to);
        assertEquals(1, profileBIntakes.size());
        assertEquals(otherProfileInRange.id, profileBIntakes.get(0).id);
    }
}
