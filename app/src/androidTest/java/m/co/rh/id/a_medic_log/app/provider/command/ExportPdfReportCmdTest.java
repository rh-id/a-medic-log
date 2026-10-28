package m.co.rh.id.a_medic_log.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicine;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createMedicineReminder;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createNote;
import static m.co.rh.id.a_medic_log.ExportImportTestUtils.createProfile;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.ExportImportTestProviderModule;
import m.co.rh.id.a_medic_log.app.provider.component.PdfReportData;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class ExportPdfReportCmdTest {

    private Provider mProvider;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private MedicineDao mMedicineDao;
    private MedicineReminderDao mMedicineReminderDao;
    private MedicineIntakeDao mMedicineIntakeDao;
    private ExportPdfReportCmd mExportPdfReportCmd;
    private final List<File> mCreatedTempFiles = new ArrayList<>();

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new ExportImportTestProviderModule());
        mProfileDao = mProvider.get(ProfileDao.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mMedicineReminderDao = mProvider.get(MedicineReminderDao.class);
        mMedicineIntakeDao = mProvider.get(MedicineIntakeDao.class);
        mExportPdfReportCmd = mProvider.get(ExportPdfReportCmd.class);
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
    public void execute_createsNonEmptyPdfFile() throws Exception {
        // arrange
        Profile profile = createProfile(mProfileDao, "Pdf Command Profile", "about");
        Note note = createNote(mNoteDao, profile.id, "pdf command note");
        Medicine medicine = createMedicine(mMedicineDao, note.id, "Paracetamol", "500mg");
        createMedicineReminder(mMedicineReminderDao, medicine.id, "take 1 pill",
                new Date(), true, Calendar.MONDAY);

        // act
        File reportFile = mExportPdfReportCmd.execute(profile.id, 30).blockingGet();
        mCreatedTempFiles.add(reportFile);

        // assert: a non-empty PDF of the fixed timestamped file name pattern
        assertTrue(reportFile.exists());
        assertTrue(reportFile.length() > 0);
        assertTrue("file name must be medication-report-<timestamp>.pdf, was "
                + reportFile.getName(), reportFile.getName().startsWith("medication-report-"));
        assertTrue(reportFile.getName().endsWith(".pdf"));
        byte[] magic = new byte[4];
        try (FileInputStream fileInputStream = new FileInputStream(reportFile)) {
            assertTrue("file too small to be a PDF", fileInputStream.read(magic) == 4);
        }
        assertTrue("file must start with the PDF magic bytes",
                "%PDF".equals(new String(magic, "US-ASCII")));
    }

    @Test
    public void execute_secondRun_deletesPreviousReportAndItsParentDir() throws Exception {
        // arrange
        Profile profile = createProfile(mProfileDao, "Pdf Cleanup Profile", "about");
        Note note = createNote(mNoteDao, profile.id, "pdf cleanup note");
        createMedicine(mMedicineDao, note.id, "Ibuprofen", "200mg");

        // act: generate two reports back to back
        File firstFile = mExportPdfReportCmd.execute(profile.id, 7).blockingGet();
        File secondFile = mExportPdfReportCmd.execute(profile.id, 7).blockingGet();
        mCreatedTempFiles.add(secondFile);

        // assert: the first report file and its now-empty <uuid> temp
        // directory are deleted, the second one is present
        File firstParent = firstFile.getParentFile();
        assertFalse("the previous report file must be deleted", firstFile.exists());
        assertNotNull(firstParent);
        assertFalse("the previous report's temp directory must be deleted",
                firstParent.exists());
        assertTrue("the new report file must exist", secondFile.exists());
        assertNotNull(secondFile.getParentFile());
        assertFalse("each report must live in its own temp directory",
                secondFile.getParentFile().equals(firstParent));
    }

    @Test
    public void getScheduleText_noEnabledReminder_returnsNullPrnPath() throws Exception {
        // arrange: one medicine without any reminder and one with a disabled reminder
        Profile profile = createProfile(mProfileDao, "Pdf Prn Profile", "about");
        Note note = createNote(mNoteDao, profile.id, "pdf prn note");
        Medicine medicineWithoutReminder = createMedicine(mMedicineDao, note.id,
                "PRN Medicine", "on demand");
        Medicine medicineWithDisabledReminder = createMedicine(mMedicineDao, note.id,
                "Paused Medicine", "reminder disabled");
        createMedicineReminder(mMedicineReminderDao, medicineWithDisabledReminder.id,
                "take 1 pill", new Date(), false, Calendar.MONDAY);
        Medicine medicineWithEnabledReminder = createMedicine(mMedicineDao, note.id,
                "Scheduled Medicine", "500mg");
        createMedicineReminder(mMedicineReminderDao, medicineWithEnabledReminder.id,
                "take 1 pill", new Date(), true, Calendar.MONDAY);
        PdfReportData reportData = mExportPdfReportCmd.assembleReportData(profile,
                new AdherenceReport(), new AdherenceReport(),
                AdherenceCalculator.rangeStart(new Date(), 7),
                AdherenceCalculator.rangeEnd(new Date()));

        // assert: no enabled reminder means no schedule text (the exporter
        // renders the localized "As needed (PRN)" label for null)
        assertNull(reportData.getScheduleText(medicineWithoutReminder.id));
        assertNull(reportData.getScheduleText(medicineWithDisabledReminder.id));
        assertNotNull(reportData.getScheduleText(medicineWithEnabledReminder.id));
    }

    @Test
    public void assemble_intakesOfOtherProfilesMedicines_neverInRange() throws Exception {
        // arrange: two profiles, each with its own note and medicine, an
        // intake for each medicine inside the range
        Profile profileA = createProfile(mProfileDao, "Pdf Scope Profile A", "about");
        Profile profileB = createProfile(mProfileDao, "Pdf Scope Profile B", "about");
        Note noteA = createNote(mNoteDao, profileA.id, "pdf scope note A");
        Note noteB = createNote(mNoteDao, profileB.id, "pdf scope note B");
        Medicine medicineA = createMedicine(mMedicineDao, noteA.id, "Medicine A", "desc");
        Medicine medicineB = createMedicine(mMedicineDao, noteB.id, "Medicine B", "desc");
        Date withinRange = new Date();
        insertMedicineIntake(medicineA.id, "own profile intake", withinRange);
        insertMedicineIntake(medicineB.id, "other profile intake", withinRange);
        long rangeStart = AdherenceCalculator.rangeStart(withinRange, 30);
        long rangeEnd = AdherenceCalculator.rangeEnd(withinRange);

        // act
        PdfReportData reportDataA = mExportPdfReportCmd.assembleReportData(profileA,
                new AdherenceReport(), new AdherenceReport(), rangeStart, rangeEnd);

        // assert: the global between-dates fetch is scoped in memory to the
        // profile's own medicines, the other profile's intake must not appear
        assertEquals(1, reportDataA.getIntakesInRange().size());
        assertEquals(medicineA.id, reportDataA.getIntakesInRange().get(0).medicineId);

        PdfReportData reportDataB = mExportPdfReportCmd.assembleReportData(profileB,
                new AdherenceReport(), new AdherenceReport(), rangeStart, rangeEnd);
        assertEquals(1, reportDataB.getIntakesInRange().size());
        assertEquals(medicineB.id, reportDataB.getIntakesInRange().get(0).medicineId);
    }

    private void insertMedicineIntake(Long medicineId, String description, Date takenDateTime) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.description = description;
        medicineIntake.takenDateTime = takenDateTime;
        mMedicineIntakeDao.insert(medicineIntake);
    }
}
