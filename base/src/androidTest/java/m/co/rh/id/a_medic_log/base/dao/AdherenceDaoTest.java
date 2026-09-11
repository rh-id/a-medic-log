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

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
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
}
