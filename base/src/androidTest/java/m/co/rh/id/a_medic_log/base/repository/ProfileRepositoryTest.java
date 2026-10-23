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

import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.a_medic_log.base.state.ProfileState;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class ProfileRepositoryTest {

    private Provider mProvider;
    private ProfileRepository mProfileRepository;
    private NoteRepository mNoteRepository;
    private NoteAttachmentRepository mNoteAttachmentRepository;
    private MedicineIntakeDao mMedicineIntakeDao;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new RepositoryTestProviderModule());
        mProfileRepository = mProvider.get(ProfileRepository.class);
        mNoteRepository = mProvider.get(NoteRepository.class);
        mNoteAttachmentRepository = mProvider.get(NoteAttachmentRepository.class);
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

    /**
     * Inserts a profile and returns its name-matched persisted entity, note
     * is inserted through the note repository with the profileId set on its
     * entity
     */
    private Profile insertProfile(String name) {
        Profile profile = new Profile();
        profile.name = name;
        mProfileRepository.insertProfile(profile);
        return profile;
    }

    private NoteState insertProfileNote(long profileId, String content,
                                        List<MedicineState> medicineStates) {
        NoteState noteState = new NoteState();
        Note note = new Note();
        note.content = content;
        note.profileId = profileId;
        noteState.updateNote(note);
        if (medicineStates != null && !medicineStates.isEmpty()) {
            noteState.updateMedicineStates(medicineStates);
        }
        mNoteRepository.insertNote(noteState);
        return noteState;
    }

    @Test
    public void insertProfile_returnsStateWrappingPersistedProfile() {
        Profile profile = new Profile();
        profile.name = "profile name";

        ProfileState insertedState = mProfileRepository.insertProfile(profile);

        // id is back-filled on the passed payload and wrapped in a state without notes
        assertNotNull(profile.id);
        assertEquals(profile.id, insertedState.getProfile().id);
        assertEquals("profile name", insertedState.getProfile().name);
        assertTrue(insertedState.getNoteList().isEmpty());
    }

    @Test
    public void findProfileById_returnsRawRow() {
        Profile profile = insertProfile("profile name");
        insertProfile("other profile name");

        Profile findProfile = mProfileRepository.findProfileById(profile.id);
        assertNotNull(findProfile);
        assertEquals("profile name", findProfile.name);
        assertNull(mProfileRepository.findProfileById(123456L));
    }

    @Test
    public void queryProfileInfo_assemblesFullNoteSnapshot() {
        Profile profile = insertProfile("profile name");
        // rich note: medicine with a reminder, inserted through insertNote
        ArrayList<MedicineState> medicineStates = new ArrayList<>();
        medicineStates.add(createMedicineState("rich medicine", 1));
        insertProfileNote(profile.id, "rich note", medicineStates);
        // plain note without nested data
        insertProfileNote(profile.id, "plain note", null);

        ProfileState snapshot = mProfileRepository.queryProfileInfo(profile.id);
        assertEquals("profile name", snapshot.getProfile().name);
        assertEquals(2, snapshot.getNoteList().size());
        NoteState richNoteState = null;
        NoteState plainNoteState = null;
        for (NoteState noteState : snapshot.getNoteList()) {
            if ("rich note".equals(noteState.getNote().content)) {
                richNoteState = noteState;
            } else if ("plain note".equals(noteState.getNote().content)) {
                plainNoteState = noteState;
            }
        }
        assertNotNull(richNoteState);
        assertNotNull(plainNoteState);
        // the nested state contains the medicine and its reminder
        assertEquals(1, richNoteState.getMedicineList().size());
        MedicineState richMedicineState = richNoteState.getMedicineList().get(0);
        assertEquals("rich medicine", richMedicineState.getMedicineName());
        assertEquals(1, richMedicineState.getMedicineReminderList().size());
        assertEquals(0, plainNoteState.getMedicineList().size());
        // the assembled state is fresh, ids are set on all levels
        assertNotNull(richNoteState.getNote().id);
        assertNotNull(richMedicineState.getMedicineReminderList().get(0).id);
    }

    @Test
    public void queryProfileCounts_aggregatesOwnedRecords() {
        Profile profile = insertProfile("profile");
        // note A: 2 medicines (1 reminder each), 3 intakes, 1 attachment
        ArrayList<MedicineState> noteAMedicineStates = new ArrayList<>();
        noteAMedicineStates.add(createMedicineState("medicine A1", 1));
        noteAMedicineStates.add(createMedicineState("medicine A2", 1));
        NoteState noteAState = insertProfileNote(profile.id, "note A", noteAMedicineStates);
        // note B: plain
        insertProfileNote(profile.id, "note B", null);

        // attachment owned by note A, inserted through the attachment repository
        NoteAttachment noteAttachment = new NoteAttachment();
        noteAttachment.name = "attachment A";
        noteAttachment.noteId = noteAState.getNoteId();
        NoteAttachmentState noteAttachmentState = new NoteAttachmentState();
        noteAttachmentState.updateNoteAttachment(noteAttachment);
        mNoteAttachmentRepository.insertNoteAttachment(noteAttachmentState);

        // intakes: 2 for note A's medicine A1, 1 for medicine A2 (DAO, paged history)
        Long medicineA1Id = noteAState.getMedicineList().get(0).getMedicineId();
        Long medicineA2Id = noteAState.getMedicineList().get(1).getMedicineId();
        assertNotNull(medicineA1Id);
        assertNotNull(medicineA2Id);
        mMedicineIntakeDao.insert(createMedicineIntake(medicineA1Id));
        mMedicineIntakeDao.insert(createMedicineIntake(medicineA1Id));
        mMedicineIntakeDao.insert(createMedicineIntake(medicineA2Id));

        // profile aggregates across its 2 notes
        ProfileRepository.ProfileCounts profileCounts = mProfileRepository.queryProfileCounts(profile.id);
        assertEquals(2, profileCounts.noteCount);
        assertEquals(2, profileCounts.medicineCount);
        assertEquals(2, profileCounts.reminderCount);
        assertEquals(1, profileCounts.attachmentCount);
        assertEquals(3, profileCounts.intakeCount);

        // a second profile owning a single plain note is isolated from the first's records
        Profile otherProfile = insertProfile("other profile");
        insertProfileNote(otherProfile.id, "other note", null);
        ProfileRepository.ProfileCounts otherProfileCounts = mProfileRepository.queryProfileCounts(otherProfile.id);
        assertEquals(1, otherProfileCounts.noteCount);
        assertEquals(0, otherProfileCounts.medicineCount);
        assertEquals(0, otherProfileCounts.reminderCount);
        assertEquals(0, otherProfileCounts.attachmentCount);
        assertEquals(0, otherProfileCounts.intakeCount);
    }
}
