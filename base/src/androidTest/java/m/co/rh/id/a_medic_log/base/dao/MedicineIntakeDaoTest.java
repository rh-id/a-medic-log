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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

/**
 * Instrumented tests of the medicine-intake search queries: the unbounded
 * description search, the global LIMIT cap with created_date_time DESC, id DESC
 * ordering and the medicine_id scoping variant.
 */
@RunWith(AndroidJUnit4.class)
public class MedicineIntakeDaoTest {
    private static final String TEST_DB = MedicineIntakeDaoTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    private AppDatabase mAppDatabase;

    @SuppressWarnings("deprecation")
    public MedicineIntakeDaoTest() {
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

    private Medicine createMedicine(String medicineName, String medicineDescription) {
        NoteDao noteDao = mAppDatabase.noteDao();
        MedicineDao medicineDao = mAppDatabase.medicineDao();
        Note note = new Note();
        note.content = "intake search note for " + medicineName;
        note.id = noteDao.insert(note);
        Medicine medicine = new Medicine();
        medicine.noteId = note.id;
        medicine.name = medicineName;
        medicine.description = medicineDescription;
        medicine.id = medicineDao.insert(medicine);
        return medicine;
    }

    private static MedicineIntake createMedicineIntake(Long medicineId, String description) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.description = description;
        return medicineIntake;
    }

    @Test
    public void searchMedicineIntakeDescriptionWithLimit_capsRowsAndReturnsNewestFirst() {
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();
        // arrange: five intakes under five medicines, every description
        // matches the search term
        long newestIntakeId = 0;
        for (int i = 0; i < 5; i++) {
            Medicine medicine = createMedicine("limit keyword medicine " + i, "desc");
            newestIntakeId = medicineIntakeDao.insert(
                    createMedicineIntake(medicine.id, "limit keyword intake " + i));
        }

        // assert: the capped intake search respects LIMIT and returns the
        // newest intakes first (created_date_time DESC, id DESC)
        List<MedicineIntake> intakes = medicineIntakeDao
                .searchMedicineIntakeDescriptionWithLimit("limit", 2);
        assertEquals(2, intakes.size());
        assertEquals(newestIntakeId, intakes.get(0).id);
    }

    @Test
    public void searchMedicineIntakeDescriptionWithMedicineLimit_returnsOnlyThatMedicinesIntakes() {
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();
        // arrange: two medicines, each with an intake that matches the
        // search term
        Medicine medicine1 = createMedicine("Med One", "desc one");
        Medicine medicine2 = createMedicine("Med Two", "desc two");
        MedicineIntake intake1 = createMedicineIntake(medicine1.id, "breakfast dose");
        intake1.id = medicineIntakeDao.insert(intake1);
        medicineIntakeDao.insert(createMedicineIntake(medicine2.id, "breakfast snack"));

        // assert: the unscoped intake search sees both medicines' intakes
        List<MedicineIntake> unscoped = medicineIntakeDao
                .searchMedicineIntakeDescriptionWithLimit("breakfast", 100);
        assertEquals(2, unscoped.size());

        // assert: the medicine-scoped search returns only that medicine's
        // intakes
        List<MedicineIntake> scopedMedicine1 = medicineIntakeDao
                .searchMedicineIntakeDescriptionWithMedicineLimit(medicine1.id, "breakfast", 100);
        assertEquals(1, scopedMedicine1.size());
        assertEquals(intake1.id, scopedMedicine1.get(0).id);
        assertEquals(medicine1.id, scopedMedicine1.get(0).medicineId);
        List<MedicineIntake> scopedMedicine2 = medicineIntakeDao
                .searchMedicineIntakeDescriptionWithMedicineLimit(medicine2.id, "breakfast", 100);
        assertEquals(1, scopedMedicine2.size());
        assertEquals(medicine2.id, scopedMedicine2.get(0).medicineId);
    }

    @Test
    public void searchMedicineIntakeDescription_matchesAndOrdersNewestFirst_noMatchEmpty() {
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();
        // arrange: one medicine with three intakes matching the search term
        // and one that does not
        Medicine medicine = createMedicine("unbounded keyword medicine", "desc");
        List<Long> matchingIntakeIds = new ArrayList<>();
        long baseDateTime = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            MedicineIntake intake = createMedicineIntake(medicine.id,
                    "unbounded keyword intake " + i);
            // distinct timestamp per seed, strictly newer the later it is
            // inserted, so the created_date_time DESC order is deterministic
            // even when inserts land in the same millisecond
            intake.createdDateTime = new Date(baseDateTime - (3 - i) * 1000L);
            matchingIntakeIds.add(medicineIntakeDao.insert(intake));
        }
        medicineIntakeDao.insert(createMedicineIntake(medicine.id, "unrelated intake"));

        // assert: the unbounded intake search returns only the matching
        // intakes, newest first (created_date_time DESC); the seeds carry
        // distinct timestamps so the order is deterministic
        List<MedicineIntake> matches = medicineIntakeDao
                .searchMedicineIntakeDescription("unbounded keyword");
        assertEquals(3, matches.size());
        for (MedicineIntake match : matches) {
            assertTrue(matchingIntakeIds.contains(match.id));
        }
        assertEquals(matchingIntakeIds.get(matchingIntakeIds.size() - 1).longValue(),
                matches.get(0).id);

        // assert: a term that matches nothing returns an empty list
        List<MedicineIntake> noMatches = medicineIntakeDao
                .searchMedicineIntakeDescription("missing term");
        assertTrue(noMatches.isEmpty());
    }
}
