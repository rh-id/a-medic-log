package m.co.rh.id.a_medic_log.app.provider.component;

import android.content.Context;

import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Cell;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.CellStyle;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Font;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Row;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Sheet;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.apoi_spreadsheet.base.POISpreadsheetContext;

/**
 * Exports a single profile's data into an XLSX spreadsheet with 4 sheets:
 * Profile, Notes, Medicines and Intakes.
 * NOTE: this class uses Apache POI which only supports API 26+,
 * callers must guard every use of this class with
 * Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
 */
public class ExcelExporter {
    private static final String SHEET_PROFILE = "Profile";
    private static final String SHEET_NOTES = "Notes";
    private static final String SHEET_MEDICINES = "Medicines";
    private static final String SHEET_INTAKES = "Intakes";

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
    private static final String REMINDER_SEPARATOR = "; ";

    // column widths in 1/256 of a character width
    private static final int COLUMN_WIDTH_NARROW = 20 * 256;
    private static final int COLUMN_WIDTH_WIDE = 60 * 256;

    private final Context mAppContext;

    public ExcelExporter(Context appContext) {
        mAppContext = appContext;
    }

    /**
     * Write the export data of a profile into an XLSX spreadsheet.
     * All POI operations are executed through POISpreadsheetContext's own executor.
     *
     * @param outFile    the file to write the spreadsheet to
     * @param exportData the assembled export data of a single profile
     * @return the written file
     * @throws Exception when failed to build or write the spreadsheet
     */
    public File export(File outFile, ProfileExportData exportData) throws Exception {
        POISpreadsheetContext poiSpreadsheetContext = POISpreadsheetContext.getInstance();
        poiSpreadsheetContext.setAppContext(mAppContext);
        Future<File> future = poiSpreadsheetContext.submitAndWait(() ->
                writeWorkbook(outFile, exportData));
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IOException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while exporting spreadsheet", e);
        }
    }

    private File writeWorkbook(File outFile, ProfileExportData exportData) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fileOutputStream = new FileOutputStream(outFile)) {
            CellStyle headerCellStyle = createHeaderCellStyle(workbook);
            writeProfileSheet(workbook, headerCellStyle, exportData);
            writeNotesSheet(workbook, headerCellStyle, exportData);
            writeMedicinesSheet(workbook, headerCellStyle, exportData);
            writeIntakesSheet(workbook, headerCellStyle, exportData);
            workbook.write(fileOutputStream);
        }
        return outFile;
    }

    private static void writeProfileSheet(XSSFWorkbook workbook, CellStyle headerCellStyle,
                                          ProfileExportData exportData) {
        Sheet sheet = workbook.createSheet(SHEET_PROFILE);
        sheet.setColumnWidth(0, COLUMN_WIDTH_NARROW);
        sheet.setColumnWidth(1, COLUMN_WIDTH_WIDE);
        int rowIndex = 0;
        Row row = sheet.createRow(rowIndex++);
        createCell(row, 0, HEADER_NAME, headerCellStyle);
        createCell(row, 1, exportData.getProfile().name, null);
        row = sheet.createRow(rowIndex++);
        createCell(row, 0, HEADER_DESCRIPTION, headerCellStyle);
        createCell(row, 1, exportData.getProfile().about, null);
        row = sheet.createRow(rowIndex);
        createCell(row, 0, LABEL_GENERATED, headerCellStyle);
        createCell(row, 1, ProfileExportData.formatIsoDateTime(new Date()), null);
    }

    private static void writeNotesSheet(XSSFWorkbook workbook, CellStyle headerCellStyle,
                                        ProfileExportData exportData) {
        Sheet sheet = workbook.createSheet(SHEET_NOTES);
        sheet.setColumnWidth(0, COLUMN_WIDTH_NARROW);
        sheet.setColumnWidth(1, COLUMN_WIDTH_WIDE);
        sheet.setColumnWidth(2, COLUMN_WIDTH_NARROW);
        writeHeaderRow(sheet, headerCellStyle, HEADER_ENTRY_DATE, HEADER_CONTENT, HEADER_TAGS);
        int rowIndex = 1;
        for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
            Row row = sheet.createRow(rowIndex++);
            createCell(row, 0, ProfileExportData.formatIsoDateTime(
                    noteExportData.getNote().entryDateTime), null);
            createCell(row, 1, noteExportData.getNote().content, null);
            createCell(row, 2, joinTags(noteExportData.getTags()), null);
        }
    }

    private static void writeMedicinesSheet(XSSFWorkbook workbook, CellStyle headerCellStyle,
                                            ProfileExportData exportData) {
        Sheet sheet = workbook.createSheet(SHEET_MEDICINES);
        sheet.setColumnWidth(0, COLUMN_WIDTH_NARROW);
        sheet.setColumnWidth(1, COLUMN_WIDTH_WIDE);
        sheet.setColumnWidth(2, COLUMN_WIDTH_NARROW);
        sheet.setColumnWidth(3, COLUMN_WIDTH_NARROW);
        writeHeaderRow(sheet, headerCellStyle, HEADER_NAME, HEADER_DESCRIPTION,
                HEADER_NOTE_ENTRY_DATE, HEADER_REMINDERS);
        int rowIndex = 1;
        for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
            for (ProfileExportData.MedicineExportData medicineExportData : noteExportData.getMedicines()) {
                Row row = sheet.createRow(rowIndex++);
                createCell(row, 0, medicineExportData.getMedicine().name, null);
                createCell(row, 1, medicineExportData.getMedicine().description, null);
                createCell(row, 2, ProfileExportData.formatIsoDateTime(
                        noteExportData.getNote().entryDateTime), null);
                createCell(row, 3, joinReminders(medicineExportData.getReminders()), null);
            }
        }
    }

    private static void writeIntakesSheet(XSSFWorkbook workbook, CellStyle headerCellStyle,
                                          ProfileExportData exportData) {
        Sheet sheet = workbook.createSheet(SHEET_INTAKES);
        sheet.setColumnWidth(0, COLUMN_WIDTH_NARROW);
        sheet.setColumnWidth(1, COLUMN_WIDTH_NARROW);
        sheet.setColumnWidth(2, COLUMN_WIDTH_WIDE);
        writeHeaderRow(sheet, headerCellStyle, HEADER_MEDICINE, HEADER_TAKEN_DATE_TIME, HEADER_DESCRIPTION);
        int rowIndex = 1;
        for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
            for (ProfileExportData.MedicineExportData medicineExportData : noteExportData.getMedicines()) {
                for (MedicineIntake medicineIntake : medicineExportData.getIntakes()) {
                    Row row = sheet.createRow(rowIndex++);
                    createCell(row, 0, medicineExportData.getMedicine().name, null);
                    createCell(row, 1, ProfileExportData.formatIsoDateTime(
                            medicineIntake.takenDateTime), null);
                    createCell(row, 2, medicineIntake.description, null);
                }
            }
        }
    }

    private static void writeHeaderRow(Sheet sheet, CellStyle headerCellStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            createCell(row, i, headers[i], headerCellStyle);
        }
    }

    private static void createCell(Row row, int column, String value, CellStyle cellStyle) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (cellStyle != null) {
            cell.setCellStyle(cellStyle);
        }
    }

    private static CellStyle createHeaderCellStyle(XSSFWorkbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        cellStyle.setFont(font);
        return cellStyle;
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
