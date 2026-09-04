package m.co.rh.id.a_medic_log.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static m.co.rh.id.a_medic_log.ExportImportTestUtils.ENTRY_EXPORT_JSON;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.collectChildFilePaths;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicine;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicineReminder;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createNote;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createProfile;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.hasIOExceptionWithMessage;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.isOrCausedByIOException;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.profileEntryName;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.writeBytes;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.zipEntryNames;

import android.content.Context;
import android.net.Uri;

import androidx.room.InvalidationTracker;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

import m.co.rh.id.a_medic_log.ExportImportTestProviderModule;
import m.co.rh.id.a_medic_log.ExportImportTestUtils;
import m.co.rh.id.a_medic_log.app.provider.command.ExportArchiveCmd;
import m.co.rh.id.a_medic_log.app.provider.command.ImportArchiveCmd;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderEventHandler;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.workmanager.Tags;
import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.AndroidNotificationDao;
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
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;

/**
 * Crash-safety tests of ImportArchiveCmd. Its class contract: attachment files are
 * extracted and copied first, then all database inserts run in a single transaction;
 * when anything fails the copied attachment files are deleted and no data is committed.
 * <p>
 * Note on the failure injection of
 * {@link #importProfiles_failedTransaction_deletesCopiedAttachmentFiles()}:
 * the export.json format itself cannot produce a database constraint violation, so the
 * transaction failure is forced through a test-only AppDatabase decorator. Evidence:
 * - none of the imported entities declares a NOT NULL column (no @NonNull on any
 * ColumnInfo of Profile, Note, NoteTag, NoteAttachment, NoteAttachmentFile, Medicine,
 * MedicineReminder, MedicineIntake; the only primitive column, MedicineIntake.id,
 * is explicitly set to 0 before its insert);
 * - ImportArchiveCmd parses every value tolerantly: org.json optString coerces a JSON
 * null into the literal string "null" (never SQL NULL), optLong/optBoolean fall back
 * to defaults, reminder_days always becomes a non-null LinkedHashSet, and a missing
 * date becomes epoch 0;
 * - every parent id is regenerated inside the transaction, so the foreign key
 * constraints (enforced by Room because the entities declare foreignKeys) can never
 * be violated by crafted archive content, and no imported table has a unique index.
 * The decorator therefore exercises exactly the production cleanup path.
 */
@RunWith(AndroidJUnit4.class)
public class ImportArchiveCmdTest {

    private static final String KEY_FORMAT_VERSION = "format_version";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final byte[] ATTACHMENT_BYTES =
            "crash safety attachment image content".getBytes();
    private static final String ATTACHMENT_FILE_NAME = "crash-safety-image.png";
    private static final String ENTRY_IMAGE = "attachments/image/" + ATTACHMENT_FILE_NAME;
    private static final String ENTRY_THUMBNAIL = "attachments/image/thumbnail/" + ATTACHMENT_FILE_NAME;

    private static final long SCHEDULING_TIMEOUT_MILLIS = 10_000;
    private static final long WORK_COUNT_POLL_INTERVAL_MILLIS = 100;

    private Provider mProvider;
    private AppDatabase mAppDatabase;
    private FileHelper mFileHelper;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private NoteAttachmentDao mNoteAttachmentDao;
    private NoteAttachmentFileDao mNoteAttachmentFileDao;
    private MedicineDao mMedicineDao;
    private MedicineReminderDao mMedicineReminderDao;
    private MedicineIntakeDao mMedicineIntakeDao;
    private ExportArchiveCmd mExportArchiveCmd;
    private ImportArchiveCmd mImportArchiveCmd;
    private TransactionFailingAppDatabase mTransactionFailingAppDatabase;
    private final List<String> mCreatedAttachmentFileNames = new ArrayList<>();
    private final List<File> mCreatedTempFiles = new ArrayList<>();

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // the test WorkManager must be initialized before the provider is created,
        // the test provider module resolves WorkManager.getInstance during creation
        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        // wrap the in-memory database so a test can force a transaction failure,
        // when unarmed the wrapper is a pass-through decorator
        ExportImportTestProviderModule module = new ExportImportTestProviderModule(
                appDatabase -> {
                    TransactionFailingAppDatabase wrapper =
                            new TransactionFailingAppDatabase(appDatabase);
                    mTransactionFailingAppDatabase = wrapper;
                    return wrapper;
                });
        mProvider = Provider.createProvider(context, module);
        mAppDatabase = mProvider.get(AppDatabase.class);
        mFileHelper = mProvider.get(FileHelper.class);
        mProfileDao = mProvider.get(ProfileDao.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mNoteAttachmentDao = mProvider.get(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = mProvider.get(NoteAttachmentFileDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mMedicineReminderDao = mProvider.get(MedicineReminderDao.class);
        mMedicineIntakeDao = mProvider.get(MedicineIntakeDao.class);
        mExportArchiveCmd = mProvider.get(ExportArchiveCmd.class);
        mImportArchiveCmd = mProvider.get(ImportArchiveCmd.class);
        // wait for the event handler subscription so the import events cannot be missed
        mProvider.get(MedicineReminderEventHandler.class);
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
    public void peek_returnsExactlyTheFileProfilesInsideTheArchive() throws Exception {
        // arrange: export two profiles into a real export archive,
        // profile A carries a note with one medicine and one reminder
        // so the manifest content counts are non-trivial for A only
        Profile profileA = createProfile(mProfileDao, "Peek Profile A", "about A");
        Profile profileB = createProfile(mProfileDao, "Peek Profile B", "about B");
        Note noteA = createNote(mNoteDao, profileA.id, "peek note of A");
        createNote(mNoteDao, profileB.id, "peek note of B");
        Medicine medicineA = createMedicine(mMedicineDao, noteA.id, "Paracetamol", "500mg");
        createMedicineReminder(mMedicineReminderDao, medicineA.id, "take 1 pill",
                new Date(), true, Calendar.MONDAY);
        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);

        // act
        List<ImportArchiveCmd.FileProfile> fileProfiles =
                mImportArchiveCmd.peek(Uri.fromFile(exportFile)).blockingGet();

        // assert: exactly the exported profiles are found, matched by id and name
        assertEquals(2, fileProfiles.size());
        Map<Long, ImportArchiveCmd.FileProfile> foundFileProfiles = new HashMap<>();
        for (ImportArchiveCmd.FileProfile fileProfile : fileProfiles) {
            foundFileProfiles.put(fileProfile.fileProfileId, fileProfile);
        }
        assertEquals(2, foundFileProfiles.size());
        assertEquals("Peek Profile A", foundFileProfiles.get(profileA.id).name);
        assertEquals("Peek Profile B", foundFileProfiles.get(profileB.id).name);

        // assert: the manifest content counts arrive through peek
        ImportArchiveCmd.FileProfile foundProfileA = foundFileProfiles.get(profileA.id);
        assertEquals(1, foundProfileA.noteCount);
        assertEquals(1, foundProfileA.medicineCount);
        assertEquals(1, foundProfileA.reminderCount);
        assertEquals(0, foundProfileA.attachmentCount);
        ImportArchiveCmd.FileProfile foundProfileB = foundFileProfiles.get(profileB.id);
        assertEquals(1, foundProfileB.noteCount);
        assertEquals(0, foundProfileB.medicineCount);
        assertEquals(0, foundProfileB.reminderCount);
        assertEquals(0, foundProfileB.attachmentCount);
    }

    @Test
    public void peekAndImport_garbageBytesFile_fails() throws Exception {
        // arrange: a file that is not a zip archive at all
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File garbageFile = new File(context.getCacheDir(), "garbage_export.zip");
        mCreatedTempFiles.add(garbageFile);
        writeBytes(garbageFile, "this is not a zip archive".getBytes(StandardCharsets.UTF_8));
        Uri garbageUri = Uri.fromFile(garbageFile);

        // act + assert: both peek and importProfiles report an error
        assertBlockingFails(mImportArchiveCmd.peek(garbageUri));
        assertBlockingFails(mImportArchiveCmd.importProfiles(garbageUri, null));
    }

    @Test
    public void peekAndImport_unsupportedFormatVersion_fails() throws Exception {
        // arrange: a hand-built zip whose export.json declares a future format version
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File unsupportedFile = new File(context.getCacheDir(), "unsupported_version_export.zip");
        mCreatedTempFiles.add(unsupportedFile);
        createManifestZip(unsupportedFile, ExportArchiveCmd.FORMAT_VERSION + 1, new JSONArray());
        Uri unsupportedUri = Uri.fromFile(unsupportedFile);

        // act + assert: the version check rejects versions above the supported one
        assertBlockingFails(mImportArchiveCmd.peek(unsupportedUri));
        assertBlockingFails(mImportArchiveCmd.importProfiles(unsupportedUri, null));
    }

    @Test
    public void peekAndImport_missingFormatVersion_fails() throws Exception {
        // arrange: a hand-built zip whose export.json has no format_version at all
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File missingVersionFile = new File(context.getCacheDir(), "missing_version_export.zip");
        mCreatedTempFiles.add(missingVersionFile);
        createManifestZip(missingVersionFile, null, new JSONArray());
        Uri missingVersionUri = Uri.fromFile(missingVersionFile);

        // act + assert: the version check rejects a missing (or below 1) format version
        assertBlockingFails(mImportArchiveCmd.peek(missingVersionUri));
        assertBlockingFails(mImportArchiveCmd.importProfiles(missingVersionUri, null));
    }

    @Test
    public void importProfiles_missingAttachmentImageEntry_importsWithFileRowSkipped()
            throws Exception {
        // arrange: a real export archive, rebuilt without the attachment image entry
        // (the thumbnail entry and the export.json keep referencing the file)
        Profile profile = createProfile(mProfileDao, "Crash Safety Profile", "about");
        Note note = createNote(mNoteDao, profile.id, "crash safety note");
        NoteAttachment attachment = createNoteAttachment(note.id, "photo");
        createNoteAttachmentFile(attachment.id, ATTACHMENT_FILE_NAME);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Medicine medicine = createMedicine(mMedicineDao, note.id, "Paracetamol", "500mg");
        createMedicineReminder(mMedicineReminderDao, medicine.id, "take 1 pill",
                new Date(), true, Calendar.MONDAY);
        createMedicineIntake(medicine.id, "took 10ml");
        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);
        File brokenArchiveFile = new File(context.getCacheDir(), "missing_attachment_export.zip");
        mCreatedTempFiles.add(brokenArchiveFile);
        List<String> copiedEntryNames = ExportImportTestUtils.copyZipWithoutEntries(
                exportFile, brokenArchiveFile, ENTRY_IMAGE);
        assertTrue("the manifest must remain in the rebuilt archive",
                copiedEntryNames.contains(ENTRY_EXPORT_JSON));
        List<String> brokenEntryNames = zipEntryNames(brokenArchiveFile);
        assertFalse("the image entry must be missing from the rebuilt archive",
                brokenEntryNames.contains(ENTRY_IMAGE));
        assertTrue("the thumbnail entry must remain in the rebuilt archive",
                brokenEntryNames.contains(ENTRY_THUMBNAIL));

        // wipe the database, snapshot the attachment image/thumbnail dirs
        // (the thumbnail dir is inside the image dir, one snapshot covers both)
        mAppDatabase.clearAllTables();
        List<String> attachmentFilesBefore = collectChildFilePaths(
                mFileHelper.getNoteAttachmentImageParent());

        // act
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(Uri.fromFile(brokenArchiveFile), null).blockingGet();

        // assert: documented skip-and-continue of copyExtractedAttachmentFiles,
        // the file row with the missing image entry is skipped, everything else is imported
        assertEquals(1, importResult.profileCount);
        assertEquals(1, importResult.noteCount);
        assertEquals(0, importResult.tagCount);
        assertEquals(1, importResult.attachmentCount);
        assertEquals(0, importResult.fileCount);
        assertEquals(1, importResult.medicineCount);
        assertEquals(1, importResult.reminderCount);
        assertEquals(1, importResult.intakeCount);

        // assert: note, attachment, medicine, reminder and intake rows are all present
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(1, profiles.size());
        List<Note> importedNotes = mNoteDao.findNotesByProfileId(profiles.get(0).id);
        assertEquals(1, importedNotes.size());
        assertEquals("crash safety note", importedNotes.get(0).content);
        List<NoteAttachment> importedAttachments =
                mNoteAttachmentDao.findNoteAttachmentsByNoteId(importedNotes.get(0).id);
        assertEquals(1, importedAttachments.size());
        List<NoteAttachmentFile> importedFiles = mNoteAttachmentFileDao
                .findNoteAttachmentFilesByAttachmentId(importedAttachments.get(0).id);
        assertEquals(0, importedFiles.size());
        assertNull("the skipped file row must not exist under its original name",
                mNoteAttachmentFileDao.findNoteAttachmentFileByFileName(ATTACHMENT_FILE_NAME));
        List<Medicine> importedMedicines =
                mMedicineDao.findMedicinesByNoteId(importedNotes.get(0).id);
        assertEquals(1, importedMedicines.size());
        assertEquals(1, mMedicineReminderDao
                .findMedicineRemindersByMedicineId(importedMedicines.get(0).id).size());
        assertEquals(1, mMedicineIntakeDao
                .findMedicineIntakesByMedicineId(importedMedicines.get(0).id).size());

        // assert: skip-and-continue copied no file, nothing is left behind in the dirs
        assertEquals(attachmentFilesBefore, collectChildFilePaths(
                mFileHelper.getNoteAttachmentImageParent()));
    }

    @Test
    public void importProfiles_failedTransaction_deletesCopiedAttachmentFiles() throws Exception {
        // arrange: a real export of a profile subtree containing an attachment file,
        // then a forced mid-transaction failure (see the class javadoc for why the
        // transaction must be forced through a test decorator instead of crafted json)
        Profile profile = createProfile(mProfileDao, "Failed Import Profile", "about");
        Note note = createNote(mNoteDao, profile.id, "failed import note");
        NoteAttachment attachment = createNoteAttachment(note.id, "photo");
        createNoteAttachmentFile(attachment.id, ATTACHMENT_FILE_NAME);
        Medicine medicine = createMedicine(mMedicineDao, note.id, "Ibuprofen", "200mg");
        createMedicineReminder(mMedicineReminderDao, medicine.id, "take 1 pill",
                new Date(), true, Calendar.MONDAY);
        createMedicineIntake(medicine.id, "took 5ml");
        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);

        // wipe the database rows, snapshot the attachment image/thumbnail dirs,
        // then arm the wrapper so the import transaction fails
        mAppDatabase.clearAllTables();
        List<String> attachmentFilesBefore = collectChildFilePaths(
                mFileHelper.getNoteAttachmentImageParent());
        mTransactionFailingAppDatabase.failNextTransaction();

        // act + assert: the Single reports the forced transaction failure
        try {
            mImportArchiveCmd.importProfiles(Uri.fromFile(exportFile), null).blockingGet();
            fail("Expected the import to fail with the forced transaction error");
        } catch (Exception expected) {
            assertTrue("the failure must be the forced transaction IllegalStateException",
                    expected instanceof IllegalStateException);
        }

        // assert: the copied attachment files were cleaned up,
        // the attachment image/thumbnail dirs hold exactly the pre-import files
        assertEquals(attachmentFilesBefore, collectChildFilePaths(
                mFileHelper.getNoteAttachmentImageParent()));

        // assert: nothing was committed by the failed transaction
        assertTrue(mProfileDao.findProfiles().isEmpty());
    }

    @Test
    public void importProfiles_schedulesReminderWorkOnlyForEnabledReminders() throws Exception {
        // arrange: a note carrying one medicine with an enabled and one with a disabled reminder,
        // reminderDays is kept for realism only, it is irrelevant at enqueue time:
        // the WorkManager scheduling depends solely on reminderEnabled + startDateTime
        // (see MedicineReminderEventHandler.startMedicineReminderNotificationWork)
        Profile originalProfile = createProfile(mProfileDao, "Reminder Profile", "about");
        Note note = createNote(mNoteDao, originalProfile.id, "reminder note");
        Medicine enabledMedicine = createMedicine(mMedicineDao, note.id, "Enabled Med", "500mg");
        MedicineReminder originalEnabledReminder = createEnabledReminder(enabledMedicine.id);
        Medicine disabledMedicine = createMedicine(mMedicineDao, note.id, "Disabled Med", "200mg");
        MedicineReminder originalDisabledReminder = createDisabledReminder(disabledMedicine.id);

        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);

        // act: wipe the database then import everything back
        mAppDatabase.clearAllTables();
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(Uri.fromFile(exportFile), null).blockingGet();
        assertEquals(1, importResult.profileCount);
        assertEquals(2, importResult.medicineCount);
        assertEquals(2, importResult.reminderCount);

        // assert: the imported reminders are regenerated with new ids
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(1, profiles.size());
        List<Note> importedNotes = mNoteDao.findNotesByProfileId(profiles.get(0).id);
        assertEquals(1, importedNotes.size());
        List<Medicine> importedMedicines = mMedicineDao.findMedicinesByNoteId(importedNotes.get(0).id);
        assertEquals(2, importedMedicines.size());
        MedicineReminder importedEnabledReminder = null;
        MedicineReminder importedDisabledReminder = null;
        for (Medicine importedMedicine : importedMedicines) {
            for (MedicineReminder importedReminder : mMedicineReminderDao
                    .findMedicineRemindersByMedicineId(importedMedicine.id)) {
                if (importedReminder.reminderEnabled != null && importedReminder.reminderEnabled) {
                    importedEnabledReminder = importedReminder;
                } else {
                    importedDisabledReminder = importedReminder;
                }
            }
        }
        assertNotNull(importedEnabledReminder);
        assertNotNull(importedDisabledReminder);
        // relies on Room's clearAllTables not resetting sqlite_sequence (true in Room 2.7.2),
        // so the autoincrement ids of the re-imported rows always differ from the original ones
        assertNotEquals(originalEnabledReminder.id, importedEnabledReminder.id);
        assertNotEquals(originalDisabledReminder.id, importedDisabledReminder.id);

        // assert: only the imported enabled reminder has a scheduled work,
        // the scheduling runs asynchronously on the shared executor after the noteAdded event;
        // exactly one work must be enqueued per enabled reminder,
        // a double-enqueue regression (e.g. a second scheduling event listener) fails here.
        // Every read below runs against a settled pipeline: the counts are polled until
        // stable across two consecutive reads, so a late duplicate enqueue cannot be missed
        WorkManager workManager = WorkManager.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        List<WorkInfo> enabledWorkInfos = awaitWorkInfosByTag(workManager,
                Tags.MEDICINE_REMINDER_TAG + importedEnabledReminder.id);
        assertEquals("exactly one work must be enqueued for the imported enabled reminder",
                1, enabledWorkInfos.size());
        for (WorkInfo workInfo : enabledWorkInfos) {
            assertEquals(WorkInfo.State.ENQUEUED, workInfo.getState());
        }
        assertTrue("imported disabled reminder must not have a scheduled work",
                awaitSettledWorkInfosByTag(workManager, Tags.MEDICINE_REMINDER_TAG
                        + importedDisabledReminder.id).isEmpty());
        assertTrue("original enabled reminder id must not have a scheduled work",
                awaitSettledWorkInfosByTag(workManager, Tags.MEDICINE_REMINDER_TAG
                        + originalEnabledReminder.id).isEmpty());
        assertTrue("original disabled reminder id must not have a scheduled work",
                awaitSettledWorkInfosByTag(workManager, Tags.MEDICINE_REMINDER_TAG
                        + originalDisabledReminder.id).isEmpty());
    }

    @Test
    public void importProfiles_missingSelectedProfileFile_peekStillListsItAndOtherProfileImports()
            throws Exception {
        // arrange: a real export of two profiles, then rebuild the archive without the
        // per-profile entry of profile B. Peek only reads the manifest, so B is still
        // listed, but a selective import of B must fail (its entry file is gone).
        Profile profileA = createProfile(mProfileDao, "Partial File Profile A", "about A");
        createNote(mNoteDao, profileA.id, "note of A");
        Profile profileB = createProfile(mProfileDao, "Partial File Profile B", "about B");
        createNote(mNoteDao, profileB.id, "note of B");
        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);
        String missingEntryName = profileEntryName(profileB.id);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File brokenArchiveFile = new File(context.getCacheDir(), "missing_profile_entry_export.zip");
        mCreatedTempFiles.add(brokenArchiveFile);
        List<String> copiedEntryNames = ExportImportTestUtils.copyZipWithoutEntries(
                exportFile, brokenArchiveFile, missingEntryName);
        assertTrue("the manifest must remain in the rebuilt archive",
                copiedEntryNames.contains(ENTRY_EXPORT_JSON));
        List<String> brokenEntryNames = zipEntryNames(brokenArchiveFile);
        assertFalse("the per-profile entry of B must be missing from the rebuilt archive",
                brokenEntryNames.contains(missingEntryName));
        assertTrue("the per-profile entry of A must remain in the rebuilt archive",
                brokenEntryNames.contains(profileEntryName(profileA.id)));

        // assert: peek reads only the manifest, so it still lists both profiles
        List<ImportArchiveCmd.FileProfile> fileProfiles =
                mImportArchiveCmd.peek(Uri.fromFile(brokenArchiveFile)).blockingGet();
        assertEquals(2, fileProfiles.size());

        // act: wipe the database, then import only profile A whose entry is intact
        mAppDatabase.clearAllTables();
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(Uri.fromFile(brokenArchiveFile),
                        Collections.singletonList(profileA.id)).blockingGet();

        // assert: only profile A was imported, the unselected profile B entry was
        // never needed (and does not even exist in the archive)
        assertEquals(1, importResult.profileCount);
        assertEquals(1, importResult.noteCount);
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(1, profiles.size());
        assertEquals("Partial File Profile A", profiles.get(0).name);

        // assert: importing the profile whose entry is missing fails the whole import
        // with an IOException naming the missing entry
        try {
            mImportArchiveCmd.importProfiles(Uri.fromFile(brokenArchiveFile),
                    Collections.singletonList(profileB.id)).blockingGet();
            fail("Expected the import to fail for the missing profile entry");
        } catch (Exception expected) {
            assertTrue("the failure must be caused by an IOException",
                    isOrCausedByIOException(expected));
            assertTrue("the error must name the missing entry " + missingEntryName,
                    hasIOExceptionWithMessage(expected, missingEntryName));
        }

        // assert: an import of everything (null selection) fails as well,
        // and the failed imports did not commit anything beyond the first import
        try {
            mImportArchiveCmd.importProfiles(Uri.fromFile(brokenArchiveFile), null).blockingGet();
            fail("Expected the import of everything to fail for the missing profile entry");
        } catch (Exception expected) {
            assertTrue("the failure must be caused by an IOException",
                    isOrCausedByIOException(expected));
        }
        assertEquals(1, mProfileDao.findProfiles().size());
    }

    @Test
    public void peekAndImport_manifestEntryWithNonNumericOrNegativeId_fails() throws Exception {
        // arrange: hand-built manifests whose profiles index carries a non-numeric
        // and a negative profile id (defensive against hand-edited archives)
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File nonNumericIdFile = new File(context.getCacheDir(), "non_numeric_id_export.zip");
        mCreatedTempFiles.add(nonNumericIdFile);
        createManifestZip(nonNumericIdFile, ExportArchiveCmd.FORMAT_VERSION,
                createManifestProfileEntries("not-a-number", "Evil Non Numeric"));
        File negativeIdFile = new File(context.getCacheDir(), "negative_id_export.zip");
        mCreatedTempFiles.add(negativeIdFile);
        createManifestZip(negativeIdFile, ExportArchiveCmd.FORMAT_VERSION,
                createManifestProfileEntries(-5, "Evil Negative"));

        // act + assert: both peek and importProfiles reject every malformed id variant,
        // the import side must fail in the manifest id validation, not with any IOException
        Uri nonNumericUri = Uri.fromFile(nonNumericIdFile);
        assertBlockingFails(mImportArchiveCmd.peek(nonNumericUri));
        assertImportFailsWithManifestIdError(mImportArchiveCmd.importProfiles(nonNumericUri, null),
                "missing or non-numeric " + KEY_ID);
        Uri negativeUri = Uri.fromFile(negativeIdFile);
        assertBlockingFails(mImportArchiveCmd.peek(negativeUri));
        assertImportFailsWithManifestIdError(mImportArchiveCmd.importProfiles(negativeUri, null),
                "non-positive " + KEY_ID);
    }

    @Test
    public void importProfiles_duplicateManifestId_importsTheProfileExactlyOnce() throws Exception {
        // arrange: a hand-built archive whose manifest lists the same id twice
        // (defensive against hand-edited archives), backed by one minimal real
        // profiles/1.json entry carrying only the keys the parser reads
        // (missing keys fall back to their defaults)
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File duplicateIdFile = new File(context.getCacheDir(), "duplicate_manifest_id_export.zip");
        mCreatedTempFiles.add(duplicateIdFile);
        JSONObject profileEntryJson = new JSONObject();
        profileEntryJson.put(KEY_ID, 1);
        profileEntryJson.put(KEY_NAME, "Duplicated Manifest Id Profile");
        Map<String, String> textEntriesByName = new LinkedHashMap<>();
        textEntriesByName.put(ENTRY_EXPORT_JSON, createManifestText(
                ExportArchiveCmd.FORMAT_VERSION,
                createManifestProfileEntries(1, "Dup A", 1, "Dup B")));
        textEntriesByName.put(profileEntryName(1), profileEntryJson.toString());
        createTextEntriesZip(duplicateIdFile, textEntriesByName);
        Uri duplicateIdUri = Uri.fromFile(duplicateIdFile);

        // assert: peek reads the manifest verbatim, both duplicated entries are listed
        assertEquals("peek must list both duplicated manifest entries",
                2, mImportArchiveCmd.peek(duplicateIdUri).blockingGet().size());

        // act + assert (sub-case 1): importing everything imports the duplicated id once
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(duplicateIdUri, null).blockingGet();
        assertEquals(1, importResult.profileCount);
        assertEquals(0, importResult.noteCount);
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(1, profiles.size());
        assertEquals("Duplicated Manifest Id Profile", profiles.get(0).name);

        // act + assert (sub-case 2): explicitly selecting the duplicated id twice
        // still imports it exactly once
        mAppDatabase.clearAllTables();
        ImportArchiveCmd.ImportResult selectedImportResult = mImportArchiveCmd
                .importProfiles(duplicateIdUri, Arrays.asList(1L, 1L)).blockingGet();
        assertEquals(1, selectedImportResult.profileCount);
        assertEquals(1, mProfileDao.findProfiles().size());
    }

    @Test
    public void peekAndImport_manifestWithoutUsableProfilesIndex_importsNothing() throws Exception {
        // arrange: hand-built manifests without a usable profiles index.
        // JSONObject.put(key, null) removes the key, so the manifest text is written raw
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String formatVersionJson = "\"" + KEY_FORMAT_VERSION + "\":"
                + ExportArchiveCmd.FORMAT_VERSION;
        List<String> manifestVariants = Arrays.asList(
                // no profiles key at all
                "{" + formatVersionJson + "}",
                // a literal null profiles value
                "{" + formatVersionJson + ",\"" + KEY_PROFILES + "\":null}",
                // an empty profiles array
                "{" + formatVersionJson + ",\"" + KEY_PROFILES + "\":[]}");
        for (int i = 0; i < manifestVariants.size(); i++) {
            File manifestFile = new File(context.getCacheDir(),
                    "manifest_without_profiles_" + i + "_export.zip");
            mCreatedTempFiles.add(manifestFile);
            createTextEntriesZip(manifestFile, Collections.singletonMap(
                    ENTRY_EXPORT_JSON, manifestVariants.get(i)));
            Uri manifestUri = Uri.fromFile(manifestFile);

            // assert: peek reads the unusable profiles index as an empty list
            assertTrue("variant " + i + " must peek as an empty profile list",
                    mImportArchiveCmd.peek(manifestUri).blockingGet().isEmpty());

            // assert: the import succeeds with all counts at zero and commits nothing
            ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                    .importProfiles(manifestUri, null).blockingGet();
            assertEquals(0, importResult.profileCount);
            assertEquals(0, importResult.noteCount);
            assertEquals(0, importResult.tagCount);
            assertEquals(0, importResult.attachmentCount);
            assertEquals(0, importResult.fileCount);
            assertEquals(0, importResult.medicineCount);
            assertEquals(0, importResult.reminderCount);
            assertEquals(0, importResult.intakeCount);
            assertTrue(mProfileDao.findProfiles().isEmpty());
        }
    }

    @Test
    public void importProfiles_unknownSelectedProfileId_importsNothingAndEmitsNoEvents()
            throws Exception {
        // arrange: a real export archive, then a selection naming an id
        // that the manifest does not list
        Profile profile = createProfile(mProfileDao, "Unknown Id Profile", "about");
        createNote(mNoteDao, profile.id, "unknown id note");
        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);

        // wipe the database, subscribe to the note events before importing
        mAppDatabase.clearAllTables();
        TestSubscriber<NoteState> noteAddedSubscriber = new TestSubscriber<>();
        mProvider.get(NoteChangeNotifier.class).getAddedNote().subscribe(noteAddedSubscriber);

        // act
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(Uri.fromFile(exportFile),
                        Collections.singletonList(9999L)).blockingGet();

        // assert: unknown ids are ignored, nothing at all is imported
        assertEquals(0, importResult.profileCount);
        assertEquals(0, importResult.noteCount);
        assertEquals(0, importResult.tagCount);
        assertEquals(0, importResult.attachmentCount);
        assertEquals(0, importResult.fileCount);
        assertEquals(0, importResult.medicineCount);
        assertEquals(0, importResult.reminderCount);
        assertEquals(0, importResult.intakeCount);
        assertTrue(mProfileDao.findProfiles().isEmpty());
        // assert: no noteAdded event (and therefore no reminder scheduling) was emitted
        noteAddedSubscriber.assertValueCount(0);
    }

    @Test
    public void peekAndImport_archiveWithoutExportJsonEntry_fails() throws Exception {
        // arrange: a valid zip archive that carries only a per-profile entry
        // but no export.json manifest entry at all
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File noManifestFile = new File(context.getCacheDir(), "no_export_json_export.zip");
        mCreatedTempFiles.add(noManifestFile);
        createTextEntriesZip(noManifestFile, Collections.singletonMap(
                profileEntryName(1), "{\"" + KEY_ID + "\":1}"));
        Uri noManifestUri = Uri.fromFile(noManifestFile);

        // act + assert: both peek and importProfiles fail with the
        // no-export.json-entry IOException
        assertBlockingFailsWithMessage(mImportArchiveCmd.peek(noManifestUri),
                "has no " + ENTRY_EXPORT_JSON + " entry");
        assertBlockingFailsWithMessage(mImportArchiveCmd.importProfiles(noManifestUri, null),
                "has no " + ENTRY_EXPORT_JSON + " entry");
    }

    @Test
    public void importProfiles_emptySelection_importsEverythingLikeNull() throws Exception {
        // arrange: a real export of two profiles,
        // profile A carries a note with one medicine and one reminder
        Profile profileA = createProfile(mProfileDao, "Empty Selection Profile A", "about A");
        Note noteA = createNote(mNoteDao, profileA.id, "note of A");
        Medicine medicineA = createMedicine(mMedicineDao, noteA.id, "Paracetamol", "500mg");
        createMedicineReminder(mMedicineReminderDao, medicineA.id, "take 1 pill",
                new Date(), true, Calendar.MONDAY);
        Profile profileB = createProfile(mProfileDao, "Empty Selection Profile B", "about B");
        createNote(mNoteDao, profileB.id, "note of B");
        File exportFile = mExportArchiveCmd.export(null).blockingGet();
        mCreatedTempFiles.add(exportFile);

        // act: wipe the database, then import with an explicitly empty selection,
        // which must behave like the null selection (import everything)
        mAppDatabase.clearAllTables();
        ImportArchiveCmd.ImportResult importResult = mImportArchiveCmd
                .importProfiles(Uri.fromFile(exportFile), new ArrayList<>()).blockingGet();

        // assert: both profiles with all of their rows were imported
        assertEquals(2, importResult.profileCount);
        assertEquals(2, importResult.noteCount);
        assertEquals(1, importResult.medicineCount);
        assertEquals(1, importResult.reminderCount);
        List<Profile> profiles = mProfileDao.findProfiles();
        assertEquals(2, profiles.size());
        List<String> importedProfileNames = new ArrayList<>();
        int importedNoteCount = 0;
        for (Profile importedProfile : profiles) {
            importedProfileNames.add(importedProfile.name);
            importedNoteCount += mNoteDao.findNotesByProfileId(importedProfile.id).size();
        }
        assertEquals(2, importedNoteCount);
        assertTrue(importedProfileNames.contains("Empty Selection Profile A"));
        assertTrue(importedProfileNames.contains("Empty Selection Profile B"));
    }

    /**
     * Build a zip archive that contains only an export.json manifest entry
     * with the given format version and manifest profiles index
     * (null writes no format_version key, the profiles index is written as given).
     */
    private static void createManifestZip(File outFile, Integer formatVersion,
                                          JSONArray manifestProfileEntries) throws Exception {
        createTextEntriesZip(outFile, Collections.singletonMap(ENTRY_EXPORT_JSON,
                createManifestText(formatVersion, manifestProfileEntries)));
    }

    /**
     * Build the export.json manifest text with the given format version and
     * manifest profiles index (null writes no format_version key,
     * the profiles index is written as given).
     */
    private static String createManifestText(Integer formatVersion,
                                             JSONArray manifestProfileEntries)
            throws JSONException {
        JSONObject manifest = new JSONObject();
        if (formatVersion != null) {
            manifest.put(KEY_FORMAT_VERSION, formatVersion.intValue());
        }
        manifest.put(KEY_PROFILES, manifestProfileEntries);
        return manifest.toString();
    }

    /**
     * Build a zip archive with the given text entries written in map order
     * (zip entry name -> UTF-8 text content), for hand-built archives whose
     * manifest text must be crafted raw (e.g. a missing or literal null
     * profiles key, which JSONObject.put(key, null) cannot express).
     */
    private static void createTextEntriesZip(File outFile, Map<String, String> textEntriesByName)
            throws Exception {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(outFile))) {
            for (Map.Entry<String, String> textEntry : textEntriesByName.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(textEntry.getKey()));
                zipOutputStream.write(textEntry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
    }

    /**
     * Build a manifest profiles index array with one {id, name} entry
     * carrying the given raw id value (e.g. a String or a negative number
     * to exercise the manifest id validation).
     */
    private static JSONArray createManifestProfileEntries(Object... idNamePairs)
            throws JSONException {
        JSONArray profileArray = new JSONArray();
        for (int i = 0; i < idNamePairs.length; i += 2) {
            JSONObject profileJson = new JSONObject();
            profileJson.put(KEY_ID, idNamePairs[i]);
            profileJson.put(KEY_NAME, idNamePairs[i + 1]);
            profileArray.put(profileJson);
        }
        return profileArray;
    }

    /**
     * Assert that the Single reports an error whose cause chain contains an IOException,
     * the import/peek error contract for every malformed archive case
     * (garbage bytes surface as ZipException, a subclass of IOException,
     * while the format version checks throw IOException directly).
     */
    private static void assertBlockingFails(Single<?> single) {
        try {
            single.blockingGet();
            fail("Expected the Single to report an error");
        } catch (Exception expected) {
            assertNotNull(expected);
            assertTrue("the failure must be caused by an IOException",
                    isOrCausedByIOException(expected));
        }
    }

    /**
     * Assert that the Single reports an error whose cause chain contains an
     * IOException whose message contains the given text.
     */
    private static void assertBlockingFailsWithMessage(Single<?> single, String messagePart) {
        try {
            single.blockingGet();
            fail("Expected the Single to report an error");
        } catch (Exception expected) {
            assertTrue("the failure must be caused by an IOException",
                    isOrCausedByIOException(expected));
            assertTrue("the error must mention " + messagePart,
                    hasIOExceptionWithMessage(expected, messagePart));
        }
    }

    /**
     * Assert that the import Single fails with an IOException whose message
     * contains the given stable fragment of the manifest id validation message
     * of {@link ImportArchiveCmd}, pinning the failure stage of the import to
     * the manifest id validation instead of merely any IOException.
     */
    private static void assertImportFailsWithManifestIdError(Single<?> importSingle,
                                                             String expectedMessage) {
        try {
            importSingle.blockingGet();
            fail("Expected the import to fail with the manifest id validation error");
        } catch (Exception expected) {
            assertTrue("the failure must be caused by an IOException",
                    isOrCausedByIOException(expected));
            assertTrue("the error must be the manifest id validation failure, expected: "
                            + expectedMessage,
                    hasIOExceptionWithMessage(expected, expectedMessage));
        }
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

    private MedicineReminder createEnabledReminder(Long medicineId) {
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.add(Calendar.HOUR_OF_DAY, 1);
        return createMedicineReminder(mMedicineReminderDao, medicineId,
                "take enabled pill", startCalendar.getTime(), true,
                Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
    }

    private MedicineReminder createDisabledReminder(Long medicineId) {
        return createMedicineReminder(mMedicineReminderDao, medicineId,
                "take disabled pill", new Date(), false, Calendar.MONDAY);
    }

    /**
     * Polls the work infos of a reminder tag until the work shows up and its
     * count is stable across two consecutive polls a short interval apart,
     * or the timeout is reached (the scheduling runs asynchronously, a count
     * that only appears on one poll could still be followed by a late
     * duplicate enqueue).
     * The caller must assert on the returned list (e.g. the exact work count).
     */
    private static List<WorkInfo> awaitWorkInfosByTag(WorkManager workManager, String tag)
            throws Exception {
        long deadline = System.currentTimeMillis() + SCHEDULING_TIMEOUT_MILLIS;
        int previousCount = -1;
        int count = workManager.getWorkInfosByTag(tag).get().size();
        while (System.currentTimeMillis() < deadline
                && (count == 0 || count != previousCount)) {
            Thread.sleep(WORK_COUNT_POLL_INTERVAL_MILLIS);
            previousCount = count;
            count = workManager.getWorkInfosByTag(tag).get().size();
        }
        return workManager.getWorkInfosByTag(tag).get();
    }

    /**
     * Polls the work infos of a reminder tag until its count is stable across
     * two consecutive polls a short interval apart, or the timeout is reached.
     * Unlike {@link #awaitWorkInfosByTag} it also settles on an empty pipeline,
     * so a "no work" assertion still runs against a fully settled scheduling
     * state instead of a possibly not-yet-enqueued one.
     */
    private static List<WorkInfo> awaitSettledWorkInfosByTag(WorkManager workManager, String tag)
            throws Exception {
        long deadline = System.currentTimeMillis() + SCHEDULING_TIMEOUT_MILLIS;
        int previousCount = -1;
        int count = workManager.getWorkInfosByTag(tag).get().size();
        while (System.currentTimeMillis() < deadline && count != previousCount) {
            Thread.sleep(WORK_COUNT_POLL_INTERVAL_MILLIS);
            previousCount = count;
            count = workManager.getWorkInfosByTag(tag).get().size();
        }
        return workManager.getWorkInfosByTag(tag).get();
    }

    /**
     * Test-only AppDatabase decorator whose runInTransaction can be armed to fail once,
     * everything else is delegated to the real in-memory database.
     */
    private static final class TransactionFailingAppDatabase extends AppDatabase {
        private final AppDatabase mDelegate;
        private boolean mFailNextTransaction;

        TransactionFailingAppDatabase(AppDatabase delegate) {
            mDelegate = delegate;
        }

        void failNextTransaction() {
            mFailNextTransaction = true;
        }

        @Override
        public void runInTransaction(Runnable body) {
            if (mFailNextTransaction) {
                mFailNextTransaction = false;
                throw new IllegalStateException(
                        "Forced transaction failure (ImportArchiveCmdTest)");
            }
            mDelegate.runInTransaction(body);
        }

        @Override
        public void clearAllTables() {
            mDelegate.clearAllTables();
        }

        @Override
        public void close() {
            mDelegate.close();
        }

        @Override
        protected InvalidationTracker createInvalidationTracker() {
            throw new UnsupportedOperationException(
                    "Test decorator, the delegate database owns the invalidation tracker");
        }

        @Override
        public ProfileDao profileDao() {
            return mDelegate.profileDao();
        }

        @Override
        public NoteDao noteDao() {
            return mDelegate.noteDao();
        }

        @Override
        public NoteAttachmentDao noteAttachmentDao() {
            return mDelegate.noteAttachmentDao();
        }

        @Override
        public NoteTagDao noteTagDao() {
            return mDelegate.noteTagDao();
        }

        @Override
        public NoteAttachmentFileDao noteAttachmentFileDao() {
            return mDelegate.noteAttachmentFileDao();
        }

        @Override
        public MedicineReminderDao medicineReminderDao() {
            return mDelegate.medicineReminderDao();
        }

        @Override
        public MedicineIntakeDao medicineIntakeDao() {
            return mDelegate.medicineIntakeDao();
        }

        @Override
        public MedicineDao medicineDao() {
            return mDelegate.medicineDao();
        }

        @Override
        public AndroidNotificationDao androidNotificationDao() {
            return mDelegate.androidNotificationDao();
        }
    }
}
