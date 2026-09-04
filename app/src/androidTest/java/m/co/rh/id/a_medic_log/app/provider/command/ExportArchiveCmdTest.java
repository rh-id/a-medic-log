package m.co.rh.id.a_medic_log.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static m.co.rh.id.a_medic_log.ExportImportTestUtils.ENTRY_EXPORT_JSON;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicine;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicineReminder;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createNote;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createProfile;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.isOrCausedByIOException;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.listTempArchivePaths;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.profileEntryName;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.readZipEntryText;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.zipEntryNames;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

import m.co.rh.id.a_medic_log.ExportImportTestProviderModule;
import m.co.rh.id.a_medic_log.app.provider.command.ExportArchiveCmd;
import m.co.rh.id.a_medic_log.app.provider.component.ProfileExportData;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class ExportArchiveCmdTest {

    private Provider mProvider;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private NoteAttachmentDao mNoteAttachmentDao;
    private MedicineDao mMedicineDao;
    private MedicineReminderDao mMedicineReminderDao;
    private ExportArchiveCmd mExportArchiveCmd;
    private File mTempFileRoot;
    private final List<File> mCreatedTempFiles = new ArrayList<>();

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new ExportImportTestProviderModule());
        mProfileDao = mProvider.get(ProfileDao.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mNoteAttachmentDao = mProvider.get(NoteAttachmentDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mMedicineReminderDao = mProvider.get(MedicineReminderDao.class);
        mExportArchiveCmd = mProvider.get(ExportArchiveCmd.class);
        // mirrors the temp root of FileHelper (cache/tmp)
        mTempFileRoot = new File(context.getCacheDir(), "tmp");
    }

    @After
    public void cleanup() {
        for (File tempFile : mCreatedTempFiles) {
            tempFile.delete();
        }
        mCreatedTempFiles.clear();
        mProvider.dispose();
    }

    @Test
    public void exportTo_writesValidExportZipToDestUriAndLeavesNoTempFileLeftover() throws Exception {
        // arrange: a profile with two notes, one medicine + reminder per note and
        // one attachment (without file rows, the counts are attachment based),
        // so the manifest content counts are non-trivial
        Profile profile = createProfile(mProfileDao, "Export To Profile", "about");
        Note note1 = createNote(mNoteDao, profile.id, "export to destination note");
        Note note2 = createNote(mNoteDao, profile.id, "second note of the export fixture");
        Medicine medicine1 = createMedicine(mMedicineDao, note1.id, "Paracetamol", "500mg");
        createMedicineReminder(mMedicineReminderDao, medicine1.id, "take 1 pill",
                new Date(), true, Calendar.MONDAY);
        Medicine medicine2 = createMedicine(mMedicineDao, note2.id, "Ibuprofen", "200mg");
        createMedicineReminder(mMedicineReminderDao, medicine2.id, "take 1 pill",
                new Date(), true, Calendar.FRIDAY);
        NoteAttachment attachment = new NoteAttachment();
        attachment.noteId = note1.id;
        attachment.name = "photo";
        attachment.id = mNoteAttachmentDao.insert(attachment);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File destFile = new File(context.getCacheDir(), "export_to_destination_test.zip");
        mCreatedTempFiles.add(destFile);
        List<String> tempArchivesBefore = listTempArchivePaths(mTempFileRoot);

        // act
        Uri destUri = Uri.fromFile(destFile);
        Uri resultUri = mExportArchiveCmd.exportTo(null, destUri).blockingGet();

        // assert: the destination Uri is returned and the archive is written there
        assertEquals(destUri, resultUri);
        assertTrue(destFile.exists());
        assertTrue(destFile.length() > 0);

        // assert: the new archive layout, a manifest plus one entry per profile
        List<String> entryNames = zipEntryNames(destFile);
        assertTrue("export archive must contain the export.json manifest entry",
                entryNames.contains(ENTRY_EXPORT_JSON));
        assertTrue("export archive must contain the per-profile entry of the exported profile",
                entryNames.contains(profileEntryName(profile.id)));
        JSONObject manifest = new JSONObject(readZipEntryText(destFile, ENTRY_EXPORT_JSON));
        assertEquals(ExportArchiveCmd.FORMAT_VERSION,
                manifest.getInt("format_version"));
        JSONArray manifestProfiles = manifest.getJSONArray("profiles");
        assertEquals(1, manifestProfiles.length());
        JSONObject manifestProfile = manifestProfiles.getJSONObject(0);
        assertEquals(profile.id.longValue(), manifestProfile.getLong("id"));
        assertEquals("Export To Profile", manifestProfile.getString("name"));
        // the manifest carries id + name + the four content counts,
        // the profile tree lives in its own entry.
        // The key-count assertion below intentionally pins the manifest
        // profile-entry schema (id + name + the four content counts), so a
        // future key addition must consciously update this assertion instead
        // of silently changing the manifest schema
        assertEquals(6, manifestProfile.length());
        assertEquals(2, manifestProfile.getInt("note_count"));
        assertEquals(2, manifestProfile.getInt("medicine_count"));
        assertEquals(2, manifestProfile.getInt("reminder_count"));
        assertEquals(1, manifestProfile.getInt("attachment_count"));
        assertTrue(readZipEntryText(destFile, profileEntryName(profile.id))
                .contains("export to destination note"));

        // assert: the temporary export file is deleted, no *.zip/*.xlsx leftover in the temp root
        assertEquals(tempArchivesBefore, listTempArchivePaths(mTempFileRoot));
    }

    @Test
    public void exportTo_unwritableDestUri_failsAndLeavesNoTempFileLeftover() {
        // arrange
        Profile profile = createProfile(mProfileDao, "Export Fail Profile", "about");
        createNote(mNoteDao, profile.id, "export fail note");
        Uri unwritableUri = Uri.parse("content://m.co.rh.id.a_medic_log.nonexistent/nope");
        List<String> tempArchivesBefore = listTempArchivePaths(mTempFileRoot);

        // act + assert: the Single reports an error instead of returning a result
        try {
            mExportArchiveCmd.exportTo(null, unwritableUri).blockingGet();
            fail("Expected exportTo to fail for the unwritable destination Uri");
        } catch (Exception expected) {
            assertNotNull(expected);
            assertTrue("the failure must be caused by an IOException",
                    isOrCausedByIOException(expected));
        }

        // assert: no *.zip/*.xlsx leftover in the temp root
        assertEquals(tempArchivesBefore, listTempArchivePaths(mTempFileRoot));
    }

    /**
     * Tests the export.json manifest content counts written by
     * {@link ExportArchiveCmd#writeManifest}, verified against the in-memory
     * {@link ExportArchiveCmd.ContentCounts} computation.
     * <p>
     * The shared-medicine dedupe rule cannot be expressed through the database
     * fixture builders (Medicine carries a single noteId column, so one medicine
     * row can never appear under two notes), therefore the counting is exercised
     * with a directly assembled in-memory ProfileExportData.
     */
    @Test
    public void counts_deduplicateSharedMedicineIdsAcrossNotes() {
        // the same medicine (id 100) appears under both notes,
        // its reminders must be counted exactly once
        Medicine sharedMedicine = inMemoryMedicine(100L, "Shared Med");
        List<MedicineReminder> sharedReminders = new ArrayList<>();
        sharedReminders.add(inMemoryReminder(200L, sharedMedicine.id));
        sharedReminders.add(inMemoryReminder(201L, sharedMedicine.id));
        // a medicine that exists only under note A
        Medicine ownMedicine = inMemoryMedicine(101L, "Own Med");
        List<MedicineReminder> ownReminders = new ArrayList<>();
        ownReminders.add(inMemoryReminder(202L, ownMedicine.id));
        // a null medicine id counts as its own entry only once,
        // the reminders of the second null id entry add nothing
        Medicine nullIdMedicineA = inMemoryMedicine(null, "Null Id A");
        Medicine nullIdMedicineB = inMemoryMedicine(null, "Null Id B");
        List<MedicineReminder> nullIdReminders = new ArrayList<>();
        nullIdReminders.add(inMemoryReminder(203L, nullIdMedicineB.id));

        Note noteA = inMemoryNote(10L);
        Note noteB = inMemoryNote(11L);

        ProfileExportData.NoteExportData noteExportA = new ProfileExportData.NoteExportData(
                noteA, Collections.emptyList(),
                Collections.singletonList(inMemoryAttachment(300L, noteA.id)),
                Arrays.asList(
                        new ProfileExportData.MedicineExportData(sharedMedicine,
                                sharedReminders, Collections.emptyList()),
                        new ProfileExportData.MedicineExportData(ownMedicine,
                                ownReminders, Collections.emptyList()),
                        new ProfileExportData.MedicineExportData(nullIdMedicineA,
                                Collections.emptyList(), Collections.emptyList())));
        ProfileExportData.NoteExportData noteExportB = new ProfileExportData.NoteExportData(
                noteB, Collections.emptyList(),
                Arrays.asList(inMemoryAttachment(301L, noteB.id), inMemoryAttachment(302L, noteB.id)),
                Arrays.asList(
                        new ProfileExportData.MedicineExportData(sharedMedicine,
                                sharedReminders, Collections.emptyList()),
                        new ProfileExportData.MedicineExportData(nullIdMedicineB,
                                nullIdReminders, Collections.emptyList())));

        Profile profile = new Profile();
        profile.id = 1L;
        profile.name = "Count Profile";
        ProfileExportData exportData = new ProfileExportData(profile,
                Arrays.asList(noteExportA, noteExportB));

        // act
        ExportArchiveCmd.ContentCounts contentCounts = new ExportArchiveCmd.ContentCounts(exportData);

        // assert: 2 notes, attachments counted per note (never per file entry)
        assertEquals(2, contentCounts.mNoteCount);
        assertEquals(3, contentCounts.mAttachmentCount);
        // assert: distinct medicine ids only, 100 + 101 + the single null entry
        assertEquals(3, contentCounts.mMedicineCount);
        // assert: reminders summed over the distinct medicines only,
        // 2 (shared, counted once) + 1 (own) + 0 (first null entry),
        // the duplicated shared entry and the second null entry add nothing
        assertEquals(3, contentCounts.mReminderCount);
    }

    private static Medicine inMemoryMedicine(Long id, String name) {
        Medicine medicine = new Medicine();
        medicine.id = id;
        medicine.noteId = 10L;
        medicine.name = name;
        return medicine;
    }

    private static MedicineReminder inMemoryReminder(Long id, Long medicineId) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.id = id;
        medicineReminder.medicineId = medicineId;
        medicineReminder.reminderDays = new LinkedHashSet<>();
        return medicineReminder;
    }

    private static Note inMemoryNote(Long id) {
        Note note = new Note();
        note.id = id;
        note.profileId = 1L;
        return note;
    }

    private static ProfileExportData.NoteAttachmentExportData inMemoryAttachment(Long id, Long noteId) {
        NoteAttachment noteAttachment = new NoteAttachment();
        noteAttachment.id = id;
        noteAttachment.noteId = noteId;
        noteAttachment.name = "photo";
        return new ProfileExportData.NoteAttachmentExportData(noteAttachment,
                Collections.emptyList());
    }
}
