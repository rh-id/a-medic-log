package m.co.rh.id.a_medic_log.app.provider.component;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;

/**
 * Exports a single profile's data into a ZIP archive of 4 CSV files:
 * profile.csv, notes.csv, medicines.csv and intakes.csv.
 * Columns mirror the sheets of {@link ExcelExporter}, works on all API levels.
 */
public class CsvArchiveExporter {
    public static final String FILE_PROFILE_CSV = "profile.csv";
    public static final String FILE_NOTES_CSV = "notes.csv";
    public static final String FILE_MEDICINES_CSV = "medicines.csv";
    public static final String FILE_INTAKES_CSV = "intakes.csv";

    private static final String HEADER_ENTRY_DATE = "Entry date";
    private static final String HEADER_CONTENT = "Content";
    private static final String HEADER_TAGS = "Tags";
    private static final String HEADER_NAME = "Name";
    private static final String HEADER_DESCRIPTION = "Description";
    private static final String HEADER_NOTE_ENTRY_DATE = "Note entry date";
    private static final String HEADER_REMINDERS = "Reminders";
    private static final String HEADER_MEDICINE = "Medicine";
    private static final String HEADER_TAKEN_DATE_TIME = "Taken date-time";

    private static final String LABEL_GENERATED = "Generated";
    private static final String CSV_FIELD_SEPARATOR = ",";
    private static final String CSV_LINE_SEPARATOR = "\r\n";
    private static final String REMINDER_SEPARATOR = "; ";
    // BOM prepended to each CSV for desktop Excel UTF-8 detection
    private static final String CSV_UTF8_BOM = "\uFEFF";

    /**
     * Write the export data of a profile into a ZIP archive of CSV files.
     *
     * @param outFile    the file to write the archive to
     * @param exportData the assembled export data of a single profile
     * @return the written file
     * @throws IOException when failed to build or write the archive
     */
    public File export(File outFile, ProfileExportData exportData) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(outFile)))) {
            writeProfileCsv(zipOutputStream, exportData);
            writeNotesCsv(zipOutputStream, exportData);
            writeMedicinesCsv(zipOutputStream, exportData);
            writeIntakesCsv(zipOutputStream, exportData);
        }
        return outFile;
    }

    private void writeProfileCsv(ZipOutputStream zipOutputStream, ProfileExportData exportData)
            throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(FILE_PROFILE_CSV));
        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, HEADER_NAME, exportData.getProfile().name);
        appendCsvLine(csv, HEADER_DESCRIPTION, exportData.getProfile().about);
        appendCsvLine(csv, LABEL_GENERATED, ProfileExportData.formatIsoDateTime(new Date()));
        writeCsvEntry(zipOutputStream, csv);
    }

    private void writeNotesCsv(ZipOutputStream zipOutputStream, ProfileExportData exportData)
            throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(FILE_NOTES_CSV));
        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, HEADER_ENTRY_DATE, HEADER_CONTENT, HEADER_TAGS);
        for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
            appendCsvLine(csv,
                    ProfileExportData.formatIsoDateTime(noteExportData.getNote().entryDateTime),
                    noteExportData.getNote().content,
                    joinTags(noteExportData.getTags()));
        }
        writeCsvEntry(zipOutputStream, csv);
    }

    private void writeMedicinesCsv(ZipOutputStream zipOutputStream, ProfileExportData exportData)
            throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(FILE_MEDICINES_CSV));
        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, HEADER_NAME, HEADER_DESCRIPTION, HEADER_NOTE_ENTRY_DATE, HEADER_REMINDERS);
        for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
            for (ProfileExportData.MedicineExportData medicineExportData : noteExportData.getMedicines()) {
                appendCsvLine(csv,
                        medicineExportData.getMedicine().name,
                        medicineExportData.getMedicine().description,
                        ProfileExportData.formatIsoDateTime(noteExportData.getNote().entryDateTime),
                        joinReminders(medicineExportData.getReminders()));
            }
        }
        writeCsvEntry(zipOutputStream, csv);
    }

    private void writeIntakesCsv(ZipOutputStream zipOutputStream, ProfileExportData exportData)
            throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(FILE_INTAKES_CSV));
        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, HEADER_MEDICINE, HEADER_TAKEN_DATE_TIME, HEADER_DESCRIPTION);
        for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
            for (ProfileExportData.MedicineExportData medicineExportData : noteExportData.getMedicines()) {
                for (MedicineIntake medicineIntake : medicineExportData.getIntakes()) {
                    appendCsvLine(csv,
                            medicineExportData.getMedicine().name,
                            ProfileExportData.formatIsoDateTime(medicineIntake.takenDateTime),
                            medicineIntake.description);
                }
            }
        }
        writeCsvEntry(zipOutputStream, csv);
    }

    private void writeCsvEntry(ZipOutputStream zipOutputStream, StringBuilder csv) throws IOException {
        // prepend the BOM once per CSV stream, before the header
        csv.insert(0, CSV_UTF8_BOM);
        zipOutputStream.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    /**
     * Append a single RFC-4180 escaped CSV line, fields are separated by comma
     * and the line is ended with CRLF.
     */
    private static void appendCsvLine(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(CSV_FIELD_SEPARATOR);
            }
            csv.append(escapeCsvField(fields[i]));
        }
        csv.append(CSV_LINE_SEPARATOR);
    }

    /**
     * Escape a single CSV field per RFC-4180,
     * fields containing comma, quote or newline are quoted and inner quotes are doubled.
     */
    private static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsEscaping = value.indexOf(',') != -1
                || value.indexOf('"') != -1
                || value.indexOf('\n') != -1
                || value.indexOf('\r') != -1;
        if (!needsEscaping) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String joinTags(List<NoteTag> noteTags) {
        if (noteTags == null || noteTags.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (NoteTag noteTag : noteTags) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(noteTag.tag);
        }
        return stringBuilder.toString();
    }

    private static String joinReminders(List<MedicineReminder> medicineReminders) {
        if (medicineReminders == null || medicineReminders.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (MedicineReminder medicineReminder : medicineReminders) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(REMINDER_SEPARATOR);
            }
            stringBuilder.append(ProfileExportData.buildReminderSummary(medicineReminder));
        }
        return stringBuilder.toString();
    }
}
