package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import m.co.rh.id.a_medic_log.ExportImportTestUtils;
import m.co.rh.id.a_medic_log.app.provider.component.ExcelExporter;
import m.co.rh.id.a_medic_log.app.provider.component.ProfileExportData;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;

@RunWith(AndroidJUnit4.class)
public class ExcelExporterTest {

    /**
     * POI only supports API 26+, the Assume guard makes sure no POI class is ever
     * loaded nor touched on older API levels (the class test must be skipped there).
     */
    @Test
    public void export_writesValidXlsxZipArchive() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // arrange: a small dataset
        Profile profile = new Profile();
        profile.name = "John Doe";
        profile.about = "test about";
        Note note = new Note();
        note.content = "spreadsheet note";
        note.entryDateTime = new Date();
        ProfileExportData exportData = new ProfileExportData(profile,
                Collections.singletonList(new ProfileExportData.NoteExportData(note,
                        new ArrayList<>(), new ArrayList<>(), new ArrayList<>())));

        File outFile = new File(context.getCacheDir(), "excel_export_test.xlsx");
        try {
            // act
            new ExcelExporter(context).export(outFile, exportData);
            assertTrue(outFile.exists());
            assertTrue(outFile.length() > 0);

            // assert: an xlsx file is a zip archive that contains xl/workbook.xml
            ZipEntry workbookEntry = null;
            try (InputStream inputStream = new BufferedInputStream(new FileInputStream(outFile));
                 ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    if ("xl/workbook.xml".equals(zipEntry.getName())) {
                        workbookEntry = zipEntry;
                        break;
                    }
                }
            }
            assertNotNull("xlsx archive must contain xl/workbook.xml entry", workbookEntry);

            // assert: the workbook declares the four sheets of the export,
            // POI writes the sheet names as plain text into xl/workbook.xml
            // (e.g. <sheet name="Profile" sheetId="1" .../>)
            String workbookXml = ExportImportTestUtils.readZipEntryText(outFile, "xl/workbook.xml");
            assertTrue("workbook.xml must declare sheet Profile", workbookXml.contains("Profile"));
            assertTrue("workbook.xml must declare sheet Notes", workbookXml.contains("Notes"));
            assertTrue("workbook.xml must declare sheet Medicines", workbookXml.contains("Medicines"));
            assertTrue("workbook.xml must declare sheet Intakes", workbookXml.contains("Intakes"));
        } finally {
            outFile.delete();
        }
    }
}
