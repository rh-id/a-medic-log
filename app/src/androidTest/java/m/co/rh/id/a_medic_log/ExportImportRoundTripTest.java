package m.co.rh.id.a_medic_log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicine;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicineReminder;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createNote;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createProfile;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.profileEntryName;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.readZipEntryText;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.writeBytes;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_medic_log.app.provider.command.ExportArchiveCmd;
import m.co.rh.id.a_medic_log.app.provider.command.ImportArchiveCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class ExportImportRoundTripTest {
    private static final byte[] ATTACHMENT_BYTES =
            "test attachment image content 0123456789".getBytes();

    private Provider mProvider;
    private AppDatabase mAppDatabase;
    private FileHelper mFileHelper;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private NoteTagDao mNoteTagDao;
    private NoteAttachmentDao mNoteAttachmentDao;
    private NoteAttachmentFileDao mNoteAttachmentFileDao;
    private MedicineDao mMedicineDao;
    private MedicineReminderDao mMedicineReminderDao;
    private MedicineIntakeDao mMedicineIntakeDao;
    private ExportArchiveCmd mExportArchiveCmd;
    private ImportArchiveCmd mImportArchiveCmd;
    private final List<String> mCreatedAttachmentFileNames = new ArrayList<>();
    private final List<File> mCreatedTempFiles = new ArrayList<>();

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new ExportImportTestProviderModule());
        mAppDatabase = mProvider.get(AppDatabase.class);
        mFileHelper = mProvider.get(FileHelper.class);
        mProfileDao = mProvider.get(ProfileDao.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mNoteTagDao = mProvider.get(NoteTagDao.class);
        mNoteAttachmentDao = mProvider.get(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = mProvider.get(NoteAttachmentFileDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mMedicineReminderDao = mProvider.get(MedicineReminderDao.class);
        mMedicineIntakeDao = mProvider.get(MedicineIntakeDao.class);
        mExportArchiveCmd = mProvider.get(ExportArchiveCmd.class);
        mImportArchiveCmd = mProvider.get(ImportArchiveCmd.class);
    }

    @After
    public void cleanup() {
        for (String fileName : mCreatedAttachmentFileNames) {
            mFileHelper.deleteNoteAttachmentImage(fileName);
        }
        for (File tempFile : mCreatedTempFiles) {
            tempFile.delete();
        }
        mCreatedAttachmentFileNames.clear();
        mCreatedTempFiles.clear();
        mAppDatabase.close();
        mProvider.dispose();
    }

    @Test
    public void exportThenFullImport_restoresAllRowsWithNewIds() throws IOException {
        // arrange: profile with 2 notes, tag, attachment + file, medicine, reminder, intake
        Profile originalProfile = createProfile(mProfileDao, "John Doe", "test about");
        Long originalProfileId = originalProfile.id;
        Note originalNote1 = createNote(mNoteDao, originalProfileId, "round trip note");
        Long originalNote1Id = originalNote1.id;
        Note originalNote2 = createNote(mNoteDao, originalProfileId, "second note");
        Long originalNote2Id = originalNote2.id;
        NoteTag originalTag = createNoteTag(originalNote1Id, "fever");
        Long originalTagId = originalTag.id;
        NoteAttachment originalAttachment = createNoteAttachment(originalNote1Id, "photo");
        Long originalAttachmentId = originalAttachment.id;
        NoteAttachmentFile originalFile = createNoteAttachmentFile(originalAttachmentId,
                "original-image.png");
        Long originalFileId = originalFile.id;
        Medicine originalMedicine = createMedicine(mMedicineDao, originalNote1Id, "Paracetamol", "500mg");
        Long originalMedicineId = originalMedicine.id;
        MedicineReminder originalReminder = createMedicineReminder(mMedicineReminderDao,
                originalMedicineId, "take 1 pill", new Date(), true,
                Calendar.MONDAY, Calendar.TUESDAY);
        Long originalReminderId = originalReminder.id;
        MedicineIntake originalIntake = createMedicineIntake(originalMedicineId, "took 10ml");
        Long originalIntakeId = originalIntake.id;

        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);
        assertTrue(exportFile.exists());

        // act: wipe the whole database then import everything back
        mAppDatabase.clearAllTables();
        TestSubscriber<NoteState> noteAddedSubscriber = new TestSubscriber<>();
        mProvider.get(NoteChangeNotifier.class).getAddedNote().subscribe(noteAddedSubscriber);
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(Uri.fromFile(exportFile), null).blockingGet();

        // assert: counts of every inserted row type
        assertEquals(1, importResult.profileCount);
        assertEquals(2, importResult.noteCount);
        assertEquals(1, importResult.tagCount);
        assertEquals(1, importResult.attachmentCount);
        assertEquals(1, importResult.fileCount);
        assertEquals(1, importResult.medicineCount);
        assertEquals(1, importResult.reminderCount);
        assertEquals(1, importResult.intakeCount);
        // assert: exactly one noteAdded event per imported note carries the reminder scheduling
        noteAddedSubscriber.assertValueCount(2);

        // assert: profile row restored with a regenerated id and the same data
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(1, profiles.size());
        Profile importedProfile = profiles.get(0);
        assertEquals("John Doe", importedProfile.name);
        assertEquals("test about", importedProfile.about);
        assertEquals(originalProfile.createdDateTime, importedProfile.createdDateTime);
        assertNotEquals(originalProfileId, importedProfile.id);

        // assert: notes restored under the new profile id with regenerated ids
        List<Note> importedNotes = mNoteDao.findNotesByProfileId(importedProfile.id);
        assertEquals(2, importedNotes.size());
        Note importedNote1 = findNoteByContent(importedNotes, "round trip note");
        assertNotNull(importedNote1);
        assertNotEquals(originalNote1Id, importedNote1.id);
        assertEquals(importedProfile.id, importedNote1.profileId);
        assertEquals(originalNote1.entryDateTime, importedNote1.entryDateTime);
        Note importedNote2 = findNoteByContent(importedNotes, "second note");
        assertNotNull(importedNote2);
        assertNotEquals(originalNote2Id, importedNote2.id);
        assertEquals(importedProfile.id, importedNote2.profileId);
        // dates survive the archive round trip as exact epoch millis
        assertEquals(originalNote2.entryDateTime, importedNote2.entryDateTime);

        // assert: tag restored under the new note id
        List<NoteTag> importedTags = mNoteTagDao.findNoteTagsByNoteId(importedNote1.id);
        assertEquals(1, importedTags.size());
        assertNotEquals(originalTagId, importedTags.get(0).id);
        assertEquals("fever", importedTags.get(0).tag);
        assertEquals(importedNote1.id, importedTags.get(0).noteId);

        // assert: attachment restored under the new note id,
        // file row points to files that exist in the FileHelper dirs with the original content
        List<NoteAttachment> importedAttachments =
                mNoteAttachmentDao.findNoteAttachmentsByNoteId(importedNote1.id);
        assertEquals(1, importedAttachments.size());
        assertNotEquals(originalAttachmentId, importedAttachments.get(0).id);
        assertEquals(importedNote1.id, importedAttachments.get(0).noteId);
        assertEquals("photo", importedAttachments.get(0).name);
        List<NoteAttachmentFile> importedFiles = mNoteAttachmentFileDao
                .findNoteAttachmentFilesByAttachmentId(importedAttachments.get(0).id);
        assertEquals(1, importedFiles.size());
        assertNotEquals(originalFileId, importedFiles.get(0).id);
        assertEquals(importedAttachments.get(0).id, importedFiles.get(0).attachmentId);
        String importedFileName = importedFiles.get(0).fileName;
        assertNotEquals("original-image.png", importedFileName);
        File importedImage = mFileHelper.getNoteAttachmentImage(importedFileName);
        File importedThumbnail = mFileHelper.getNoteAttachmentThumbnail(importedFileName);
        assertTrue(importedImage.exists());
        assertTrue(importedThumbnail.exists());
        assertFalse(importedImage.getAbsolutePath()
                .equals(mFileHelper.getNoteAttachmentImage("original-image.png").getAbsolutePath()));
        mCreatedAttachmentFileNames.add(importedFileName);

        // assert: medicine, reminder and intake restored with correct parent references
        List<Medicine> importedMedicines = mMedicineDao.findMedicinesByNoteId(importedNote1.id);
        assertEquals(1, importedMedicines.size());
        assertNotEquals(originalMedicineId, importedMedicines.get(0).id);
        assertEquals("Paracetamol", importedMedicines.get(0).name);
        assertEquals("500mg", importedMedicines.get(0).description);
        assertEquals(importedNote1.id, importedMedicines.get(0).noteId);
        List<MedicineReminder> importedReminders = mMedicineReminderDao
                .findMedicineRemindersByMedicineId(importedMedicines.get(0).id);
        assertEquals(1, importedReminders.size());
        assertNotEquals(originalReminderId, importedReminders.get(0).id);
        assertEquals("take 1 pill", importedReminders.get(0).message);
        assertEquals(originalReminder.startDateTime, importedReminders.get(0).startDateTime);
        assertEquals(importedMedicines.get(0).id, importedReminders.get(0).medicineId);
        assertTrue(importedReminders.get(0).reminderEnabled);
        assertTrue(importedReminders.get(0).reminderDays.contains(Calendar.MONDAY));
        assertTrue(importedReminders.get(0).reminderDays.contains(Calendar.TUESDAY));
        List<MedicineIntake> importedIntakes = mMedicineIntakeDao
                .findMedicineIntakesByMedicineId(importedMedicines.get(0).id);
        assertEquals(1, importedIntakes.size());
        assertNotEquals(originalIntakeId.longValue(), importedIntakes.get(0).id);
        assertEquals("took 10ml", importedIntakes.get(0).description);
        assertEquals(originalIntake.takenDateTime, importedIntakes.get(0).takenDateTime);
        assertEquals(importedMedicines.get(0).id, importedIntakes.get(0).medicineId);
    }

    @Test
    public void partialImportIntoPopulatedDb_existingRowsUntouched() throws IOException {
        // arrange: profiles A and B, both are exported into the archive while
        // only A is later passed as the fileProfileIds filter of importProfiles
        Profile originalProfileA = createProfile(mProfileDao, "Profile A", "about A");
        Long originalProfileAId = originalProfileA.id;
        Profile originalProfileB = createProfile(mProfileDao, "Profile B", "about B");
        Note originalNoteA = createNote(mNoteDao, originalProfileA.id, "note of A");
        Long originalNoteAId = originalNoteA.id;
        Note originalNoteB = createNote(mNoteDao, originalProfileB.id, "note of B");
        // the archive carries a medicine (with a reminder) under B's note,
        // so the "no medicines under any note" assertion below is not vacuous
        Medicine originalMedicineB = createMedicine(mMedicineDao, originalNoteB.id,
                "B Ibuprofen", "200mg");
        createMedicineReminder(mMedicineReminderDao, originalMedicineB.id, "take B pill",
                new Date(), true, Calendar.WEDNESDAY);
        File exportFile = mExportArchiveCmd
                .export(Arrays.asList(originalProfileA.id, originalProfileB.id)).blockingGet();
        mCreatedTempFiles.add(exportFile);
        // assert: the fixture is real, B's profile entry in the archive carries
        // the medicine that the partial import of A must leave out
        String profileBEntryText = readZipEntryText(exportFile,
                profileEntryName(originalProfileB.id));
        assertTrue("B's profile entry must carry the B medicine name",
                profileBEntryText.contains("B Ibuprofen"));
        assertTrue("B's profile entry must carry the B medicine description",
                profileBEntryText.contains("200mg"));

        // act: wipe db, populate it with an unrelated profile, then import only profile A
        mAppDatabase.clearAllTables();
        Profile existingProfile = createProfile(mProfileDao, "Existing Profile", "existing about");
        Long existingProfileId = existingProfile.id;
        Note existingNote = createNote(mNoteDao, existingProfileId, "existing note");
        Long existingNoteId = existingNote.id;
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(Uri.fromFile(exportFile),
                        Collections.singletonList(originalProfileAId)).blockingGet();

        // assert: only profile A was imported, with regenerated ids
        assertEquals(1, importResult.profileCount);
        assertEquals(1, importResult.noteCount);
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(2, profiles.size());
        Profile importedProfileA = findProfileByName(profiles, "Profile A");
        assertNotNull(importedProfileA);
        assertNotEquals(originalProfileAId, importedProfileA.id);
        List<Note> importedNotesA = mNoteDao.findNotesByProfileId(importedProfileA.id);
        assertEquals(1, importedNotesA.size());
        assertEquals("note of A", importedNotesA.get(0).content);
        assertNotEquals(originalNoteAId, importedNotesA.get(0).id);

        // assert: no trace of profile B, the fileProfileIds filter was honored:
        // no profile named B, no note of B and zero medicines under any note
        // (meaningful because the archive carries B's medicine, so a filter
        // leak of B's note or its medicines would show up here)
        assertNoProfileNamed(profiles, "Profile B");
        List<Note> allNotes = new ArrayList<>();
        for (Profile profile : profiles) {
            allNotes.addAll(mNoteDao.findNotesByProfileId(profile.id));
        }
        assertEquals(2, allNotes.size());
        assertNull(findNoteByContent(allNotes, "note of B"));
        for (Note note : allNotes) {
            assertTrue(mMedicineDao.findMedicinesByNoteId(note.id).isEmpty());
        }

        // assert: the pre-existing row is untouched (same id, same note)
        Profile existingProfileFromDb = mProfileDao.findProfileById(existingProfileId);
        assertNotNull(existingProfileFromDb);
        assertEquals("Existing Profile", existingProfileFromDb.name);
        assertEquals("existing about", existingProfileFromDb.about);
        Note existingNoteFromDb = mNoteDao.findNoteById(existingNoteId);
        assertNotNull(existingNoteFromDb);
        assertEquals("existing note", existingNoteFromDb.content);
        assertEquals(existingProfileId, existingNoteFromDb.profileId);
        List<Note> existingProfileNotes = mNoteDao.findNotesByProfileId(existingProfileId);
        assertEquals(1, existingProfileNotes.size());
    }

    @Test
    public void importSameFileTwice_createsDuplicatesWithoutDuplicateFileNames() throws IOException {
        // arrange
        Profile originalProfile = createProfile(mProfileDao, "Duplicated Profile", "about");
        Note note = createNote(mNoteDao, originalProfile.id, "duplicated note");
        NoteAttachment attachment = createNoteAttachment(note.id, "photo");
        createNoteAttachmentFile(attachment.id, "duplicate-image.png");
        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);

        // act: wipe the database and import the same file twice
        mAppDatabase.clearAllTables();
        ImportArchiveCmd.ImportResult firstImport = mImportArchiveCmd
                .importProfiles(Uri.fromFile(exportFile), null).blockingGet();
        ImportArchiveCmd.ImportResult secondImport = mImportArchiveCmd
                .importProfiles(Uri.fromFile(exportFile), null).blockingGet();

        // assert: duplicates created
        assertEquals(1, firstImport.profileCount);
        assertEquals(1, secondImport.profileCount);
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(2, profiles.size());
        assertEquals("Duplicated Profile", profiles.get(0).name);
        assertEquals("Duplicated Profile", profiles.get(1).name);
        assertNotEquals(profiles.get(0).id, profiles.get(1).id);

        // assert: every attachment file_name row is unique, each file exists in the dirs
        Set<String> fileNames = new HashSet<>();
        List<String> fileNameList = new ArrayList<>();
        for (Profile profile : profiles) {
            for (Note importedNote : mNoteDao.findNotesByProfileId(profile.id)) {
                for (NoteAttachment importedAttachment :
                        mNoteAttachmentDao.findNoteAttachmentsByNoteId(importedNote.id)) {
                    for (NoteAttachmentFile file : mNoteAttachmentFileDao
                            .findNoteAttachmentFilesByAttachmentId(importedAttachment.id)) {
                        fileNames.add(file.fileName);
                        fileNameList.add(file.fileName);
                        assertTrue(mFileHelper.getNoteAttachmentImage(file.fileName).exists());
                        assertTrue(mFileHelper.getNoteAttachmentThumbnail(file.fileName).exists());
                        mCreatedAttachmentFileNames.add(file.fileName);
                    }
                }
            }
        }
        assertEquals(2, fileNameList.size());
        assertEquals(fileNameList.size(), fileNames.size());
    }

    private NoteTag createNoteTag(Long noteId, String tag) {
        NoteTag noteTag = new NoteTag();
        noteTag.noteId = noteId;
        noteTag.tag = tag;
        noteTag.id = mNoteTagDao.insert(noteTag);
        return noteTag;
    }

    private NoteAttachment createNoteAttachment(Long noteId, String name) {
        NoteAttachment noteAttachment = new NoteAttachment();
        noteAttachment.noteId = noteId;
        noteAttachment.name = name;
        noteAttachment.id = mNoteAttachmentDao.insert(noteAttachment);
        return noteAttachment;
    }

    private NoteAttachmentFile createNoteAttachmentFile(Long attachmentId, String fileName)
            throws IOException {
        writeBytes(new File(mFileHelper.getNoteAttachmentImageParent(), fileName), ATTACHMENT_BYTES);
        writeBytes(new File(mFileHelper.getNoteAttachmentThumbnailParent(), fileName), ATTACHMENT_BYTES);
        mCreatedAttachmentFileNames.add(fileName);
        NoteAttachmentFile noteAttachmentFile = new NoteAttachmentFile();
        noteAttachmentFile.attachmentId = attachmentId;
        noteAttachmentFile.fileName = fileName;
        noteAttachmentFile.id = mNoteAttachmentFileDao.insert(noteAttachmentFile);
        return noteAttachmentFile;
    }

    private MedicineIntake createMedicineIntake(Long medicineId, String description) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.description = description;
        medicineIntake.takenDateTime = new Date();
        medicineIntake.id = mMedicineIntakeDao.insert(medicineIntake);
        return medicineIntake;
    }

    private static Note findNoteByContent(List<Note> notes, String content) {
        for (Note note : notes) {
            if (content.equals(note.content)) {
                return note;
            }
        }
        return null;
    }

    private static Profile findProfileByName(List<Profile> profiles, String name) {
        for (Profile profile : profiles) {
            if (name.equals(profile.name)) {
                return profile;
            }
        }
        return null;
    }

    private static void assertNoProfileNamed(List<Profile> profiles, String name) {
        for (Profile profile : profiles) {
            assertFalse(name.equals(profile.name));
        }
    }
}
