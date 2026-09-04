package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import m.co.rh.id.a_medic_log.app.provider.component.CsvArchiveExporter;
import m.co.rh.id.a_medic_log.app.provider.component.ProfileExportData;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;

@RunWith(AndroidJUnit4.class)
public class CsvArchiveExporterTest {

    @Test
    public void export_createsZipWithFourCsvEntries_headersAndRfc4180Escaping() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // arrange: a small dataset, the note content contains comma, quote and newline
        Profile profile = new Profile();
        profile.name = "John Doe";
        profile.about = "test about";
        Note note = new Note();
        note.content = "He said \"hello\", then left\r\nnext line, with comma";
        note.entryDateTime = new Date(1693598400000L);
        NoteTag noteTag = new NoteTag();
        noteTag.tag = "fever";
        List<NoteTag> tags = Collections.singletonList(noteTag);
        List<ProfileExportData.NoteAttachmentExportData> attachments = new ArrayList<>();
        Medicine medicine = new Medicine();
        medicine.name = "Paracetamol";
        medicine.description = "500mg, take with water";
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.message = "take 1 pill";
        medicineReminder.startDateTime = new Date(1693598400000L);
        medicineReminder.reminderDays = new LinkedHashSet<>();
        medicineReminder.reminderDays.add(Calendar.MONDAY);
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.description = "took 10ml, felt better";
        medicineIntake.takenDateTime = new Date(1693598400000L);
        ProfileExportData.MedicineExportData medicineExportData =
                new ProfileExportData.MedicineExportData(medicine,
                        Collections.singletonList(medicineReminder),
                        Collections.singletonList(medicineIntake));
        ProfileExportData exportData = new ProfileExportData(profile,
                Collections.singletonList(new ProfileExportData.NoteExportData(note, tags,
                        attachments, Collections.singletonList(medicineExportData))));

        File outFile = new File(context.getCacheDir(), "csv_archive_test.zip");
        try {
            // act
            new CsvArchiveExporter().export(outFile, exportData);
            assertTrue(outFile.exists());

            // assert: exactly the 4 expected CSV entries exist inside the archive
            List<ZipEntryContent> entries = readZipEntries(outFile);
            assertEquals(4, entries.size());
            ZipEntryContent profileCsv = findEntry(entries, CsvArchiveExporter.FILE_PROFILE_CSV);
            ZipEntryContent notesCsv = findEntry(entries, CsvArchiveExporter.FILE_NOTES_CSV);
            ZipEntryContent medicinesCsv = findEntry(entries, CsvArchiveExporter.FILE_MEDICINES_CSV);
            ZipEntryContent intakesCsv = findEntry(entries, CsvArchiveExporter.FILE_INTAKES_CSV);

            // assert: header rows (each CSV starts with a UTF-8 BOM for Excel detection)
            assertTrue(profileCsv.content.startsWith("\uFEFFName,John Doe\r\n"));
            assertTrue(profileCsv.content.contains("Description,test about\r\n"));
            assertTrue(notesCsv.content.startsWith("\uFEFFEntry date,Content,Tags\r\n"));
            assertTrue(medicinesCsv.content.startsWith("\uFEFFName,Description,Note entry date,Reminders\r\n"));
            assertTrue(intakesCsv.content.startsWith("\uFEFFMedicine,Taken date-time,Description\r\n"));

            // assert: RFC-4180 escaping, a field with comma/quote/newline is quoted
            // with doubled inner quotes and the raw newlines are kept inside the quotes
            String escapedContent = "\"He said \"\"hello\"\", then left\r\n"
                    + "next line, with comma\"";
            assertTrue(notesCsv.content.contains(escapedContent));
            assertTrue(notesCsv.content.endsWith(",fever\r\n"));

            // assert: escaped medicine description and intake description
            assertTrue(medicinesCsv.content.contains("Paracetamol,\"500mg, take with water\""));
            assertTrue(intakesCsv.content.contains("\"took 10ml, felt better\""));
            assertTrue(medicinesCsv.content.contains("take 1 pill"));
        } finally {
            outFile.delete();
        }
    }

    private static List<ZipEntryContent> readZipEntries(File zipFile) throws IOException {
        List<ZipEntryContent> entries = new ArrayList<>();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(zipFile));
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                entries.add(new ZipEntryContent(zipEntry.getName(),
                        new String(readAllBytes(zipInputStream), StandardCharsets.UTF_8)));
            }
        }
        return entries;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buff = new byte[2048];
        int b = inputStream.read(buff);
        while (b != -1) {
            byteArrayOutputStream.write(buff, 0, b);
            b = inputStream.read(buff);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static ZipEntryContent findEntry(List<ZipEntryContent> entries, String name) {
        for (ZipEntryContent entry : entries) {
            if (entry.name.equals(name)) {
                return entry;
            }
        }
        return null;
    }

    private static class ZipEntryContent {
        final String name;
        final String content;

        ZipEntryContent(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }
}
