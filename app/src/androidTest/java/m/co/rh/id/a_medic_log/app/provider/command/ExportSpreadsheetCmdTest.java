package m.co.rh.id.a_medic_log.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicine;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createNote;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createProfile;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.listTempArchivePaths;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.zipEntryNames;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.ExportImportTestProviderModule;
import m.co.rh.id.a_medic_log.app.provider.command.ExportSpreadsheetCmd;
import m.co.rh.id.a_medic_log.app.provider.component.CsvArchiveExporter;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class ExportSpreadsheetCmdTest {

    private Provider mProvider;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private MedicineDao mMedicineDao;
    private ExportSpreadsheetCmd mExportSpreadsheetCmd;
    private File mTempFileRoot;
    private final List<File> mCreatedTempFiles = new ArrayList<>();

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new ExportImportTestProviderModule());
        mProfileDao = mProvider.get(ProfileDao.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mExportSpreadsheetCmd = mProvider.get(ExportSpreadsheetCmd.class);
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
    public void export_singleProfile_producesDeviceAppropriateSpreadsheetArchive() throws Exception {
        // arrange
        Profile profile = createProfile(mProfileDao, "Spreadsheet Profile", "about");
        Note note = createNote(mNoteDao, profile.id, "spreadsheet command note");
        createMedicine(mMedicineDao, note.id, "Paracetamol", "500mg");

        // act
        File exportFile = mExportSpreadsheetCmd.export(profile.id).blockingGet();
        mCreatedTempFiles.add(exportFile);

        // assert: valid non-empty archive of the expected format,
        // xlsx on API 26+ and a CSV archive below that
        assertTrue(exportFile.exists());
        assertTrue(exportFile.length() > 0);
        List<String> entryNames = zipEntryNames(exportFile);
        if (isSpreadsheetSupported()) {
            assertTrue("xlsx archive must contain xl/workbook.xml entry",
                    entryNames.contains("xl/workbook.xml"));
        } else {
            assertCsvArchiveEntries(entryNames);
        }
    }

    @Test
    public void exportTo_writesDestArchiveAndLeavesNoTempFileLeftover() throws Exception {
        // arrange
        Profile profile = createProfile(mProfileDao, "Spreadsheet Export To Profile", "about");
        Note note = createNote(mNoteDao, profile.id, "spreadsheet export to note");
        createMedicine(mMedicineDao, note.id, "Ibuprofen", "200mg");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String destFileName = isSpreadsheetSupported()
                ? "spreadsheet_export_to_test.xlsx" : "spreadsheet_export_to_test.zip";
        File destFile = new File(context.getCacheDir(), destFileName);
        mCreatedTempFiles.add(destFile);
        List<String> tempArchivesBefore = listTempArchivePaths(mTempFileRoot);

        // act
        Uri destUri = Uri.fromFile(destFile);
        Uri resultUri = mExportSpreadsheetCmd.exportTo(profile.id, destUri).blockingGet();

        // assert: the archive is written to the destination Uri
        assertEquals(destUri, resultUri);
        assertTrue(destFile.exists());
        assertTrue(destFile.length() > 0);
        List<String> entryNames = zipEntryNames(destFile);
        if (isSpreadsheetSupported()) {
            assertTrue("xlsx archive must contain xl/workbook.xml entry",
                    entryNames.contains("xl/workbook.xml"));
        } else {
            assertCsvArchiveEntries(entryNames);
        }

        // assert: the temporary archive file is deleted, no *.zip/*.xlsx leftover in the temp root
        assertEquals(tempArchivesBefore, listTempArchivePaths(mTempFileRoot));
    }

    /**
     * POI based spreadsheet export only supports API 26+,
     * below that a CSV archive is exported instead
     */
    private static boolean isSpreadsheetSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    private static void assertCsvArchiveEntries(List<String> entryNames) {
        assertTrue(entryNames.contains(CsvArchiveExporter.FILE_PROFILE_CSV));
        assertTrue(entryNames.contains(CsvArchiveExporter.FILE_NOTES_CSV));
        assertTrue(entryNames.contains(CsvArchiveExporter.FILE_MEDICINES_CSV));
        assertTrue(entryNames.contains(CsvArchiveExporter.FILE_INTAKES_CSV));
    }
}
