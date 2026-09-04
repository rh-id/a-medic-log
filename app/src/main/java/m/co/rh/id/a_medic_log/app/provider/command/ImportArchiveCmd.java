package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.base.AppDatabase;
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
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Imports profiles from an export ZIP archive created by {@link ExportArchiveCmd}.
 * Import is crash-safe: attachment files are extracted and copied first,
 * then all database inserts run in a single transaction. If anything fails,
 * the copied attachment files are deleted and no data is committed.
 */
public class ImportArchiveCmd {
    private static final String TAG = ImportArchiveCmd.class.getName();

    private static final String ENTRY_EXPORT_JSON = "export.json";
    private static final String ENTRY_PROFILE_PREFIX = "profiles/";
    private static final String ENTRY_PROFILE_JSON_SUFFIX = ".json";
    private static final String ENTRY_IMAGE_PREFIX = "attachments/image/";
    private static final String ENTRY_THUMBNAIL_PREFIX = "attachments/image/thumbnail/";

    /**
     * Maximum size of a JSON entry (export.json or profiles/&lt;id&gt;.json) read
     * from an export archive, far above any legitimate export. Guards against
     * hand-crafted archives declaring enormous entries that would exhaust
     * the memory when read fully.
     */
    private static final long MAX_JSON_ENTRY_BYTES = 64 * 1024 * 1024;

    private static final String KEY_FORMAT_VERSION = "format_version";
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

    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected AppDatabase mAppDatabase;
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
    protected ProfileChangeNotifier mProfileChangeNotifier;
    protected NoteChangeNotifier mNoteChangeNotifier;

    public ImportArchiveCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mAppDatabase = provider.get(AppDatabase.class);
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
        mProfileChangeNotifier = provider.get(ProfileChangeNotifier.class);
        mNoteChangeNotifier = provider.get(NoteChangeNotifier.class);
    }

    /**
     * Read the list of profiles available inside an export ZIP archive
     * without importing anything. Only the export.json manifest is read,
     * the per-profile entry files are not parsed.
     *
     * @param zipUri URI of the export ZIP archive
     * @return Single of the profiles found in the archive manifest
     */
    public Single<List<FileProfile>> peek(Uri zipUri) {
        return Single.fromCallable(() -> peekFileProfiles(zipUri))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Import selected profiles from an export ZIP archive.
     * On success, a profile added event is emitted per imported profile
     * and a note added event (with the full nested state) is emitted per imported note.
     *
     * @param zipUri         URI of the export ZIP archive
     * @param fileProfileIds file profile ids (from {@link #peek(Uri)}) of the profiles to import,
     *                       when null or empty all profiles found in the archive are imported
     * @return Single of the import result counts
     */
    public Single<ImportResult> importProfiles(Uri zipUri, Collection<Long> fileProfileIds) {
        return Single.fromCallable(() -> importSelectedProfiles(zipUri, fileProfileIds))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    private List<FileProfile> peekFileProfiles(Uri zipUri) throws IOException {
        JSONObject manifest = readExportJsonRoot(zipUri);
        return parseManifestFileProfiles(manifest);
    }

    private ImportResult importSelectedProfiles(Uri zipUri, Collection<Long> fileProfileIds) throws IOException {
        JSONObject manifest = readExportJsonRoot(zipUri);
        List<FileProfile> manifestFileProfiles = parseManifestFileProfiles(manifest);
        List<Long> selectedFileProfileIds =
                resolveSelectedFileProfileIds(manifestFileProfiles, fileProfileIds);
        List<ProfileRecord> selectedProfileRecords =
                parseSelectedProfileRecords(zipUri, selectedFileProfileIds);
        ImportResult importResult = new ImportResult();
        if (selectedProfileRecords.isEmpty()) {
            return importResult;
        }
        Map<String, String> newFileNames = new LinkedHashMap<>();
        Map<String, File> imageTempFiles = new LinkedHashMap<>();
        Map<String, File> thumbnailTempFiles = new LinkedHashMap<>();
        List<File> tempFiles = new ArrayList<>();
        List<String> copiedFileNames = new ArrayList<>();
        List<NoteState> noteStates = new ArrayList<>();
        try {
            extractAttachmentFiles(zipUri, selectedProfileRecords, newFileNames,
                    imageTempFiles, thumbnailTempFiles, tempFiles);
            copyExtractedAttachmentFiles(selectedProfileRecords, newFileNames,
                    imageTempFiles, thumbnailTempFiles, copiedFileNames);
            mAppDatabase.runInTransaction(() ->
                    insertProfileRecords(selectedProfileRecords, noteStates, importResult));
            // emit events only after the transaction committed successfully,
            // noteAdded carries the full nested state so that reminder works are scheduled from it,
            // no separate medicine/reminder/intake events are emitted to avoid duplicated reminder works
            for (ProfileRecord profileRecord : selectedProfileRecords) {
                mProfileChangeNotifier.profileAdded(profileRecord.profile);
            }
            for (NoteState noteState : noteStates) {
                mNoteChangeNotifier.noteAdded(noteState);
            }
            return importResult;
        } catch (Exception e) {
            mLogger.e(TAG, "Import failed, deleting copied attachment files", e);
            for (String copiedFileName : copiedFileNames) {
                mFileHelper.deleteNoteAttachmentImage(copiedFileName);
            }
            throw e;
        } finally {
            for (File tempFile : tempFiles) {
                tempFile.delete();
            }
        }
    }

    private JSONObject readExportJsonRoot(Uri zipUri) throws IOException {
        byte[] jsonBytes = readExportJson(zipUri);
        try {
            JSONObject root = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8));
            checkFormatVersion(root);
            return root;
        } catch (JSONException e) {
            throw new IOException("Invalid " + ENTRY_EXPORT_JSON + " content", e);
        }
    }

    private void checkFormatVersion(JSONObject root) throws IOException {
        int formatVersion = root.optInt(KEY_FORMAT_VERSION, 0);
        if (formatVersion < 1) {
            throw new IOException("Invalid " + ENTRY_EXPORT_JSON + ", missing or invalid "
                    + KEY_FORMAT_VERSION);
        }
        if (formatVersion > ExportArchiveCmd.FORMAT_VERSION) {
            throw new IOException("Unsupported export format version " + formatVersion
                    + ", this app supports up to version " + ExportArchiveCmd.FORMAT_VERSION);
        }
    }

    private byte[] readExportJson(Uri zipUri) throws IOException {
        try (InputStream inputStream = openInputStream(zipUri);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             ZipInputStream zipInputStream = new ZipInputStream(bufferedInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (ENTRY_EXPORT_JSON.equals(zipEntry.getName())) {
                    return readJsonEntryBytes(zipEntry, zipInputStream);
                }
            }
        }
        throw new IOException("Export archive has no " + ENTRY_EXPORT_JSON + " entry");
    }

    private InputStream openInputStream(Uri zipUri) throws IOException {
        InputStream inputStream = mAppContext.getContentResolver().openInputStream(zipUri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream for " + zipUri);
        }
        return inputStream;
    }

    /**
     * Parses the manifest profiles index into the FileProfile list.
     * A missing or null profiles array yields an empty list (defensive
     * against hand-edited archives): entries that are not JSON objects are
     * skipped, while entries that are objects must carry a positive numeric
     * id or the whole read fails.
     */
    private List<FileProfile> parseManifestFileProfiles(JSONObject manifest) throws IOException {
        List<FileProfile> fileProfiles = new ArrayList<>();
        JSONArray profileArray = manifest.optJSONArray(KEY_PROFILES);
        if (profileArray == null) {
            return fileProfiles;
        }
        int size = profileArray.length();
        for (int i = 0; i < size; i++) {
            JSONObject profileJson = profileArray.optJSONObject(i);
            if (profileJson == null) {
                continue;
            }
            fileProfiles.add(new FileProfile(parseManifestProfileId(profileJson, i),
                    profileJson.optString(KEY_NAME),
                    profileJson.optInt(KEY_NOTE_COUNT),
                    profileJson.optInt(KEY_MEDICINE_COUNT),
                    profileJson.optInt(KEY_REMINDER_COUNT),
                    profileJson.optInt(KEY_ATTACHMENT_COUNT)));
        }
        return fileProfiles;
    }

    private long parseManifestProfileId(JSONObject profileJson, int index) throws IOException {
        long fileProfileId;
        try {
            fileProfileId = profileJson.getLong(KEY_ID);
        } catch (JSONException e) {
            throw new IOException("Invalid " + ENTRY_EXPORT_JSON + ", the manifest profile entry at index "
                    + index + " has a missing or non-numeric " + KEY_ID, e);
        }
        if (fileProfileId <= 0) {
            throw new IOException("Invalid " + ENTRY_EXPORT_JSON + ", the manifest profile entry at index "
                    + index + " has a non-positive " + KEY_ID + ": " + fileProfileId);
        }
        return fileProfileId;
    }

    /**
     * Resolves the import selection against the manifest profiles index.
     * A null or empty selection selects all manifest ids, otherwise the manifest
     * ids matching the selection are returned in manifest order
     * (ids that are not listed in the manifest are ignored, as before).
     * Duplicate manifest ids are deduplicated (preserving the first manifest
     * position) so each distinct id is imported at most once per
     * importProfiles call, defensive against hand-edited archives.
     */
    private static List<Long> resolveSelectedFileProfileIds(List<FileProfile> manifestFileProfiles,
                                                            Collection<Long> fileProfileIds) {
        LinkedHashSet<Long> uniqueSelectedFileProfileIds =
                new LinkedHashSet<>(manifestFileProfiles.size());
        if (fileProfileIds == null || fileProfileIds.isEmpty()) {
            for (FileProfile fileProfile : manifestFileProfiles) {
                uniqueSelectedFileProfileIds.add(fileProfile.fileProfileId);
            }
        } else {
            for (FileProfile fileProfile : manifestFileProfiles) {
                if (fileProfileIds.contains(fileProfile.fileProfileId)) {
                    uniqueSelectedFileProfileIds.add(fileProfile.fileProfileId);
                }
            }
        }
        return new ArrayList<>(uniqueSelectedFileProfileIds);
    }

    /**
     * Reads only the profiles/&lt;id&gt;.json entries of the selected file profile ids
     * (unselected profiles are never parsed) and parses each of them into a
     * ProfileRecord. A selected id whose profile entry is missing from the
     * archive fails the import.
     */
    private List<ProfileRecord> parseSelectedProfileRecords(Uri zipUri,
                                                            List<Long> selectedFileProfileIds) throws IOException {
        Map<Long, byte[]> profileJsonBytesByFileProfileId = new LinkedHashMap<>();
        try (InputStream inputStream = openInputStream(zipUri);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             ZipInputStream zipInputStream = new ZipInputStream(bufferedInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                Long fileProfileId = parseProfileEntryFileProfileId(zipEntry.getName());
                if (fileProfileId == null || !selectedFileProfileIds.contains(fileProfileId)
                        || profileJsonBytesByFileProfileId.containsKey(fileProfileId)) {
                    continue;
                }
                profileJsonBytesByFileProfileId.put(fileProfileId,
                        readJsonEntryBytes(zipEntry, zipInputStream));
            }
        }
        List<ProfileRecord> selectedProfileRecords = new ArrayList<>(selectedFileProfileIds.size());
        for (Long fileProfileId : selectedFileProfileIds) {
            byte[] profileJsonBytes = profileJsonBytesByFileProfileId.get(fileProfileId);
            if (profileJsonBytes == null) {
                throw new IOException("Export archive is missing the profile entry "
                        + createProfileEntryName(fileProfileId));
            }
            selectedProfileRecords.add(parseProfileRecord(fileProfileId, profileJsonBytes));
        }
        return selectedProfileRecords;
    }

    /**
     * @return the file profile id of a profiles/&lt;id&gt;.json zip entry name,
     * or null when the entry name is not a plain numeric profile entry name
     */
    private static Long parseProfileEntryFileProfileId(String entryName) {
        if (!entryName.startsWith(ENTRY_PROFILE_PREFIX)
                || !entryName.endsWith(ENTRY_PROFILE_JSON_SUFFIX)) {
            return null;
        }
        String fileName = entryName.substring(ENTRY_PROFILE_PREFIX.length(),
                entryName.length() - ENTRY_PROFILE_JSON_SUFFIX.length());
        try {
            return Long.parseLong(fileName);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String createProfileEntryName(long fileProfileId) {
        return ENTRY_PROFILE_PREFIX + fileProfileId + ENTRY_PROFILE_JSON_SUFFIX;
    }

    private ProfileRecord parseProfileRecord(long fileProfileId, byte[] profileJsonBytes) throws IOException {
        JSONObject profileJson;
        try {
            profileJson = new JSONObject(new String(profileJsonBytes, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException("Invalid content in the profile entry "
                    + createProfileEntryName(fileProfileId), e);
        }
        Profile profile = new Profile();
        profile.id = profileJson.optLong(KEY_ID);
        profile.name = profileJson.optString(KEY_NAME);
        profile.about = profileJson.optString(KEY_ABOUT);
        profile.createdDateTime = parseDate(profileJson, KEY_CREATED_DATE_TIME);
        profile.updatedDateTime = parseDate(profileJson, KEY_UPDATED_DATE_TIME);
        List<NoteRecord> notes = new ArrayList<>();
        JSONArray noteArray = profileJson.optJSONArray(KEY_NOTES);
        if (noteArray != null) {
            int size = noteArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject noteJson = noteArray.optJSONObject(i);
                if (noteJson == null) {
                    continue;
                }
                notes.add(parseNoteRecord(noteJson));
            }
        }
        return new ProfileRecord(profile, notes);
    }

    private NoteRecord parseNoteRecord(JSONObject noteJson) {
        Note note = new Note();
        note.id = noteJson.optLong(KEY_ID);
        note.entryDateTime = parseDate(noteJson, KEY_ENTRY_DATE_TIME);
        note.content = noteJson.optString(KEY_CONTENT);
        note.createdDateTime = parseDate(noteJson, KEY_CREATED_DATE_TIME);
        note.updatedDateTime = parseDate(noteJson, KEY_UPDATED_DATE_TIME);
        List<NoteTag> tags = new ArrayList<>();
        JSONArray tagArray = noteJson.optJSONArray(KEY_TAGS);
        if (tagArray != null) {
            int size = tagArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject tagJson = tagArray.optJSONObject(i);
                if (tagJson == null) {
                    continue;
                }
                NoteTag noteTag = new NoteTag();
                noteTag.id = tagJson.optLong(KEY_ID);
                noteTag.tag = tagJson.optString(KEY_TAG);
                tags.add(noteTag);
            }
        }
        List<AttachmentRecord> attachments = new ArrayList<>();
        JSONArray attachmentArray = noteJson.optJSONArray(KEY_ATTACHMENTS);
        if (attachmentArray != null) {
            int size = attachmentArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject attachmentJson = attachmentArray.optJSONObject(i);
                if (attachmentJson == null) {
                    continue;
                }
                attachments.add(parseAttachmentRecord(attachmentJson));
            }
        }
        List<MedicineRecord> medicines = new ArrayList<>();
        JSONArray medicineArray = noteJson.optJSONArray(KEY_MEDICINES);
        if (medicineArray != null) {
            int size = medicineArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject medicineJson = medicineArray.optJSONObject(i);
                if (medicineJson == null) {
                    continue;
                }
                medicines.add(parseMedicineRecord(medicineJson));
            }
        }
        return new NoteRecord(note, tags, attachments, medicines);
    }

    private AttachmentRecord parseAttachmentRecord(JSONObject attachmentJson) {
        NoteAttachment noteAttachment = new NoteAttachment();
        noteAttachment.id = attachmentJson.optLong(KEY_ID);
        noteAttachment.name = attachmentJson.optString(KEY_NAME);
        noteAttachment.createdDateTime = parseDate(attachmentJson, KEY_CREATED_DATE_TIME);
        List<NoteAttachmentFile> files = new ArrayList<>();
        JSONArray fileArray = attachmentJson.optJSONArray(KEY_FILES);
        if (fileArray != null) {
            int size = fileArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject fileJson = fileArray.optJSONObject(i);
                if (fileJson == null) {
                    continue;
                }
                NoteAttachmentFile noteAttachmentFile = new NoteAttachmentFile();
                noteAttachmentFile.id = fileJson.optLong(KEY_ID);
                noteAttachmentFile.fileName = fileJson.optString(KEY_FILE_NAME);
                noteAttachmentFile.createdDateTime = parseDate(fileJson, KEY_CREATED_DATE_TIME);
                files.add(noteAttachmentFile);
            }
        }
        return new AttachmentRecord(noteAttachment, files);
    }

    private MedicineRecord parseMedicineRecord(JSONObject medicineJson) {
        Medicine medicine = new Medicine();
        medicine.id = medicineJson.optLong(KEY_ID);
        medicine.name = medicineJson.optString(KEY_NAME);
        medicine.description = medicineJson.optString(KEY_DESCRIPTION);
        medicine.createdDateTime = parseDate(medicineJson, KEY_CREATED_DATE_TIME);
        medicine.updatedDateTime = parseDate(medicineJson, KEY_UPDATED_DATE_TIME);
        List<MedicineReminder> reminders = new ArrayList<>();
        JSONArray reminderArray = medicineJson.optJSONArray(KEY_REMINDERS);
        if (reminderArray != null) {
            int size = reminderArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject reminderJson = reminderArray.optJSONObject(i);
                if (reminderJson == null) {
                    continue;
                }
                reminders.add(parseMedicineReminder(reminderJson));
            }
        }
        List<MedicineIntake> intakes = new ArrayList<>();
        JSONArray intakeArray = medicineJson.optJSONArray(KEY_INTAKES);
        if (intakeArray != null) {
            int size = intakeArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject intakeJson = intakeArray.optJSONObject(i);
                if (intakeJson == null) {
                    continue;
                }
                MedicineIntake medicineIntake = new MedicineIntake();
                medicineIntake.id = intakeJson.optLong(KEY_ID);
                medicineIntake.description = intakeJson.optString(KEY_DESCRIPTION);
                medicineIntake.takenDateTime = parseDate(intakeJson, KEY_TAKEN_DATE_TIME);
                medicineIntake.createdDateTime = parseDate(intakeJson, KEY_CREATED_DATE_TIME);
                medicineIntake.updatedDateTime = parseDate(intakeJson, KEY_UPDATED_DATE_TIME);
                intakes.add(medicineIntake);
            }
        }
        return new MedicineRecord(medicine, reminders, intakes);
    }

    private MedicineReminder parseMedicineReminder(JSONObject reminderJson) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.id = reminderJson.optLong(KEY_ID);
        medicineReminder.startDateTime = parseDate(reminderJson, KEY_START_DATE_TIME);
        medicineReminder.reminderEnabled = reminderJson.optBoolean(KEY_REMINDER_ENABLED, true);
        medicineReminder.message = reminderJson.optString(KEY_MESSAGE);
        LinkedHashSet<Integer> reminderDays = new LinkedHashSet<>();
        JSONArray reminderDaysArray = reminderJson.optJSONArray(KEY_REMINDER_DAYS);
        if (reminderDaysArray != null) {
            int size = reminderDaysArray.length();
            for (int i = 0; i < size; i++) {
                reminderDays.add(reminderDaysArray.optInt(i));
            }
        }
        medicineReminder.reminderDays = reminderDays;
        medicineReminder.createdDateTime = parseDate(reminderJson, KEY_CREATED_DATE_TIME);
        medicineReminder.updatedDateTime = parseDate(reminderJson, KEY_UPDATED_DATE_TIME);
        return medicineReminder;
    }

    private static Date parseDate(JSONObject json, String key) {
        return new Date(json.optLong(key, 0L));
    }

    /**
     * First pass of the import, reads the ZIP and extracts every attachment image and
     * thumbnail entry of the selected profiles into temp files named with a new unique file name.
     */
    private void extractAttachmentFiles(Uri zipUri, List<ProfileRecord> selectedProfileRecords,
                                        Map<String, String> newFileNames,
                                        Map<String, File> imageTempFiles,
                                        Map<String, File> thumbnailTempFiles,
                                        List<File> tempFiles) throws IOException {
        for (ProfileRecord profileRecord : selectedProfileRecords) {
            for (NoteRecord noteRecord : profileRecord.notes) {
                for (AttachmentRecord attachmentRecord : noteRecord.attachments) {
                    for (NoteAttachmentFile noteAttachmentFile : attachmentRecord.files) {
                        String originalFileName = noteAttachmentFile.fileName;
                        if (originalFileName == null || originalFileName.isEmpty()
                                || newFileNames.containsKey(originalFileName)) {
                            continue;
                        }
                        newFileNames.put(originalFileName, generateUniqueFileName(originalFileName));
                    }
                }
            }
        }
        if (newFileNames.isEmpty()) {
            return;
        }
        try (InputStream inputStream = openInputStream(zipUri);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             ZipInputStream zipInputStream = new ZipInputStream(bufferedInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                String originalFileName;
                boolean isThumbnail;
                // check thumbnail prefix first, it is contained in the image prefix path
                if (entryName.startsWith(ENTRY_THUMBNAIL_PREFIX)) {
                    originalFileName = entryName.substring(ENTRY_THUMBNAIL_PREFIX.length());
                    isThumbnail = true;
                } else if (entryName.startsWith(ENTRY_IMAGE_PREFIX)) {
                    originalFileName = entryName.substring(ENTRY_IMAGE_PREFIX.length());
                    isThumbnail = false;
                } else {
                    continue;
                }
                if (!newFileNames.containsKey(originalFileName)) {
                    continue;
                }
                Map<String, File> targetTempFiles = isThumbnail ? thumbnailTempFiles : imageTempFiles;
                if (targetTempFiles.containsKey(originalFileName)) {
                    continue;
                }
                File tempFile = mFileHelper.createTempFile(newFileNames.get(originalFileName));
                tempFiles.add(tempFile);
                copyStreamToFile(zipInputStream, tempFile);
                targetTempFiles.put(originalFileName, tempFile);
            }
        }
    }

    /**
     * Second pass of the import, raw copies every extracted temp file into the
     * attachment image and thumbnail directories, then renames the in-memory
     * attachment file entries to their new unique file names.
     */
    private void copyExtractedAttachmentFiles(List<ProfileRecord> selectedProfileRecords,
                                              Map<String, String> newFileNames,
                                              Map<String, File> imageTempFiles,
                                              Map<String, File> thumbnailTempFiles,
                                              List<String> copiedFileNames) throws IOException {
        for (ProfileRecord profileRecord : selectedProfileRecords) {
            for (NoteRecord noteRecord : profileRecord.notes) {
                for (AttachmentRecord attachmentRecord : noteRecord.attachments) {
                    Iterator<NoteAttachmentFile> fileIterator = attachmentRecord.files.iterator();
                    while (fileIterator.hasNext()) {
                        NoteAttachmentFile noteAttachmentFile = fileIterator.next();
                        String originalFileName = noteAttachmentFile.fileName;
                        File imageTempFile = imageTempFiles.get(originalFileName);
                        if (imageTempFile == null) {
                            mLogger.e(TAG, "Skipping import of attachment file with missing image entry: "
                                    + originalFileName);
                            fileIterator.remove();
                            continue;
                        }
                        File thumbnailTempFile = thumbnailTempFiles.get(originalFileName);
                        if (thumbnailTempFile == null) {
                            mLogger.e(TAG, "Missing thumbnail entry for attachment file: "
                                    + originalFileName + ", using the image as thumbnail");
                            thumbnailTempFile = imageTempFile;
                        }
                        String newFileName = newFileNames.get(originalFileName);
                        mFileHelper.copyToAttachmentDirs(imageTempFile, thumbnailTempFile, newFileName);
                        copiedFileNames.add(newFileName);
                        noteAttachmentFile.fileName = newFileName;
                    }
                }
            }
        }
    }

    /**
     * Insert the whole imported object graph in a single database transaction,
     * every generated id is written back to the in-memory objects and parent
     * references are remapped while inserting.
     * Must be called on a background thread.
     */
    private void insertProfileRecords(List<ProfileRecord> selectedProfileRecords,
                                      List<NoteState> noteStates, ImportResult importResult) {
        for (ProfileRecord profileRecord : selectedProfileRecords) {
            Profile profile = profileRecord.profile;
            profile.id = null;
            profile.id = mProfileDao.insert(profile);
            importResult.profileCount++;
            int size = profileRecord.notes.size();
            for (int i = 0; i < size; i++) {
                noteStates.add(insertNoteRecord(profile.id, profileRecord.notes.get(i), importResult));
            }
        }
    }

    private NoteState insertNoteRecord(Long profileId, NoteRecord noteRecord, ImportResult importResult) {
        Note note = noteRecord.note;
        note.profileId = profileId;
        note.id = null;
        note.id = mNoteDao.insert(note);
        importResult.noteCount++;
        for (NoteTag noteTag : noteRecord.tags) {
            noteTag.noteId = note.id;
            noteTag.id = null;
            noteTag.id = mNoteTagDao.insert(noteTag);
            importResult.tagCount++;
        }
        List<NoteAttachmentState> noteAttachmentStates = new ArrayList<>();
        for (AttachmentRecord attachmentRecord : noteRecord.attachments) {
            NoteAttachment noteAttachment = attachmentRecord.noteAttachment;
            noteAttachment.noteId = note.id;
            noteAttachment.id = null;
            noteAttachment.id = mNoteAttachmentDao.insert(noteAttachment);
            importResult.attachmentCount++;
            for (NoteAttachmentFile noteAttachmentFile : attachmentRecord.files) {
                noteAttachmentFile.attachmentId = noteAttachment.id;
                noteAttachmentFile.id = null;
                noteAttachmentFile.id = mNoteAttachmentFileDao.insert(noteAttachmentFile);
                importResult.fileCount++;
            }
            NoteAttachmentState noteAttachmentState = new NoteAttachmentState();
            noteAttachmentState.updateNoteAttachment(noteAttachment);
            noteAttachmentState.updateNoteAttachmentFileList(attachmentRecord.files);
            noteAttachmentStates.add(noteAttachmentState);
        }
        List<MedicineState> medicineStates = new ArrayList<>();
        for (MedicineRecord medicineRecord : noteRecord.medicines) {
            Medicine medicine = medicineRecord.medicine;
            medicine.noteId = note.id;
            medicine.id = null;
            medicine.id = mMedicineDao.insert(medicine);
            importResult.medicineCount++;
            for (MedicineReminder medicineReminder : medicineRecord.reminders) {
                medicineReminder.medicineId = medicine.id;
                medicineReminder.id = null;
                medicineReminder.id = mMedicineReminderDao.insert(medicineReminder);
                importResult.reminderCount++;
            }
            // NoteRepository.insertNote does not insert intakes, import must insert them explicitly
            for (MedicineIntake medicineIntake : medicineRecord.intakes) {
                medicineIntake.medicineId = medicine.id;
                medicineIntake.id = 0;
                medicineIntake.id = mMedicineIntakeDao.insert(medicineIntake);
                importResult.intakeCount++;
            }
            MedicineState medicineState = new MedicineState();
            medicineState.updateMedicine(medicine);
            medicineState.updateMedicineReminderList(medicineRecord.reminders);
            medicineStates.add(medicineState);
        }
        NoteState noteState = new NoteState();
        noteState.updateNote(note);
        noteState.updateNoteTagSet(noteRecord.tags);
        noteState.updateNoteAttachments(noteAttachmentStates);
        noteState.updateMedicineStates(medicineStates);
        return noteState;
    }

    private static String generateUniqueFileName(String originalFileName) {
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalFileName.length() - 1) {
            extension = originalFileName.substring(dotIndex);
        }
        return UUID.randomUUID().toString() + extension;
    }

    private static void copyStreamToFile(InputStream inputStream, File outFile) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(outFile)) {
            byte[] buff = new byte[2048];
            int b = inputStream.read(buff);
            while (b != -1) {
                outputStream.write(buff, 0, b);
                b = inputStream.read(buff);
            }
            outputStream.flush();
        }
    }

    /**
     * Reads a JSON archive entry fully into memory, bounded by MAX_JSON_ENTRY_BYTES.
     * ZipEntry.getSize() may return -1 (unknown size), so the stream copy itself is
     * bounded as the robust option; a known over-cap declared size fails immediately
     * before reading.
     */
    private static byte[] readJsonEntryBytes(ZipEntry zipEntry, InputStream inputStream) throws IOException {
        long declaredSize = zipEntry.getSize();
        if (declaredSize > MAX_JSON_ENTRY_BYTES) {
            throw new IOException("Export archive entry " + zipEntry.getName()
                    + " exceeds the maximum supported size");
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buff = new byte[2048];
        long totalBytes = 0;
        int b = inputStream.read(buff);
        while (b != -1) {
            totalBytes += b;
            if (totalBytes > MAX_JSON_ENTRY_BYTES) {
                throw new IOException("Export archive entry " + zipEntry.getName()
                        + " exceeds the maximum supported size");
            }
            byteArrayOutputStream.write(buff, 0, b);
            b = inputStream.read(buff);
        }
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * A profile record found inside an export archive. The content counts come
     * from the manifest entry (written by {@link ExportArchiveCmd}), a missing
     * or non-numeric count is read as 0, defensive against hand-edited archives.
     */
    public static class FileProfile implements Serializable {
        public final long fileProfileId;
        public final String name;
        public final int noteCount;
        public final int medicineCount;
        public final int reminderCount;
        public final int attachmentCount;

        public FileProfile(long fileProfileId, String name, int noteCount,
                           int medicineCount, int reminderCount, int attachmentCount) {
            this.fileProfileId = fileProfileId;
            this.name = name;
            this.noteCount = noteCount;
            this.medicineCount = medicineCount;
            this.reminderCount = reminderCount;
            this.attachmentCount = attachmentCount;
        }
    }

    /**
     * Counts of the records inserted by a successful import
     */
    public static class ImportResult {
        public long profileCount;
        public long noteCount;
        public long tagCount;
        public long attachmentCount;
        public long fileCount;
        public long medicineCount;
        public long reminderCount;
        public long intakeCount;
    }

    private static class ProfileRecord {
        final Profile profile;
        final List<NoteRecord> notes;

        ProfileRecord(Profile profile, List<NoteRecord> notes) {
            this.profile = profile;
            this.notes = notes;
        }
    }

    private static class NoteRecord {
        final Note note;
        final List<NoteTag> tags;
        final List<AttachmentRecord> attachments;
        final List<MedicineRecord> medicines;

        NoteRecord(Note note, List<NoteTag> tags, List<AttachmentRecord> attachments,
                   List<MedicineRecord> medicines) {
            this.note = note;
            this.tags = tags;
            this.attachments = attachments;
            this.medicines = medicines;
        }
    }

    private static class AttachmentRecord {
        final NoteAttachment noteAttachment;
        final List<NoteAttachmentFile> files;

        AttachmentRecord(NoteAttachment noteAttachment, List<NoteAttachmentFile> files) {
            this.noteAttachment = noteAttachment;
            this.files = files;
        }
    }

    private static class MedicineRecord {
        final Medicine medicine;
        final List<MedicineReminder> reminders;
        final List<MedicineIntake> intakes;

        MedicineRecord(Medicine medicine, List<MedicineReminder> reminders, List<MedicineIntake> intakes) {
            this.medicine = medicine;
            this.reminders = reminders;
            this.intakes = intakes;
        }
    }
}
