package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.text.StaticLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.app.provider.command.AdherenceCalculator;
import m.co.rh.id.a_medic_log.app.provider.command.AdherenceReport;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

/**
 * Render-only tests for {@link PdfReportExporter}: the exporter is a pure
 * in-memory renderer, so every export test builds its {@link PdfReportData}
 * fixture directly via the public constructor from hand-made entities, no
 * database involved. The real assembly behavior (profile scoping, ascending
 * intake sort, PRN detection) is covered against the production
 * ExportPdfReportCmd.assembleReportData in ExportPdfReportCmdTest.
 */
@RunWith(AndroidJUnit4.class)
public class PdfReportExporterTest {

    private static final int A4_WIDTH_POINTS = 595;
    private static final int A4_HEIGHT_POINTS = 842;
    private static final int PAGE_DIMENSION_TOLERANCE_POINTS = 1;
    private static final int LONG_HISTORY_INTAKE_COUNT = 300;
    private static final long LONG_HISTORY_SPACING_MILLIS = 6L * 60 * 60 * 1000;

    // paginate geometry: content top is at 40pt, content bottom at 778pt
    // (A4 842pt - 40pt margin - 24pt footer strip). The 730pt filler leaves
    // exactly 8pt: the 4pt section title and the 4pt table header (both
    // keep-with-next) still fit, the 4pt data row overflows.
    private static final float CHAIN_FILLER_HEIGHT = 730f;
    private static final float CHAIN_TITLE_HEIGHT = 4f;
    private static final float CHAIN_HEADER_HEIGHT = 4f;
    private static final float CHAIN_ROW_HEIGHT = 4f;

    private Provider mProvider;
    private final List<File> mCreatedFiles = new ArrayList<>();

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // the exporter only reads the provider's application context, an empty
        // module is enough for this pure renderer test (no db, no WorkManager)
        mProvider = Provider.createProvider(context, (registry, p) -> {
            // leave blank
        });
    }

    @After
    public void cleanup() {
        for (File createdFile : mCreatedFiles) {
            createdFile.delete();
        }
        mCreatedFiles.clear();
        mProvider.dispose();
    }

    @Test
    public void export_writesValidA4PdfFile() throws Exception {
        // arrange: one profile, a scheduled medicine with an enabled reminder
        // and a PRN medicine without any reminder, plus a single intake
        Profile profile = buildProfile("Pdf Report Profile");
        Medicine scheduledMedicine = buildMedicine(1L, "Paracetamol", "500mg");
        Medicine prnMedicine = buildMedicine(2L, "Ibuprofen", "200mg");
        // no entry for prnMedicine: getScheduleText returns null and the
        // exporter renders the localized "As needed (PRN)" label for it
        Map<Long, List<MedicineReminder>> reminderMap = Collections.singletonMap(
                scheduledMedicine.id, Collections.singletonList(
                        buildEnabledReminder(scheduledMedicine.id, "take 1 pill",
                                Calendar.MONDAY, Calendar.TUESDAY)));
        List<MedicineIntake> intakesInRange = Collections.singletonList(
                buildIntake(scheduledMedicine.id, "morning dose", new Date()));
        AdherenceReport adherence7Days = buildAdherenceReport(7,
                scheduledMedicine.id, scheduledMedicine.name);
        AdherenceReport adherence30Days = buildAdherenceReport(30,
                scheduledMedicine.id, scheduledMedicine.name);
        PdfReportData reportData = new PdfReportData(profile,
                Arrays.asList(scheduledMedicine, prnMedicine), reminderMap,
                adherence7Days, adherence30Days, intakesInRange,
                AdherenceCalculator.rangeStart(new Date(), 7),
                AdherenceCalculator.rangeEnd(new Date()));

        File outFile = new File(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir(), "pdf_report_export_test.pdf");
        mCreatedFiles.add(outFile);

        // act
        File result = new PdfReportExporter(mProvider)
                .export(outFile, reportData);

        // assert: the file is a non-trivial PDF
        assertEquals(outFile, result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0);
        assertPdfMagicBytes(result);

        // assert: a renderable document of at least one A4 sized page
        try (ParcelFileDescriptor parcelFileDescriptor =
                     ParcelFileDescriptor.open(result, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer pdfRenderer = new PdfRenderer(parcelFileDescriptor)) {
            assertTrue("PDF must contain at least one page",
                    pdfRenderer.getPageCount() >= 1);
            PdfRenderer.Page page = pdfRenderer.openPage(0);
            assertTrue("page width must be A4 (595pt), was " + page.getWidth(),
                    Math.abs(page.getWidth() - A4_WIDTH_POINTS)
                            <= PAGE_DIMENSION_TOLERANCE_POINTS);
            assertTrue("page height must be A4 (842pt), was " + page.getHeight(),
                    Math.abs(page.getHeight() - A4_HEIGHT_POINTS)
                            <= PAGE_DIMENSION_TOLERANCE_POINTS);
            page.close();
        }
    }

    @Test
    public void export_longHistory_paginatesIntoMultiplePages() throws Exception {
        // arrange: 300 intakes spaced 6 hours apart (75 days, inside the range)
        Profile profile = buildProfile("Pdf Report Long Profile");
        Medicine medicine = buildMedicine(1L, "Ibuprofen", "200mg");
        List<MedicineIntake> intakesInRange = buildAscendingIntakes(medicine.id,
                LONG_HISTORY_INTAKE_COUNT, System.currentTimeMillis(),
                LONG_HISTORY_SPACING_MILLIS);
        PdfReportData reportData = new PdfReportData(profile,
                Collections.singletonList(medicine), Collections.emptyMap(),
                new AdherenceReport(), new AdherenceReport(), intakesInRange,
                AdherenceCalculator.rangeStart(new Date(), 90),
                AdherenceCalculator.rangeEnd(new Date()));

        File outFile = new File(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir(), "pdf_report_long_test.pdf");
        mCreatedFiles.add(outFile);

        // act
        new PdfReportExporter(mProvider).export(outFile, reportData);

        // assert: the intake history appendix must overflow one page
        try (ParcelFileDescriptor parcelFileDescriptor =
                     ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer pdfRenderer = new PdfRenderer(parcelFileDescriptor)) {
            assertTrue("a long intake history must paginate into multiple pages, was "
                    + pdfRenderer.getPageCount(), pdfRenderer.getPageCount() > 1);
        }
    }

    @Test
    public void paginate_sectionTitleAndHeaderChain_moveTogetherToNextPage() {
        // arrange: the dominant layout chain [sectionTitle(keep) ->
        // headerRow(keep) -> dataRow] placed so that title and header land
        // exactly at the content bottom and the first data row overflows.
        // Pulling back only the last element would strand the section title
        // alone at the bottom of page 1, the whole chain must move instead.
        PdfReportExporter.Element filler = new PdfReportExporter.Element(
                CHAIN_FILLER_HEIGHT, false) {
            @Override
            void draw(Canvas canvas) {
                // leave blank
            }
        };
        PdfReportExporter.Element sectionTitle = new PdfReportExporter.Element(
                CHAIN_TITLE_HEIGHT, true) {
            @Override
            void draw(Canvas canvas) {
                // leave blank
            }
        };
        PdfReportExporter.Element tableHeader = new PdfReportExporter.RowElement(
                new StaticLayout[0], new float[0], null, true,
                CHAIN_HEADER_HEIGHT, null);
        PdfReportExporter.Element dataRow = new PdfReportExporter.RowElement(
                new StaticLayout[0], new float[0], tableHeader, false,
                CHAIN_ROW_HEIGHT, null);
        List<PdfReportExporter.Element> elements =
                Arrays.asList(filler, sectionTitle, tableHeader, dataRow);

        // act
        List<PdfReportExporter.PageLayout> pages = PdfReportExporter.paginate(elements);

        // assert: page 1 keeps only the filler, page 2 opens with the whole
        // pulled-back chain in order followed by the overflowing data row
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).mElements.size());
        assertSame(filler, pages.get(0).mElements.get(0).mElement);
        assertEquals(3, pages.get(1).mElements.size());
        assertSame(sectionTitle, pages.get(1).mElements.get(0).mElement);
        assertSame(tableHeader, pages.get(1).mElements.get(1).mElement);
        assertSame(dataRow, pages.get(1).mElements.get(2).mElement);
    }

    // ------------------------------------------------------------------ in-memory fixtures

    /**
     * Build the intake history ascending by takenDateTime, mirroring the
     * ascending sort the production assembly applies before handing the list
     * to the renderer (which draws {@code getIntakesInRange()} as-is).
     */
    private static List<MedicineIntake> buildAscendingIntakes(long medicineId, int count,
                                                              long newestTakenMillis,
                                                              long spacingMillis) {
        List<MedicineIntake> intakes = new ArrayList<>(count);
        long oldestTakenMillis = newestTakenMillis - (long) (count - 1) * spacingMillis;
        for (int i = 0; i < count; i++) {
            intakes.add(buildIntake(medicineId, "history dose " + i,
                    new Date(oldestTakenMillis + (long) i * spacingMillis)));
        }
        return intakes;
    }

    private static Profile buildProfile(String name) {
        Profile profile = new Profile();
        profile.name = name;
        return profile;
    }

    private static Medicine buildMedicine(long id, String name, String description) {
        Medicine medicine = new Medicine();
        medicine.id = id;
        medicine.name = name;
        medicine.description = description;
        return medicine;
    }

    private static MedicineReminder buildEnabledReminder(long medicineId, String message,
                                                         Integer... weekDays) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.medicineId = medicineId;
        medicineReminder.message = message;
        // the entity's default constructor already enables the reminder and
        // starts it now, only the days are made explicit here
        medicineReminder.reminderDays = new LinkedHashSet<>(Arrays.asList(weekDays));
        return medicineReminder;
    }

    private static MedicineIntake buildIntake(long medicineId, String description,
                                              Date takenDateTime) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.description = description;
        medicineIntake.takenDateTime = takenDateTime;
        return medicineIntake;
    }

    private static AdherenceReport buildAdherenceReport(int rangeDays, long medicineId,
                                                        String medicineName) {
        AdherenceReport adherenceReport = new AdherenceReport();
        adherenceReport.rangeDays = rangeDays;
        adherenceReport.totalTaken = 10;
        adherenceReport.totalMissed = 2;
        adherenceReport.totalExtra = 1;
        adherenceReport.adherencePercent = 83;
        AdherenceReport.MedicineAdherence medicineAdherence =
                new AdherenceReport.MedicineAdherence();
        medicineAdherence.medicineId = medicineId;
        medicineAdherence.medicineName = medicineName;
        medicineAdherence.taken = 10;
        medicineAdherence.missed = 2;
        medicineAdherence.extra = 1;
        medicineAdherence.adherencePercent = 83;
        adherenceReport.perMedicine.add(medicineAdherence);
        return adherenceReport;
    }

    private static void assertPdfMagicBytes(File file) throws Exception {
        byte[] magic = new byte[4];
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            int read = fileInputStream.read(magic);
            assertTrue("file too small to be a PDF", read == 4);
        }
        assertEquals("file must start with the PDF magic bytes",
                "%PDF", new String(magic, "US-ASCII"));
    }
}
