package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.component.CsvArchiveExporter;
import m.co.rh.id.a_medic_log.app.provider.component.ExcelExporter;
import m.co.rh.id.a_medic_log.app.provider.component.ProfileExportData;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.aprovider.Provider;

/**
 * Exports a single profile with all of its notes, attachments, medicines,
 * reminders and intakes as a spreadsheet archive.
 * Uses XLSX spreadsheet on API 26+, falls back to a ZIP archive of CSV files on older API levels.
 */
public class ExportSpreadsheetCmd {
    private static final String EXCEL_FILE_NAME = "profile_export.xlsx";
    private static final String CSV_FILE_NAME = "profile_export.zip";

    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected FileHelper mFileHelper;
    protected ProfileDao mProfileDao;
    protected NoteDao mNoteDao;
    protected NoteTagDao mNoteTagDao;
    protected NoteAttachmentDao mNoteAttachmentDao;
    protected NoteAttachmentFileDao mNoteAttachmentFileDao;
    protected MedicineDao mMedicineDao;
    protected MedicineReminderDao mMedicineReminderDao;
    protected MedicineIntakeDao mMedicineIntakeDao;

    public ExportSpreadsheetCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mFileHelper = provider.get(FileHelper.class);
        mProfileDao = provider.get(ProfileDao.class);
        mNoteDao = provider.get(NoteDao.class);
        mNoteTagDao = provider.get(NoteTagDao.class);
        mNoteAttachmentDao = provider.get(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = provider.get(NoteAttachmentFileDao.class);
        mMedicineDao = provider.get(MedicineDao.class);
        mMedicineReminderDao = provider.get(MedicineReminderDao.class);
        mMedicineIntakeDao = provider.get(MedicineIntakeDao.class);
    }

    /**
     * Export a single profile into a spreadsheet archive.
     *
     * @param profileId id of the profile to export
     * @return Single of the created archive temp file
     */
    public Single<File> export(long profileId) {
        return Single.fromCallable(() -> exportToFile(profileId))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Export a single profile into a spreadsheet archive and copy it into the destination Uri
     * (e.g. a SAF document Uri). The temporary archive file is deleted afterwards.
     *
     * @param profileId id of the profile to export
     * @param destUri   destination Uri to copy the archive into
     * @return Single of the destination Uri
     */
    public Single<Uri> exportTo(long profileId, Uri destUri) {
        return export(profileId)
                .flatMap(exportFile -> Single.fromCallable(() -> {
                    try {
                        mFileHelper.copyFileToUri(exportFile, destUri);
                    } finally {
                        exportFile.delete();
                    }
                    return destUri;
                }).subscribeOn(Schedulers.from(mExecutorService)));
    }

    private File exportToFile(long profileId) throws Exception {
        Profile profile = mProfileDao.findProfileById(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Profile not found for id: " + profileId);
        }
        ProfileExportData exportData = ProfileExportData.assemble(profile, mNoteDao, mNoteTagDao,
                mNoteAttachmentDao, mNoteAttachmentFileDao, mMedicineDao,
                mMedicineReminderDao, mMedicineIntakeDao);
        // ExcelExporter touches POI which only supports API 26+,
        // it must never be constructed nor used below API 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return exportExcel(exportData);
        }
        return exportCsv(exportData);
    }

    private File exportExcel(ProfileExportData exportData) throws Exception {
        File outFile = mFileHelper.createTempFile(EXCEL_FILE_NAME);
        boolean success = false;
        try {
            File result = new ExcelExporter(mAppContext).export(outFile, exportData);
            success = true;
            return result;
        } finally {
            // delete the temp file on any failure, including Error-level throwables
            if (!success) {
                outFile.delete();
            }
        }
    }

    private File exportCsv(ProfileExportData exportData) throws IOException {
        File outFile = mFileHelper.createTempFile(CSV_FILE_NAME);
        boolean success = false;
        try {
            File result = new CsvArchiveExporter().export(outFile, exportData);
            success = true;
            return result;
        } finally {
            // delete the temp file on any failure, including Error-level throwables
            if (!success) {
                outFile.delete();
            }
        }
    }
}
