package m.co.rh.id.a_medic_log.base.dao;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;

@RunWith(AndroidJUnit4.class)
public class NoteDaoTest {
    private static final String TEST_DB = NoteDaoTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    @SuppressWarnings("deprecation")
    public NoteDaoTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    private AppDatabase createAppDb() {
        return Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class,
                TEST_DB)
                .build();
    }

    private void deleteAppDb() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(TEST_DB);
    }

    @Test
    public void insertUpdateDelete_noteState() {
        AppDatabase appDb = createAppDb();
        NoteDao noteDao = appDb.noteDao();
        MedicineDao medicineDao = appDb.medicineDao();

        String noteContent = "test note";
        String medicineName = "test medicine name";
        String medicineIntakeDesc = "test medicine intake";
        String medicineReminderMessage = "test medicine reminder message";
        NoteState noteState = new NoteState();
        Note note = new Note();
        note.content = noteContent;
        noteState.updateNote(note);
        List<MedicineState> medicineStateList = new ArrayList<>();
        MedicineState medicineState = new MedicineState();
        Medicine medicine = new Medicine();
        medicine.name = medicineName;
        medicineState.updateMedicine(medicine);
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.message = medicineReminderMessage;
        medicineState.updateMedicineReminderList(Collections.singletonList(medicineReminder));
        medicineStateList.add(medicineState);
        noteState.updateMedicineStates(medicineStateList);

        // INSERT NOTE
        noteDao.insertNote(noteState);
        assertEquals(1, noteDao.countNote());
        assertEquals(1, medicineDao.countMedicine());
        assertEquals(1, medicineDao.countMedicineReminder());
        assertEquals(0, medicineDao.countMedicineIntake());
        Note noteFromInsert = noteDao.findNoteById(noteState.getNoteId());
        assertEquals(noteContent, noteFromInsert.content);
        List<Medicine> medicineListFromInsert = noteDao.findMedicineByNoteId(noteState.getNoteId());
        assertEquals(1, medicineListFromInsert.size());
        assertEquals(medicineName, medicineListFromInsert.get(0).name);
        List<MedicineReminder> medicineReminderListFromInsert = medicineDao.findMedicineRemindersByMedicineId(
                medicineListFromInsert.get(0).id
        );
        assertEquals(1, medicineReminderListFromInsert.size());
        assertEquals(medicineReminderMessage, medicineReminderListFromInsert.get(0).message);

        // try insert medicine intake
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineListFromInsert.get(0).id;
        medicineIntake.description = medicineIntakeDesc;
        medicineDao.insert(medicineIntake);

        // after that update note and add medicine
        String noteContentUpdate = noteContent + " updated ";
        String newMedicineName = "new medicine name test";
        noteState.getNote().content = noteContentUpdate;
        Medicine newMedicine = new Medicine();
        newMedicine.name = newMedicineName;
        MedicineState newMedicineState = new MedicineState();
        newMedicineState.updateMedicine(newMedicine);
        noteState.addMedicineList(newMedicineState);
        // UPDATE NOTE
        noteDao.updateNote(noteState);
        assertEquals(1, noteDao.countNote());
        assertEquals(2, medicineDao.countMedicine());
        assertEquals(1, medicineDao.countMedicineReminder());
        assertEquals(1, medicineDao.countMedicineIntake());
        Note noteFromUpdate = noteDao.findNoteById(noteState.getNoteId());
        assertEquals(noteContentUpdate, noteFromUpdate.content);
        List<Medicine> medicineListFromUpdate = noteDao.findMedicineByNoteId(noteState.getNoteId());
        assertEquals(2, medicineListFromUpdate.size());
        assertEquals(medicineName, medicineListFromUpdate.get(0).name);
        assertEquals(newMedicineName, medicineListFromUpdate.get(1).name);
        List<MedicineReminder> medicineReminderListFromUpdate = medicineDao.findMedicineRemindersByMedicineId(
                medicineListFromUpdate.get(0).id
        );
        assertEquals(1, medicineReminderListFromUpdate.size());
        assertEquals(medicineReminderMessage, medicineReminderListFromUpdate.get(0).message);

        // ensure medicine intake not deleted when updating note state
        MedicineIntake updateNoteMedicineIntake = medicineDao.findLastMedicineIntake(medicineIntake.medicineId);
        assertNotNull(updateNoteMedicineIntake);
        assertEquals(medicineIntakeDesc, updateNoteMedicineIntake.description);

        // DELETE NOTE
        noteDao.deleteNote(noteState);
        assertEquals(0, noteDao.countNote());
        assertEquals(0, medicineDao.countMedicine());
        assertEquals(0, medicineDao.countMedicineReminder());
        assertEquals(0, medicineDao.countMedicineIntake());

        deleteAppDb();
    }
}