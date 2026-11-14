package m.co.rh.id.a_medic_log.base.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

@RunWith(AndroidJUnit4.class)
public class MedicineDaoTest {
    private static final String TEST_DB = MedicineDaoTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    private AppDatabase mAppDatabase;

    @SuppressWarnings("deprecation")
    public MedicineDaoTest() {
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

    private Note insertNote(String content) {
        Note note = new Note();
        note.content = content;
        note.id = mAppDatabase.noteDao().insert(note);
        return note;
    }

    private Medicine insertMedicine(long noteId, String medicineName) {
        Medicine medicine = new Medicine();
        medicine.noteId = noteId;
        medicine.name = medicineName;
        medicine.id = mAppDatabase.medicineDao().insert(medicine);
        return medicine;
    }

    @Test
    public void findMedicinesByIds_returnsAllRowsBeyondChunkSize() {
        // insert 1 note first (medicine.note_id is an FK CASCADE to note),
        // then 1001 medicines sharing that note
        Note note = insertNote("batch query test note");
        MedicineDao medicineDao = mAppDatabase.medicineDao();

        List<Long> insertedMedicineIds = new ArrayList<>();
        Medicine[] excludedMedicine = new Medicine[1];
        mAppDatabase.runInTransaction(() -> {
            for (int i = 0; i < 1001; i++) {
                Medicine medicine = insertMedicine(note.id, "batch medicine " + i);
                insertedMedicineIds.add(medicine.id);
            }
            // insert one extra medicine under the same note but don't add it to the query list
            excludedMedicine[0] = insertMedicine(note.id, "excluded medicine");
        });

        // query all medicines by their ids (this should chunk at 500, then 500, then 1)
        List<Medicine> result = medicineDao.findMedicinesByIds(insertedMedicineIds);

        // assert all 1001 medicines are returned
        assertEquals(1001, result.size());

        // assert the set of returned ids equals the set of inserted ids
        Set<Long> returnedIds = new HashSet<>();
        for (Medicine medicine : result) {
            returnedIds.add(medicine.id);
        }
        Set<Long> expectedIds = new HashSet<>(insertedMedicineIds);
        assertEquals(expectedIds, returnedIds);

        // assert the excluded medicine is not in the results
        assertTrue(!returnedIds.contains(excludedMedicine[0].id));
    }

    @Test
    public void findMedicinesByIds_deduplicatesInputAcrossChunks() {
        // insert 1 note and 1001 medicines under it
        Note note = insertNote("batch dedup test note");
        MedicineDao medicineDao = mAppDatabase.medicineDao();

        List<Long> insertedMedicineIds = new ArrayList<>();
        mAppDatabase.runInTransaction(() -> {
            for (int i = 0; i < 1001; i++) {
                Medicine medicine = insertMedicine(note.id, "batch medicine " + i);
                insertedMedicineIds.add(medicine.id);
            }
        });

        // build input list with duplicates spanning chunk boundaries
        List<Long> inputIds = new ArrayList<>(insertedMedicineIds);
        // append the first 600 ids again — without global deduplication these
        // duplicates would span chunk boundaries and return duplicate rows
        for (int i = 0; i < 600; i++) {
            inputIds.add(insertedMedicineIds.get(i));
        }

        // query with duplicate ids
        List<Medicine> result = medicineDao.findMedicinesByIds(inputIds);

        // assert deduplication happened: still only 1001 unique medicines returned
        assertEquals(1001, result.size());

        // assert all returned ids are unique
        Set<Long> returnedIds = new HashSet<>();
        for (Medicine medicine : result) {
            returnedIds.add(medicine.id);
        }
        assertEquals(1001, returnedIds.size());

        // assert the returned ids match the original inserted ids
        Set<Long> expectedIds = new HashSet<>(insertedMedicineIds);
        assertEquals(expectedIds, returnedIds);
    }

    @Test
    public void findMedicinesByIds_nullAndEmptyInput() {
        MedicineDao medicineDao = mAppDatabase.medicineDao();

        // null input returns empty non-null list
        List<Medicine> nullResult = medicineDao.findMedicinesByIds(null);
        assertNotNull(nullResult);
        assertEquals(0, nullResult.size());

        // empty input returns empty non-null list
        List<Medicine> emptyResult = medicineDao.findMedicinesByIds(new ArrayList<>());
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.size());
    }

    @Test
    public void findMedicinesByIds_exactlyAtChunkSizeReturnsAll() {
        // insert 1 note and exactly 500 medicines under it (chunk size boundary)
        Note note = insertNote("batch boundary test note");
        MedicineDao medicineDao = mAppDatabase.medicineDao();

        List<Long> insertedMedicineIds = new ArrayList<>();
        mAppDatabase.runInTransaction(() -> {
            for (int i = 0; i < 500; i++) {
                Medicine medicine = insertMedicine(note.id, "boundary medicine " + i);
                insertedMedicineIds.add(medicine.id);
            }
        });

        // query all 500 medicines by their ids (exactly at the chunk-size boundary)
        List<Medicine> result = medicineDao.findMedicinesByIds(insertedMedicineIds);

        // assert all 500 medicines are returned
        assertEquals(500, result.size());

        // assert the set of returned ids equals the set of inserted ids
        Set<Long> returnedIds = new HashSet<>();
        for (Medicine medicine : result) {
            returnedIds.add(medicine.id);
        }
        Set<Long> expectedIds = new HashSet<>(insertedMedicineIds);
        assertEquals(expectedIds, returnedIds);
    }
}
