package m.co.rh.id.a_medic_log.base.dao;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    // fixed base in the past so seeded notes have distinct, ordered entry dates
    private long mBaseTime;

    @SuppressWarnings("deprecation")
    public NoteDaoTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Before
    public void init() {
        mAppDatabase = createAppDb();
        mBaseTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
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

    // ---------------------------------------------------------------- note search queries

    @Test
    public void searchNotesWithProfileLimit_excludesOtherProfilesNotes_unscopedSeesBoth() {
        // arrange: profile B owns a note matched by every search source,
        // profile A owns one content match and one tag-only match (the tag
        // match resolves noteATag even though its content never matches)
        Profile profileA = insertProfile("Profile A", "about A");
        Profile profileB = insertProfile("Profile B", "about B");
        Note noteAContent = insertNote(profileA.id, "shared keyword content", minutesAfterBase(1));
        Note noteATag = insertNote(profileA.id, "plain A content", minutesAfterBase(2));
        insertNoteTag(noteATag.id, "shared keyword tag");
        Note noteB = insertNote(profileB.id, "shared keyword content", minutesAfterBase(3));
        insertNoteTag(noteB.id, "shared keyword tag");
        Medicine medicineB = insertMedicine(noteB.id, "shared keyword medicine", "desc B");
        insertMedicineIntake(medicineB.id, "shared keyword intake");
        NoteDao noteDao = mAppDatabase.noteDao();

        // assert: the profile-scoped query filters out profile B's note even
        // though that note matches all four sources; profile A's content and
        // tag source remain, newest first
        List<Note> scopedNotes = noteDao.searchNotesWithProfileLimit(profileA.id, "keyword", 100);
        assertEquals(2, scopedNotes.size());
        assertEquals(noteATag.id, scopedNotes.get(0).id);
        assertEquals(noteAContent.id, scopedNotes.get(1).id);
        assertEquals(profileA.id, scopedNotes.get(0).profileId);

        // assert: the same query without the profile scope sees both
        // profiles, proving the scoped variant does the filtering; profile
        // B's note matches content, tag, medicine and intake yet appears
        // exactly once
        List<Note> unscopedNotes = noteDao.searchNotesWithLimit("keyword", 100);
        assertEquals(3, unscopedNotes.size());
        assertEquals(noteB.id, unscopedNotes.get(0).id);
    }

    @Test
    public void searchNotes_tagMatch_returnsParentNote() {
        Profile profile = insertProfile("Profile", "about");
        Note note = insertNote(profile.id, "plain content", minutesAfterBase(1));
        insertNoteTag(note.id, "tagword");
        NoteDao noteDao = mAppDatabase.noteDao();

        List<Note> byTag = noteDao.searchNotesWithLimit("tagword", 100);
        assertEquals(1, byTag.size());
        assertEquals(note.id, byTag.get(0).id);
    }

    @Test
    public void searchNotes_medicineNameAndDescriptionMatch_returnsParentNote_orphanNeverSurfaces() {
        // arrange
        Profile profile = insertProfile("Profile", "about");
        Note note = insertNote(profile.id, "medicine note", minutesAfterBase(1));
        insertMedicine(note.id, "Zyrtec", "cetirizine 10mg against allergy");
        // an orphan medicine (no parent note) can never appear as a result:
        // results are notes, so this seed alone cannot detect a dropped
        // medicine.note_id = note.id correlation; cross-note leakage through
        // the EXISTS correlations is covered by
        // noteSearch_siblingRelations_doNotLeakIntoUnrelatedNotes
        insertMedicine(null, "Zyrtec", "orphan medicine row");
        NoteDao noteDao = mAppDatabase.noteDao();

        // assert: name match and description match both resolve to the parent note
        List<Note> byName = noteDao.searchNotesWithLimit("Zyrtec", 100);
        assertEquals(1, byName.size());
        assertEquals(note.id, byName.get(0).id);
        List<Note> byDescription = noteDao.searchNotesWithLimit("cetirizine", 100);
        assertEquals(1, byDescription.size());
        assertEquals(note.id, byDescription.get(0).id);
        List<Note> scoped = noteDao.searchNotesWithProfileLimit(profile.id, "allergy", 100);
        assertEquals(1, scoped.size());
        assertEquals(note.id, scoped.get(0).id);
        assertTrue(noteDao.searchNotesWithLimit("nonexistent", 100).isEmpty());
    }

    @Test
    public void searchNotes_intakeDescriptionMatch_returnsParentNote_noMatchTermEmpty() {
        // arrange: two medicines under one note, each with an intake that
        // matches the search term
        Profile profile = insertProfile("Profile", "about");
        Note note = insertNote(profile.id, "intake parent note", minutesAfterBase(1));
        Medicine medicine1 = insertMedicine(note.id, "Med One", "desc one");
        Medicine medicine2 = insertMedicine(note.id, "Med Two", "desc two");
        insertMedicineIntake(medicine1.id, "breakfast dose");
        insertMedicineIntake(medicine2.id, "breakfast snack");
        NoteDao noteDao = mAppDatabase.noteDao();

        // assert: the intake description reaches the note through the
        // medicine chain; both matching intakes collapse into one note row
        List<Note> byDescription = noteDao.searchNotesWithLimit("breakfast", 100);
        assertEquals(1, byDescription.size());
        assertEquals(note.id, byDescription.get(0).id);
        List<Note> scoped = noteDao.searchNotesWithProfileLimit(profile.id, "breakfast", 100);
        assertEquals(1, scoped.size());
        assertEquals(note.id, scoped.get(0).id);
        assertTrue(noteDao.searchNotesWithLimit("lunch", 100).isEmpty());
    }

    @Test
    public void noteSearch_siblingRelations_doNotLeakIntoUnrelatedNotes() {
        // arrange: one profile, four notes with distinct entry dates and a
        // distinct search term per source so a failure localizes to one
        // EXISTS branch. The control note matches no source at all; its own
        // non-matching medicine and intake are what make dropping
        // medicine_intake.medicine_id = medicine.id detectable, because the
        // resulting cross join would pair the control's medicine with the
        // intake sibling's matching intake.
        Profile profile = insertProfile("Profile", "about");
        Note control = insertNote(profile.id, "unrelated plain note", minutesAfterBase(1));
        Medicine controlMedicine = insertMedicine(control.id, "Calcium", "bone supplement");
        insertMedicineIntake(controlMedicine.id, "morning pill");
        // tag sibling: matches the tag source only
        Note tagSibling = insertNote(profile.id, "note with plain content", minutesAfterBase(2));
        insertNoteTag(tagSibling.id, "tagword");
        // medicine sibling: matches the medicine source only; the orphan
        // medicine below is an extra, defense-in-depth trigger for a dropped
        // medicine.note_id = note.id correlation: results are notes, so the
        // orphan can never be a result row itself and can only help expose
        // the dropped correlation — though the sibling's own "medword"
        // medicine already satisfies an uncorrelated EXISTS just the same
        Note medicineSibling = insertNote(profile.id, "another plain note", minutesAfterBase(3));
        insertMedicine(medicineSibling.id, "medword", "plain description");
        insertMedicine(null, "medword", "orphan medicine row");
        // intake sibling: matches the intake source only, through the medicine chain
        Note intakeSibling = insertNote(profile.id, "yet another plain note", minutesAfterBase(4));
        Medicine intakeMedicine = insertMedicine(intakeSibling.id, "Vitamin C", "immune support");
        insertMedicineIntake(intakeMedicine.id, "intakeword after meal");
        NoteDao noteDao = mAppDatabase.noteDao();

        // assert: each term resolves to exactly its own sibling note, so no
        // sibling's tag, medicine or intake leaks into an unrelated note
        List<Note> byTag = noteDao.searchNotesWithLimit("tagword", 100);
        assertEquals(1, byTag.size());
        assertEquals(tagSibling.id, byTag.get(0).id);
        List<Note> byMedicine = noteDao.searchNotesWithLimit("medword", 100);
        assertEquals(1, byMedicine.size());
        assertEquals(medicineSibling.id, byMedicine.get(0).id);
        List<Note> byIntake = noteDao.searchNotesWithLimit("intakeword", 100);
        assertEquals(1, byIntake.size());
        assertEquals(intakeSibling.id, byIntake.get(0).id);

        // belt and braces: the control note is absent from every result
        for (List<Note> result : Arrays.asList(byTag, byMedicine, byIntake)) {
            for (Note note : result) {
                assertTrue("control note must not match the search, got note "
                        + note.id, !note.id.equals(control.id));
            }
        }

        // assert: profile scoping composes with the EXISTS filtering
        List<Note> scoped = noteDao.searchNotesWithProfileLimit(profile.id, "intakeword", 100);
        assertEquals(1, scoped.size());
        assertEquals(intakeSibling.id, scoped.get(0).id);
    }

    @Test
    public void searchNotesWithLimit_capsRowsAndReturnsNewestFirst() {
        // arrange: five fully decorated notes (tag + medicine + intake each)
        Profile profile = insertProfile("Profile", "about");
        Note newestNote = null;
        for (int i = 0; i < 5; i++) {
            Note note = insertNote(profile.id, "limit keyword note " + i, minutesAfterBase(i));
            insertNoteTag(note.id, "limit keyword tag " + i);
            Medicine medicine = insertMedicine(note.id, "limit keyword medicine " + i, "desc");
            insertMedicineIntake(medicine.id, "limit keyword intake " + i);
            newestNote = note;
        }
        Long newestNoteId = newestNote.id;
        NoteDao noteDao = mAppDatabase.noteDao();

        // assert: the capped note search respects LIMIT and returns the
        // newest rows first (entry_date_time DESC); every note matches all
        // four sources, so the single OR'd query exercises every source
        List<Note> notes = noteDao.searchNotesWithLimit("limit", 2);
        assertEquals(2, notes.size());
        assertEquals(newestNoteId, notes.get(0).id);
        List<Note> scopedNotes = noteDao.searchNotesWithProfileLimit(profile.id, "limit", 3);
        assertEquals(3, scopedNotes.size());
        assertEquals(newestNoteId, scopedNotes.get(0).id);
    }

    @Test
    public void searchNotes_mergesAllSources_globallyOrderedByEntryDateTimeDescThenIdDesc() {
        // arrange: four notes matched by different sources with distinct
        // entry dates plus two same-date notes proving the id tiebreaker;
        // noteTag matches both the content and the tag source
        Profile profile = insertProfile("Profile", "about");
        Note noteContent = insertNote(profile.id, "magicword in content", minutesAfterBase(10));
        Note noteMedicine = insertNote(profile.id, "plain content", minutesAfterBase(20));
        insertMedicine(noteMedicine.id, "magicword medicine", "desc");
        Note noteIntake = insertNote(profile.id, "another plain content", minutesAfterBase(30));
        Medicine intakeMedicine = insertMedicine(noteIntake.id, "Insulin", "desc");
        insertMedicineIntake(intakeMedicine.id, "took with magicword remark");
        Note noteTag = insertNote(profile.id, "magicword in content and tag", minutesAfterBase(40));
        insertNoteTag(noteTag.id, "magicword tag");
        // identical entry_date_time: the later-inserted (higher id) note wins
        Note tieOlder = insertNote(profile.id, "magicword tie note one", minutesAfterBase(1));
        Note tieNewer = insertNote(profile.id, "magicword tie note two", minutesAfterBase(1));
        assertTrue(tieOlder.id < tieNewer.id);
        NoteDao noteDao = mAppDatabase.noteDao();

        // assert: six unique notes, newest first regardless of which source
        // matched, with equal entry dates ordered by id DESC
        List<Note> result = noteDao.searchNotesWithProfileLimit(profile.id, "magicword", 100);
        assertEquals(6, result.size());
        assertEquals(noteTag.id, result.get(0).id);
        assertEquals(noteIntake.id, result.get(1).id);
        assertEquals(noteMedicine.id, result.get(2).id);
        assertEquals(noteContent.id, result.get(3).id);
        assertEquals(tieNewer.id, result.get(4).id);
        assertEquals(tieOlder.id, result.get(5).id);
        assertSortedByEntryDateTimeDesc(result);
    }

    private static void assertSortedByEntryDateTimeDesc(List<Note> notes) {
        for (int i = 1; i < notes.size(); i++) {
            long previous = notes.get(i - 1).entryDateTime.getTime();
            long current = notes.get(i).entryDateTime.getTime();
            assertTrue("notes must be ordered by entry_date_time DESC, got "
                            + previous + " before " + current,
                    previous >= current);
        }
    }

    // ---------------------------------------------------------------- seed builders

    private Profile insertProfile(String name, String about) {
        Profile profile = new Profile();
        profile.name = name;
        profile.about = about;
        profile.id = mAppDatabase.profileDao().insert(profile);
        return profile;
    }

    private Note insertNote(Long profileId, String content, Date entryDateTime) {
        Note note = new Note();
        note.profileId = profileId;
        note.content = content;
        note.entryDateTime = entryDateTime;
        note.id = mAppDatabase.noteDao().insert(note);
        return note;
    }

    private NoteTag insertNoteTag(Long noteId, String tag) {
        NoteTag noteTag = new NoteTag();
        noteTag.noteId = noteId;
        noteTag.tag = tag;
        noteTag.id = mAppDatabase.noteTagDao().insert(noteTag);
        return noteTag;
    }

    private Medicine insertMedicine(Long noteId, String name, String description) {
        Medicine medicine = new Medicine();
        medicine.noteId = noteId;
        medicine.name = name;
        medicine.description = description;
        medicine.id = mAppDatabase.medicineDao().insert(medicine);
        return medicine;
    }

    private MedicineIntake insertMedicineIntake(Long medicineId, String description) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.description = description;
        medicineIntake.takenDateTime = new Date();
        medicineIntake.id = mAppDatabase.medicineIntakeDao().insert(medicineIntake);
        return medicineIntake;
    }

    private Date minutesAfterBase(int minutes) {
        return new Date(mBaseTime + TimeUnit.MINUTES.toMillis(minutes));
    }
}
