package m.co.rh.id.a_medic_log.base.repository;

import static org.junit.Assert.assertEquals;
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
import java.util.List;

import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class MedicineRepositoryTest {

    private Provider mProvider;
    private MedicineRepository mMedicineRepository;
    private NoteDao mNoteDao;
    private MedicineDao mMedicineDao;
    private MedicineReminderDao mMedicineReminderDao;
    private MedicineIntakeDao mMedicineIntakeDao;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new RepositoryTestProviderModule());
        mMedicineRepository = mProvider.get(MedicineRepository.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mMedicineReminderDao = mProvider.get(MedicineReminderDao.class);
        mMedicineIntakeDao = mProvider.get(MedicineIntakeDao.class);
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
}
