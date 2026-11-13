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

import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

/**
 * Instrumented tests of the reminders-by-profile query: returns every
 * reminder of the profile's medicines (enabled and disabled) with the profile
 * scoping over the medicine-note-profile join.
 */
@RunWith(AndroidJUnit4.class)
public class MedicineReminderDaoTest {
    private static final String TEST_DB = MedicineReminderDaoTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    private AppDatabase mAppDatabase;

    @SuppressWarnings("deprecation")
    public MedicineReminderDaoTest() {
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

    private Profile createProfile(String profileName) {
        ProfileDao profileDao = mAppDatabase.profileDao();
        Profile profile = new Profile();
        profile.name = profileName;
        profile.id = profileDao.insert(profile);
        return profile;
    }

    private Medicine createMedicine(Long profileId, String medicineName) {
        NoteDao noteDao = mAppDatabase.noteDao();
        MedicineDao medicineDao = mAppDatabase.medicineDao();
        Note note = new Note();
        note.profileId = profileId;
        note.content = "reminder test note for " + medicineName;
        note.id = noteDao.insert(note);
        Medicine medicine = new Medicine();
        medicine.noteId = note.id;
        medicine.name = medicineName;
        medicine.id = medicineDao.insert(medicine);
        return medicine;
    }

    private static MedicineReminder createReminder(Long medicineId, String message,
                                                   boolean reminderEnabled) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.medicineId = medicineId;
        medicineReminder.message = message;
        medicineReminder.reminderEnabled = reminderEnabled;
        medicineReminder.createdDateTime = new Date();
        return medicineReminder;
    }

    @Test
    public void findMedicineRemindersByProfileId_returnsEnabledAndDisabled_scopesToProfile() {
        MedicineReminderDao medicineReminderDao = mAppDatabase.medicineReminderDao();
        // arrange: profile A owns two medicines, one with an enabled and a
        // disabled reminder, one without any reminder; profile B owns a
        // medicine with an enabled reminder that must be excluded
        Profile profileA = createProfile("Reminder profile A");
        Profile profileB = createProfile("Reminder profile B");
        Medicine medicineA1 = createMedicine(profileA.id, "reminder med one");
        Medicine medicineA2 = createMedicine(profileA.id, "reminder med two");
        Medicine medicineB = createMedicine(profileB.id, "reminder med other");
        MedicineReminder enabledA = createReminder(medicineA1.id, "enabled reminder A", true);
        enabledA.id = medicineReminderDao.insert(enabledA);
        MedicineReminder disabledA = createReminder(medicineA1.id, "disabled reminder A", false);
        disabledA.id = medicineReminderDao.insert(disabledA);
        MedicineReminder enabledB = createReminder(medicineB.id, "enabled reminder B", true);
        enabledB.id = medicineReminderDao.insert(enabledB);

        // assert: both reminders of profile A's medicine are returned with
        // their enabled state, profile B's reminder is excluded
        List<MedicineReminder> remindersA = medicineReminderDao
                .findMedicineRemindersByProfileId(profileA.id);
        assertEquals(2, remindersA.size());
        long enabledCount = remindersA.stream()
                .filter(reminder -> reminder.reminderEnabled != null && reminder.reminderEnabled)
                .count();
        assertEquals(1, enabledCount);
        for (MedicineReminder reminder : remindersA) {
            assertTrue(enabledA.id.equals(reminder.id)
                    || disabledA.id.equals(reminder.id));
        }
        List<MedicineReminder> remindersB = medicineReminderDao
                .findMedicineRemindersByProfileId(profileB.id);
        assertEquals(1, remindersB.size());

        // assert: a profile whose medicines own no reminder gets an empty
        // result
        Profile profileC = createProfile("Reminder profile C");
        createMedicine(profileC.id, "reminder med no reminder");
        List<MedicineReminder> remindersC = medicineReminderDao
                .findMedicineRemindersByProfileId(profileC.id);
        assertTrue(remindersC.isEmpty());
    }
}
