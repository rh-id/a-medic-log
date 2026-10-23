package m.co.rh.id.a_medic_log.base.dao;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;

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
import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;

@RunWith(AndroidJUnit4.class)
public class NoteDaoTest {
    private static final String TEST_DB = NoteDaoTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    private AppDatabase mAppDatabase;

    @SuppressWarnings("deprecation")
    public NoteDaoTest() {
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

    @Test
    public void insertUpdateDelete_noteState() {
        NoteDao noteDao = mAppDatabase.noteDao();
        MedicineDao medicineDao = mAppDatabase.medicineDao();
        NoteTagDao noteTagDao = mAppDatabase.noteTagDao();
        MedicineReminderDao medicineReminderDao = mAppDatabase.medicineReminderDao();
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();

        String noteContent = "test note";
        String noteTagTag = "sample tag";
        String medicineName = "test medicine name";
        String medicineIntakeDesc = "test medicine intake";
        String medicineReminderMessage = "test medicine reminder message";
        NoteState noteState = new NoteState();
        Note note = new Note();
        note.content = noteContent;
        noteState.updateNote(note);
        NoteTag noteTag = new NoteTag();
        noteTag.tag = noteTagTag;
        noteState.updateNoteTagSet(Collections.singletonList(noteTag));
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
        mAppDatabase.runInTransaction(() -> {
            Note n = noteState.getNote();
            long noteId = noteDao.insert(n);
            n.id = noteId;
            for (NoteTag nt : noteState.getNoteTagSet()) {
                nt.noteId = noteId;
                nt.id = noteTagDao.insert(nt);
            }
            for (MedicineState ms : noteState.getMedicineList()) {
                ms.setNoteId(noteId);
                Medicine med = ms.getMedicine();
                long medId = medicineDao.insert(med);
                med.id = medId;
                for (MedicineReminder mr : ms.getMedicineReminderList()) {
                    mr.medicineId = medId;
                    mr.id = medicineReminderDao.insert(mr);
                }
            }
        });

        Note noteFromInsert = noteDao.findNoteById(noteState.getNoteId());
        assertNotNull(noteFromInsert);
        assertEquals(noteContent, noteFromInsert.content);
        List<NoteTag> noteTagListFromInsert = noteTagDao.findNoteTagsByNoteId(noteState.getNoteId());
        assertEquals(1, noteTagListFromInsert.size());
        assertEquals(noteTagTag, noteTagListFromInsert.get(0).tag);
        List<Medicine> medicineListFromInsert = medicineDao.findMedicinesByNoteId(noteState.getNoteId());
        assertEquals(1, medicineListFromInsert.size());
        assertEquals(medicineName, medicineListFromInsert.get(0).name);
        assertEquals(1, medicineReminderDao.findMedicineRemindersByMedicineId(medicineListFromInsert.get(0).id).size());
        assertEquals(0, medicineIntakeDao.findMedicineIntakesByMedicineIdWithLimit(medicineListFromInsert.get(0).id, 10).size());
        List<MedicineReminder> medicineReminderListFromInsert = medicineReminderDao.findMedicineRemindersByMedicineId(
                medicineListFromInsert.get(0).id
        );
        assertEquals(1, medicineReminderListFromInsert.size());
        assertEquals(medicineReminderMessage, medicineReminderListFromInsert.get(0).message);

        // try insert medicine intake
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineListFromInsert.get(0).id;
        medicineIntake.description = medicineIntakeDesc;
        long medicineIntakeId = medicineIntakeDao.insert(medicineIntake);
        medicineIntake.id = medicineIntakeId;

        // after that update note
        String noteContentUpdate = noteContent + " updated ";
        noteState.getNote().content = noteContentUpdate;
        // UPDATE NOTE
        mAppDatabase.runInTransaction(() -> {
            noteDao.update(noteState.getNote());
        });

        assertNotNull(noteDao.findNoteById(noteState.getNoteId()));
        assertEquals(1, noteTagDao.findNoteTagsByNoteId(noteState.getNoteId()).size());
        assertEquals(1, medicineDao.findMedicinesByNoteId(noteState.getNoteId()).size());
        assertEquals(1, medicineReminderDao.findMedicineRemindersByMedicineId(medicineListFromInsert.get(0).id).size());
        assertNotNull(medicineIntakeDao.findLastMedicineIntake(medicineListFromInsert.get(0).id));
        Note noteFromUpdate = noteDao.findNoteById(noteState.getNoteId());
        assertEquals(noteContentUpdate, noteFromUpdate.content);
        List<NoteTag> noteTagListFromUpdate = noteTagDao.findNoteTagsByNoteId(noteState.getNoteId());
        assertEquals(1, noteTagListFromUpdate.size());
        assertEquals(noteTagTag, noteTagListFromUpdate.get(0).tag);
        List<Medicine> medicineListFromUpdate = medicineDao.findMedicinesByNoteId(noteState.getNoteId());
        assertEquals(1, medicineListFromUpdate.size());
        assertEquals(medicineName, medicineListFromUpdate.get(0).name);
        List<MedicineReminder> medicineReminderListFromUpdate = medicineReminderDao.findMedicineRemindersByMedicineId(
                medicineListFromUpdate.get(0).id
        );
        assertEquals(1, medicineReminderListFromUpdate.size());
        assertEquals(medicineReminderMessage, medicineReminderListFromUpdate.get(0).message);

        // ensure medicine intake not deleted when updating note state
        MedicineIntake updateNoteMedicineIntake = medicineIntakeDao.findLastMedicineIntake(medicineIntake.medicineId);
        assertNotNull(updateNoteMedicineIntake);
        assertEquals(medicineIntakeDesc, updateNoteMedicineIntake.description);

        // DELETE NOTE (FK CASCADE handles related records)
        mAppDatabase.runInTransaction(() -> {
            noteDao.delete(noteState.getNote());
        });

        assertNull(noteDao.findNoteById(noteState.getNoteId()));
        assertEquals(0, noteTagDao.findNoteTagsByNoteId(noteState.getNoteId()).size());
        assertEquals(0, medicineDao.findMedicinesByNoteId(noteState.getNoteId()).size());
        assertEquals(0, medicineReminderDao.findMedicineRemindersByMedicineId(medicineListFromInsert.get(0).id).size());
        assertNull(medicineIntakeDao.findLastMedicineIntake(medicineListFromInsert.get(0).id));
    }

    private static MedicineIntake createMedicineIntake(Long medicineId) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        return medicineIntake;
    }

    @Test
    public void countMedicineIntakesByNoteId() {
        NoteDao noteDao = mAppDatabase.noteDao();
        MedicineDao medicineDao = mAppDatabase.medicineDao();
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();

        Note noteA = new Note();
        noteA.content = "note A";
        Note noteB = new Note();
        noteB.content = "note B";

        mAppDatabase.runInTransaction(() -> {
            noteA.id = noteDao.insert(noteA);
            noteB.id = noteDao.insert(noteB);
        });

        // no medicine yet, zero intakes
        assertEquals(0, medicineIntakeDao.countMedicineIntakesByNoteId(noteA.id));

        Medicine medicineA1 = new Medicine();
        medicineA1.name = "medicine A1";
        medicineA1.noteId = noteA.id;
        Medicine medicineA2 = new Medicine();
        medicineA2.name = "medicine A2";
        medicineA2.noteId = noteA.id;
        Medicine medicineB1 = new Medicine();
        medicineB1.name = "medicine B1";
        medicineB1.noteId = noteB.id;

        mAppDatabase.runInTransaction(() -> {
            medicineA1.id = medicineDao.insert(medicineA1);
            medicineA2.id = medicineDao.insert(medicineA2);
            medicineB1.id = medicineDao.insert(medicineB1);
        });

        // note A: 2 intakes for medicine A1 + 1 intake for medicine A2,
        // note B: 5 intakes for medicine B1
        mAppDatabase.runInTransaction(() -> {
            medicineIntakeDao.insert(createMedicineIntake(medicineA1.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineA1.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineA2.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineB1.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineB1.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineB1.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineB1.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineB1.id));
        });

        // aggregates the intakes of both medicines of the note
        assertEquals(3, medicineIntakeDao.countMedicineIntakesByNoteId(noteA.id));
        // intakes of another note's medicine are not counted
        assertEquals(5, medicineIntakeDao.countMedicineIntakesByNoteId(noteB.id));
    }

    @Test
    public void countProfileLevel_aggregatesAcrossNotes() {
        ProfileDao profileDao = mAppDatabase.profileDao();
        NoteDao noteDao = mAppDatabase.noteDao();
        NoteAttachmentDao noteAttachmentDao = mAppDatabase.noteAttachmentDao();
        MedicineDao medicineDao = mAppDatabase.medicineDao();
        MedicineReminderDao medicineReminderDao = mAppDatabase.medicineReminderDao();
        MedicineIntakeDao medicineIntakeDao = mAppDatabase.medicineIntakeDao();

        Profile profileA = new Profile();
        profileA.name = "profile A";
        Profile profileB = new Profile();
        profileB.name = "profile B";

        Note noteA1 = new Note();
        noteA1.content = "note A1";
        Note noteA2 = new Note();
        noteA2.content = "note A2";
        Note noteB1 = new Note();
        noteB1.content = "note B1";

        Medicine medicineA1 = new Medicine();
        medicineA1.name = "medicine A1";
        Medicine medicineA2 = new Medicine();
        medicineA2.name = "medicine A2";
        Medicine medicineB1 = new Medicine();
        medicineB1.name = "medicine B1";

        MedicineReminder reminderA1 = new MedicineReminder();
        reminderA1.message = "reminder A1";
        MedicineReminder reminderB1 = new MedicineReminder();
        reminderB1.message = "reminder B1";

        NoteAttachment attachmentA1 = new NoteAttachment();
        attachmentA1.name = "attachment A1";
        NoteAttachment attachmentA2 = new NoteAttachment();
        attachmentA2.name = "attachment A2";

        mAppDatabase.runInTransaction(() -> {
            profileA.id = profileDao.insert(profileA);
            profileB.id = profileDao.insert(profileB);
            noteA1.profileId = profileA.id;
            noteA2.profileId = profileA.id;
            noteB1.profileId = profileB.id;
            noteA1.id = noteDao.insert(noteA1);
            noteA2.id = noteDao.insert(noteA2);
            noteB1.id = noteDao.insert(noteB1);
            medicineA1.noteId = noteA1.id;
            medicineA2.noteId = noteA2.id;
            medicineB1.noteId = noteB1.id;
            medicineA1.id = medicineDao.insert(medicineA1);
            medicineA2.id = medicineDao.insert(medicineA2);
            medicineB1.id = medicineDao.insert(medicineB1);
            reminderA1.medicineId = medicineA1.id;
            reminderB1.medicineId = medicineB1.id;
            medicineReminderDao.insert(reminderA1);
            medicineReminderDao.insert(reminderB1);
            attachmentA1.noteId = noteA1.id;
            attachmentA2.noteId = noteA2.id;
            noteAttachmentDao.insert(attachmentA1);
            noteAttachmentDao.insert(attachmentA2);
            // profile A: 1 intake under note A1's medicine + 2 intakes under note A2's medicine,
            // profile B: 1 intake under note B1's medicine
            medicineIntakeDao.insert(createMedicineIntake(medicineA1.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineA2.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineA2.id));
            medicineIntakeDao.insert(createMedicineIntake(medicineB1.id));
        });

        // profile A aggregates across its 2 notes
        assertEquals(2, noteDao.countNotesByProfileId(profileA.id));
        assertEquals(2, medicineDao.countMedicinesByProfileId(profileA.id));
        assertEquals(1, medicineReminderDao.countMedicineRemindersByProfileId(profileA.id));
        assertEquals(2, noteAttachmentDao.countNoteAttachmentsByProfileId(profileA.id));
        assertEquals(3, medicineIntakeDao.countMedicineIntakesByProfileId(profileA.id));

        // profile B owns a single note, its records are not counted for profile A
        assertEquals(1, noteDao.countNotesByProfileId(profileB.id));
        assertEquals(1, medicineDao.countMedicinesByProfileId(profileB.id));
        assertEquals(1, medicineReminderDao.countMedicineRemindersByProfileId(profileB.id));
        assertEquals(0, noteAttachmentDao.countNoteAttachmentsByProfileId(profileB.id));
        assertEquals(1, medicineIntakeDao.countMedicineIntakesByProfileId(profileB.id));
    }
}
