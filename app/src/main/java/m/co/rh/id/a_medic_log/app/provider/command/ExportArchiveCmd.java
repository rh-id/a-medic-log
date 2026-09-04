package m.co.rh.id.a_medic_log.app.provider.command;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.BuildConfig;
import m.co.rh.id.a_medic_log.app.provider.component.ProfileExportData;
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
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Exports selected profiles with all of their notes, attachments, medicines, reminders
 * and intakes into a ZIP archive containing an export.json manifest (archive metadata
 * plus the per-profile content counts note_count, medicine_count, reminder_count and
 * attachment_count), one profiles/&lt;id&gt;.json entry per profile and the raw
 * attachment files.
 */
public class ExportArchiveCmd {
    private static final String TAG = ExportArchiveCmd.class.getName();

    /**
     * Current version of the export format written to the export.json manifest
     */
    public static final int FORMAT_VERSION = 1;

    private static final String EXPORT_FILE_NAME = "export.zip";
    private static final String ENTRY_EXPORT_JSON = "export.json";
    private static final String ENTRY_PROFILE_PREFIX = "profiles/";
    private static final String ENTRY_PROFILE_JSON_SUFFIX = ".json";
    private static final String ENTRY_IMAGE_PREFIX = "attachments/image/";
    private static final String ENTRY_THUMBNAIL_PREFIX = "attachments/image/thumbnail/";

    private static final String KEY_FORMAT_VERSION = "format_version";
    private static final String KEY_APP_VERSION = "app_version";
    private static final String KEY_EXPORTED_DATE_TIME = "exported_date_time";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_NOTE_COUNT = "note_count";
    private static final String KEY_MEDICINE_COUNT = "medicine_count";
    private static final String KEY_REMINDER_COUNT = "reminder_count";
    private static final String KEY_ATTACHMENT_COUNT = "attachment_count";
    private static final String KEY_ABOUT = "about";
    private static final String KEY_CREATED_DATE_TIME = "created_date_time";
    private static final String KEY_UPDATED_DATE_TIME = "updated_date_time";
    private static final String KEY_NOTES = "notes";
    private static final String KEY_ENTRY_DATE_TIME = "entry_date_time";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_TAG = "tag";
    private static final String KEY_ATTACHMENTS = "attachments";
    private static final String KEY_FILES = "files";
    private static final String KEY_FILE_NAME = "file_name";
    private static final String KEY_MEDICINES = "medicines";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_REMINDERS = "reminders";
    private static final String KEY_START_DATE_TIME = "start_date_time";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_REMINDER_DAYS = "reminder_days";
    private static final String KEY_INTAKES = "intakes";
    private static final String KEY_TAKEN_DATE_TIME = "taken_date_time";

    protected ExecutorService mExecutorService;
    protected FileHelper mFileHelper;
    protected ILogger mLogger;
    protected ProfileDao mProfileDao;
    protected NoteDao mNoteDao;
    protected NoteTagDao mNoteTagDao;
    protected NoteAttachmentDao mNoteAttachmentDao;
    protected NoteAttachmentFileDao mNoteAttachmentFileDao;
    protected MedicineDao mMedicineDao;
    protected MedicineReminderDao mMedicineReminderDao;
    protected MedicineIntakeDao mMedicineIntakeDao;

    public ExportArchiveCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mFileHelper = provider.get(FileHelper.class);
        mLogger = provider.get(ILogger.class);
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
     * Export profiles into a ZIP archive.
     *
     * @param profileIds ids of the profiles to export,
     *                   when null or empty all profiles are exported
     * @return Single of the created ZIP archive temp file
     */
    public Single<File> export(Collection<Long> profileIds) {
        return Single.fromCallable(() -> exportToFile(profileIds))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Export profiles into a ZIP archive and copy it into the destination Uri
     * (e.g. a SAF document Uri). The temporary archive file is deleted afterwards.
     *
     * @param profileIds ids of the profiles to export,
     *                   when null or empty all profiles are exported
     * @param destUri    destination Uri to copy the archive into
     * @return Single of the destination Uri
     */
    public Single<Uri> exportTo(Collection<Long> profileIds, Uri destUri) {
        return export(profileIds)
                .flatMap(exportFile -> Single.fromCallable(() -> {
                    try {
                        mFileHelper.copyFileToUri(exportFile, destUri);
                    } finally {
                        exportFile.delete();
                    }
                    return destUri;
                }).subscribeOn(Schedulers.from(mExecutorService)));
    }

    private File exportToFile(Collection<Long> profileIds) throws IOException {
        List<ProfileExportData> exportDataList = assembleExportData(profileIds);
        File outFile = mFileHelper.createTempFile(EXPORT_FILE_NAME);
        boolean success = false;
        try {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(outFile)))) {
                writeManifest(zipOutputStream, exportDataList);
                writeProfileEntries(zipOutputStream, exportDataList);
                writeAttachmentEntries(zipOutputStream, exportDataList);
            }
            success = true;
            return outFile;
        } catch (JSONException e) {
            throw new IOException("Failed to build export JSON", e);
        } finally {
            // delete the temp file on any failure, including Error-level throwables
            if (!success) {
                outFile.delete();
            }
        }
    }

    private List<ProfileExportData> assembleExportData(Collection<Long> profileIds) {
        List<Profile> profiles;
        if (profileIds == null || profileIds.isEmpty()) {
            profiles = mProfileDao.findProfiles();
        } else {
            profiles = new ArrayList<>();
            for (Long profileId : profileIds) {
                if (profileId == null) {
                    continue;
                }
                Profile profile = mProfileDao.findProfileById(profileId);
                if (profile != null) {
                    profiles.add(profile);
                }
            }
        }
        List<ProfileExportData> exportDataList = new ArrayList<>(profiles.size());
        for (Profile profile : profiles) {
            exportDataList.add(ProfileExportData.assemble(profile, mNoteDao, mNoteTagDao,
                    mNoteAttachmentDao, mNoteAttachmentFileDao, mMedicineDao,
                    mMedicineReminderDao, mMedicineIntakeDao));
        }
        return exportDataList;
    }

    /**
     * Writes the export.json manifest: the archive metadata plus the profiles
     * index (one id/name entry per exported profile extended with the profile
     * content counts note_count, medicine_count, reminder_count and
     * attachment_count, the per-profile entry file name is derived from the id).
     * The counts are computed from the in-memory export data, no extra
     * database or file reads happen here.
     */
    private void writeManifest(ZipOutputStream zipOutputStream,
                               List<ProfileExportData> exportDataList) throws IOException, JSONException {
        JSONObject manifest = new JSONObject();
        manifest.put(KEY_FORMAT_VERSION, FORMAT_VERSION);
        manifest.put(KEY_APP_VERSION, BuildConfig.VERSION_NAME);
        manifest.put(KEY_EXPORTED_DATE_TIME, System.currentTimeMillis());
        JSONArray profileArray = new JSONArray();
        for (ProfileExportData exportData : exportDataList) {
            Profile profile = exportData.getProfile();
            ContentCounts contentCounts = new ContentCounts(exportData);
            JSONObject profileJson = new JSONObject();
            profileJson.put(KEY_ID, toEpoch(profile.id));
            profileJson.put(KEY_NAME, profile.name);
            profileJson.put(KEY_NOTE_COUNT, contentCounts.mNoteCount);
            profileJson.put(KEY_MEDICINE_COUNT, contentCounts.mMedicineCount);
            profileJson.put(KEY_REMINDER_COUNT, contentCounts.mReminderCount);
            profileJson.put(KEY_ATTACHMENT_COUNT, contentCounts.mAttachmentCount);
            profileArray.put(profileJson);
        }
        manifest.put(KEY_PROFILES, profileArray);
        writeZipTextEntry(zipOutputStream, ENTRY_EXPORT_JSON, manifest.toString());
    }

    /**
     * Content counts of a single profile, computed from its in-memory export data.
     * medicine_count counts the DISTINCT medicine ids across all of the profile's
     * notes (a medicine shared by several notes is counted once, a null id counts
     * as its own entry only once), reminder_count sums the reminders of those
     * distinct medicines, attachment_count counts the attachments themselves
     * (never their file entries) while note_count counts the notes.
     * Package-private for tests.
     */
    static final class ContentCounts {
        final int mNoteCount;
        final int mMedicineCount;
        final int mReminderCount;
        final int mAttachmentCount;

        ContentCounts(ProfileExportData exportData) {
            Set<Long> distinctMedicineIds = new HashSet<>();
            int reminderCount = 0;
            int attachmentCount = 0;
            for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
                attachmentCount += noteExportData.getAttachments().size();
                for (ProfileExportData.MedicineExportData medicineExportData
                        : noteExportData.getMedicines()) {
                    // HashSet accepts null, so a null medicine id counts as its own entry once
                    if (distinctMedicineIds.add(medicineExportData.getMedicine().id)) {
                        reminderCount += medicineExportData.getReminders().size();
                    }
                }
            }
            mNoteCount = exportData.getNotes().size();
            mMedicineCount = distinctMedicineIds.size();
            mReminderCount = reminderCount;
            mAttachmentCount = attachmentCount;
        }
    }

    /**
     * Writes one profiles/&lt;id&gt;.json entry per exported profile, its content is
     * the unchanged full profile tree (notes, tags, attachments, medicines,
     * reminders and intakes). Each entry is written immediately while iterating
     * so the whole archive never has to be built as one big JSON string.
     */
    private void writeProfileEntries(ZipOutputStream zipOutputStream,
                                     List<ProfileExportData> exportDataList) throws IOException, JSONException {
        for (ProfileExportData exportData : exportDataList) {
            Profile profile = exportData.getProfile();
            if (profile.id == null) {
                throw new IOException("Cannot export profile " + profile.name + " without an id");
            }
            writeZipTextEntry(zipOutputStream, createProfileEntryName(profile.id),
                    createProfileJson(exportData).toString());
        }
    }

    private static String createProfileEntryName(long fileProfileId) {
        return ENTRY_PROFILE_PREFIX + fileProfileId + ENTRY_PROFILE_JSON_SUFFIX;
    }

    private static void writeZipTextEntry(ZipOutputStream zipOutputStream, String entryName, String text)
            throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private JSONObject createProfileJson(ProfileExportData exportData) throws JSONException {
        Profile profile = exportData.getProfile();
        JSONObject json = new JSONObject();
        json.put(KEY_ID, toEpoch(profile.id));
        json.put(KEY_NAME, profile.name);
        json.put(KEY_ABOUT, profile.about);
        json.put(KEY_CREATED_DATE_TIME, toEpoch(profile.createdDateTime));
        json.put(KEY_UPDATED_DATE_TIME, toEpoch(profile.updatedDateTime));
        JSONArray noteArray = new JSONArray();
        for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
            noteArray.put(createNoteJson(noteExportData));
        }
        json.put(KEY_NOTES, noteArray);
        return json;
    }

    private JSONObject createNoteJson(ProfileExportData.NoteExportData noteExportData) throws JSONException {
        Note note = noteExportData.getNote();
        JSONObject json = new JSONObject();
        json.put(KEY_ID, toEpoch(note.id));
        json.put(KEY_ENTRY_DATE_TIME, toEpoch(note.entryDateTime));
        json.put(KEY_CONTENT, note.content);
        json.put(KEY_CREATED_DATE_TIME, toEpoch(note.createdDateTime));
        json.put(KEY_UPDATED_DATE_TIME, toEpoch(note.updatedDateTime));
        JSONArray tagArray = new JSONArray();
        for (NoteTag noteTag : noteExportData.getTags()) {
            JSONObject tagJson = new JSONObject();
            tagJson.put(KEY_ID, toEpoch(noteTag.id));
            tagJson.put(KEY_TAG, noteTag.tag);
            tagArray.put(tagJson);
        }
        json.put(KEY_TAGS, tagArray);
        JSONArray attachmentArray = new JSONArray();
        for (ProfileExportData.NoteAttachmentExportData attachmentExportData : noteExportData.getAttachments()) {
            attachmentArray.put(createAttachmentJson(attachmentExportData));
        }
        json.put(KEY_ATTACHMENTS, attachmentArray);
        JSONArray medicineArray = new JSONArray();
        for (ProfileExportData.MedicineExportData medicineExportData : noteExportData.getMedicines()) {
            medicineArray.put(createMedicineJson(medicineExportData));
        }
        json.put(KEY_MEDICINES, medicineArray);
        return json;
    }

    private JSONObject createAttachmentJson(ProfileExportData.NoteAttachmentExportData attachmentExportData)
            throws JSONException {
        NoteAttachment noteAttachment = attachmentExportData.getNoteAttachment();
        JSONObject json = new JSONObject();
        json.put(KEY_ID, toEpoch(noteAttachment.id));
        json.put(KEY_NAME, noteAttachment.name);
        json.put(KEY_CREATED_DATE_TIME, toEpoch(noteAttachment.createdDateTime));
        JSONArray fileArray = new JSONArray();
        for (NoteAttachmentFile noteAttachmentFile : attachmentExportData.getFiles()) {
            JSONObject fileJson = new JSONObject();
            fileJson.put(KEY_ID, toEpoch(noteAttachmentFile.id));
            fileJson.put(KEY_FILE_NAME, noteAttachmentFile.fileName);
            fileJson.put(KEY_CREATED_DATE_TIME, toEpoch(noteAttachmentFile.createdDateTime));
            fileArray.put(fileJson);
        }
        json.put(KEY_FILES, fileArray);
        return json;
    }

    private JSONObject createMedicineJson(ProfileExportData.MedicineExportData medicineExportData)
            throws JSONException {
        Medicine medicine = medicineExportData.getMedicine();
        JSONObject json = new JSONObject();
        json.put(KEY_ID, toEpoch(medicine.id));
        json.put(KEY_NAME, medicine.name);
        json.put(KEY_DESCRIPTION, medicine.description);
        json.put(KEY_CREATED_DATE_TIME, toEpoch(medicine.createdDateTime));
        json.put(KEY_UPDATED_DATE_TIME, toEpoch(medicine.updatedDateTime));
        JSONArray reminderArray = new JSONArray();
        for (MedicineReminder medicineReminder : medicineExportData.getReminders()) {
            reminderArray.put(createReminderJson(medicineReminder));
        }
        json.put(KEY_REMINDERS, reminderArray);
        JSONArray intakeArray = new JSONArray();
        for (MedicineIntake medicineIntake : medicineExportData.getIntakes()) {
            JSONObject intakeJson = new JSONObject();
            intakeJson.put(KEY_ID, toEpoch(medicineIntake.id));
            intakeJson.put(KEY_DESCRIPTION, medicineIntake.description);
            intakeJson.put(KEY_TAKEN_DATE_TIME, toEpoch(medicineIntake.takenDateTime));
            intakeJson.put(KEY_CREATED_DATE_TIME, toEpoch(medicineIntake.createdDateTime));
            intakeJson.put(KEY_UPDATED_DATE_TIME, toEpoch(medicineIntake.updatedDateTime));
            intakeArray.put(intakeJson);
        }
        json.put(KEY_INTAKES, intakeArray);
        return json;
    }

    private JSONObject createReminderJson(MedicineReminder medicineReminder) throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_ID, toEpoch(medicineReminder.id));
        json.put(KEY_START_DATE_TIME, toEpoch(medicineReminder.startDateTime));
        json.put(KEY_REMINDER_ENABLED, medicineReminder.reminderEnabled != null && medicineReminder.reminderEnabled);
        json.put(KEY_MESSAGE, medicineReminder.message);
        JSONArray reminderDays = new JSONArray();
        if (medicineReminder.reminderDays != null) {
            for (Integer dayOfWeek : medicineReminder.reminderDays) {
                if (dayOfWeek != null) {
                    reminderDays.put(dayOfWeek.intValue());
                }
            }
        }
        json.put(KEY_REMINDER_DAYS, reminderDays);
        json.put(KEY_CREATED_DATE_TIME, toEpoch(medicineReminder.createdDateTime));
        json.put(KEY_UPDATED_DATE_TIME, toEpoch(medicineReminder.updatedDateTime));
        return json;
    }

    private void writeAttachmentEntries(ZipOutputStream zipOutputStream,
                                        List<ProfileExportData> exportDataList) throws IOException {
        Set<String> writtenEntryNames = new HashSet<>();
        for (ProfileExportData exportData : exportDataList) {
            for (ProfileExportData.NoteExportData noteExportData : exportData.getNotes()) {
                for (ProfileExportData.NoteAttachmentExportData attachmentExportData : noteExportData.getAttachments()) {
                    for (NoteAttachmentFile noteAttachmentFile : attachmentExportData.getFiles()) {
                        String fileName = noteAttachmentFile.fileName;
                        if (fileName == null || fileName.isEmpty()) {
                            continue;
                        }
                        writeZipFileEntry(zipOutputStream, ENTRY_IMAGE_PREFIX + fileName,
                                mFileHelper.getNoteAttachmentImage(fileName), writtenEntryNames);
                        writeZipFileEntry(zipOutputStream, ENTRY_THUMBNAIL_PREFIX + fileName,
                                mFileHelper.getNoteAttachmentThumbnail(fileName), writtenEntryNames);
                    }
                }
            }
        }
    }

    private void writeZipFileEntry(ZipOutputStream zipOutputStream, String entryName, File file,
                                   Set<String> writtenEntryNames) throws IOException {
        if (writtenEntryNames.contains(entryName)) {
            return;
        }
        if (!file.exists()) {
            mLogger.e(TAG, "Skipping missing attachment file entry " + entryName
                    + " path: " + file.getAbsolutePath());
            return;
        }
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buff = new byte[2048];
            int b = inputStream.read(buff);
            while (b != -1) {
                zipOutputStream.write(buff, 0, b);
                b = inputStream.read(buff);
            }
        }
        zipOutputStream.closeEntry();
        writtenEntryNames.add(entryName);
    }

    private static Object toEpoch(Date date) {
        return date == null ? JSONObject.NULL : date.getTime();
    }

    private static Object toEpoch(Long id) {
        return id == null ? JSONObject.NULL : id;
    }
}
