package m.co.rh.id.a_medic_log.base.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class NoteRepositoryTest {

    private Provider mProvider;
    private NoteRepository mNoteRepository;
    private NoteAttachmentRepository mNoteAttachmentRepository;
    private NoteDao mNoteDao;
    private NoteTagDao mNoteTagDao;
    private NoteAttachmentDao mNoteAttachmentDao;
    private MedicineDao mMedicineDao;
    private MedicineReminderDao mMedicineReminderDao;
    private MedicineIntakeDao mMedicineIntakeDao;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new RepositoryTestProviderModule());
        mNoteRepository = mProvider.get(NoteRepository.class);
        mNoteAttachmentRepository = mProvider.get(NoteAttachmentRepository.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mNoteTagDao = mProvider.get(NoteTagDao.class);
        mNoteAttachmentDao = mProvider.get(NoteAttachmentDao.class);
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

    private static MedicineState createMedicineState(String name, int reminderCount) {
        MedicineState medicineState = new MedicineState();
        Medicine medicine = new Medicine();
        medicine.name = name;
        medicineState.updateMedicine(medicine);
        List<MedicineReminder> medicineReminders = new ArrayList<>();
        for (int i = 0; i < reminderCount; i++) {
            MedicineReminder medicineReminder = new MedicineReminder();
            medicineReminder.message = name + " reminder " + i;
            medicineReminders.add(medicineReminder);
        }
        medicineState.updateMedicineReminderList(medicineReminders);
        return medicineState;
    }

    private NoteState createFullNoteState(String content) {
        NoteState noteState = new NoteState();
        Note note = new Note();
        note.content = content;
        noteState.updateNote(note);
        NoteTag noteTag = new NoteTag();
        noteTag.tag = "tag " + content;
        noteState.updateNoteTagSet(Collections.singletonList(noteTag));
        ArrayList<MedicineState> medicineStates = new ArrayList<>();
        medicineStates.add(createMedicineState("medicine 1", 1));
        medicineStates.add(createMedicineState("medicine 2", 2));
        noteState.updateMedicineStates(medicineStates);
        return noteState;
    }

    @Test
    public void insertNote_backFillsIdsAndQueryNoteInfoAssemblesFullState() {
        NoteState noteState = createFullNoteState("test note");

        mNoteRepository.insertNote(noteState);

        // ids are back-filled on the passed payload
        Long noteId = noteState.getNoteId();
        assertNotNull(noteId);
        for (MedicineState medicineState : noteState.getMedicineList()) {
            assertNotNull(medicineState.getMedicineId());
            for (MedicineReminder medicineReminder : medicineState.getMedicineReminderList()) {
                assertNotNull(medicineReminder.id);
                assertEquals(medicineState.getMedicineId(), medicineReminder.medicineId);
            }
        }

        // the assembled state is fresh and complete
        NoteState freshNoteState = mNoteRepository.queryNoteInfo(noteId);
        assertNotNull(freshNoteState.getNote());
        assertEquals("test note", freshNoteState.getNote().content);
        assertEquals(1, freshNoteState.getNoteTagSet().size());
        for (NoteTag freshTag : freshNoteState.getNoteTagSet()) {
            assertEquals("tag test note", freshTag.tag);
            assertEquals(noteId, freshTag.noteId);
        }
        assertEquals(2, freshNoteState.getMedicineList().size());
        int totalReminders = 0;
        for (MedicineState freshMedicineState : freshNoteState.getMedicineList()) {
            assertEquals(noteId, freshMedicineState.getMedicine().noteId);
            for (MedicineReminder freshReminder : freshMedicineState.getMedicineReminderList()) {
                assertNotNull(freshReminder.id);
                totalReminders++;
            }
        }
        assertEquals(3, totalReminders);
        assertEquals(0, freshNoteState.getNoteAttachmentStates().size());
    }

    @Test
    public void updateNote_persistsContentChanges() {
        NoteState noteState = createFullNoteState("content before update");
        mNoteRepository.insertNote(noteState);
        noteState.getNote().content = "content after update";

        mNoteRepository.updateNote(noteState);

        assertEquals("content after update", mNoteDao.findNoteById(noteState.getNoteId()).content);
    }

    @Test
    public void queryNoteCounts_matchesOwnedRecords() {
        NoteState noteState = createFullNoteState("note with records");
        mNoteRepository.insertNote(noteState);
        long noteId = noteState.getNoteId();

        // attachment owned by the note, inserted through the attachment repository
        NoteAttachment noteAttachment = new NoteAttachment();
        noteAttachment.name = "attachment 1";
        noteAttachment.noteId = noteId;
        NoteAttachmentState noteAttachmentState = new NoteAttachmentState();
        noteAttachmentState.updateNoteAttachment(noteAttachment);
        NoteAttachmentFile file = new NoteAttachmentFile();
        file.fileName = "file.jpg";
        noteAttachmentState.updateNoteAttachmentFileList(Collections.singletonList(file));
        mNoteAttachmentRepository.insertNoteAttachment(noteAttachmentState);

        // intakes are paged history, not part of any state
        MedicineState medicineState1 = noteState.getMedicineList().get(0);
        MedicineState medicineState2 = noteState.getMedicineList().get(1);
        assertNotNull(medicineState1.getMedicineId());
        assertNotNull(medicineState2.getMedicineId());
        mMedicineIntakeDao.insert(createMedicineIntake(medicineState1.getMedicineId()));
        mMedicineIntakeDao.insert(createMedicineIntake(medicineState1.getMedicineId()));
        mMedicineIntakeDao.insert(createMedicineIntake(medicineState2.getMedicineId()));

        // 2 medicines, 1 + 2 reminders, 1 attachment, 3 intakes
        NoteRepository.NoteCounts noteCounts = mNoteRepository.queryNoteCounts(noteId);
        assertEquals(2, noteCounts.medicineCount);
        assertEquals(3, noteCounts.reminderCount);
        assertEquals(1, noteCounts.attachmentCount);
        assertEquals(3, noteCounts.intakeCount);
    }

    @Test
    public void deleteNote_cascadesAllOwnedRows() {
        NoteState noteState = createFullNoteState("note to delete");
        mNoteRepository.insertNote(noteState);
        long noteId = noteState.getNoteId();

        mNoteRepository.deleteNote(noteState);

        // note and everything it owns is gone (FK ON DELETE CASCADE)
        assertNull(mNoteDao.findNoteById(noteId));
        assertEquals(0, mNoteTagDao.findNoteTagsByNoteId(noteId).size());
        assertEquals(0, mMedicineDao.findMedicinesByNoteId(noteId).size());
        assertEquals(0, mNoteAttachmentDao.findNoteAttachmentsByNoteId(noteId).size());
        assertEquals(0, mMedicineIntakeDao.countMedicineIntakesByNoteId(noteId));
    }
}
