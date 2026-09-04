package m.co.rh.id.a_medic_log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Shared helpers for the export/import instrumented tests:
 * zip archive inspection and rebuilding, temp file leftover detection
 * and the common dao backed seed builders.
 */
public final class ExportImportTestUtils {

    /** Zip entry names of the export archive, mirrored from ExportArchiveCmd. */
    public static final String ENTRY_EXPORT_JSON = "export.json";
    public static final String ENTRY_PROFILE_PREFIX = "profiles/";
    public static final String ENTRY_PROFILE_JSON_SUFFIX = ".json";

    private static final int COPY_BUFFER_SIZE = 2048;

    private ExportImportTestUtils() {
    }

    // ------------------------------------------------------------------ zip helpers

    /**
     * @return the zip entry name of the per-profile JSON file
     * of the given file profile id (profiles/&lt;id&gt;.json)
     */
    public static String profileEntryName(long fileProfileId) {
        return ENTRY_PROFILE_PREFIX + fileProfileId + ENTRY_PROFILE_JSON_SUFFIX;
    }

    /**
     * @return the names of every entry of the given zip archive
     */
    public static List<String> zipEntryNames(File zipFile) throws IOException {
        List<String> entryNames = new ArrayList<>();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(zipFile));
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                entryNames.add(zipEntry.getName());
            }
        }
        return entryNames;
    }

    /**
     * @return the full UTF-8 text content of the first zip entry with the given name
     * @throws IOException when the archive cannot be read or has no such entry
     */
    public static String readZipEntryText(File zipFile, String entryName) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(zipFile));
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (entryName.equals(zipEntry.getName())) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buff = new byte[COPY_BUFFER_SIZE];
                    int b = zipInputStream.read(buff);
                    while (b != -1) {
                        byteArrayOutputStream.write(buff, 0, b);
                        b = zipInputStream.read(buff);
                    }
                    return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("Zip archive " + zipFile.getName() + " has no entry " + entryName);
    }

    /**
     * Raw copy every entry of sourceZip into targetZip except the given excluded
     * entry names (the entry bytes are copied as-is).
     *
     * @return the list of copied entry names
     */
    public static List<String> copyZipWithoutEntries(File sourceZip, File targetZip,
                                                     String... excludedEntryNames) throws IOException {
        Set<String> excludedEntryNameSet = new HashSet<>(Arrays.asList(excludedEntryNames));
        List<String> copiedEntryNames = new ArrayList<>();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(sourceZip));
             ZipInputStream zipInputStream = new ZipInputStream(inputStream);
             OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(targetZip));
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                if (excludedEntryNameSet.contains(entryName)) {
                    continue;
                }
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                byte[] buff = new byte[COPY_BUFFER_SIZE];
                int b = zipInputStream.read(buff);
                while (b != -1) {
                    zipOutputStream.write(buff, 0, b);
                    b = zipInputStream.read(buff);
                }
                zipOutputStream.closeEntry();
                copiedEntryNames.add(entryName);
            }
        }
        return copiedEntryNames;
    }

    public static void writeBytes(File outFile, byte[] bytes) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(outFile)) {
            outputStream.write(bytes);
        }
    }

    // ------------------------------------------------------- temp leftover detection

    /**
     * Paths of every *.zip/*.xlsx file below the FileHelper temp root (cache/tmp),
     * used to detect leftover temporary export files.
     */
    public static List<String> listTempArchivePaths(File tempFileRoot) {
        List<String> archivePaths = new ArrayList<>();
        collectTempArchivePaths(tempFileRoot, archivePaths);
        Collections.sort(archivePaths);
        return archivePaths;
    }

    private static void collectTempArchivePaths(File dir, List<String> archivePaths) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectTempArchivePaths(file, archivePaths);
            } else {
                String fileName = file.getName();
                if (fileName.endsWith(".zip") || fileName.endsWith(".xlsx")) {
                    archivePaths.add(file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Absolute paths of every regular file below the given directory (recursively),
     * sorted, used to snapshot the FileHelper attachment image/thumbnail directories
     * before and after an import (the thumbnail directory is a subdirectory of the
     * image directory, so one snapshot of the image parent covers both).
     * Returns an empty list when the directory does not exist.
     */
    public static List<String> collectChildFilePaths(File dir) {
        List<String> filePaths = new ArrayList<>();
        collectChildFilePaths(dir, filePaths);
        Collections.sort(filePaths);
        return filePaths;
    }

    private static void collectChildFilePaths(File dir, List<String> filePaths) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectChildFilePaths(file, filePaths);
            } else {
                filePaths.add(file.getAbsolutePath());
            }
        }
    }

    // ------------------------------------------------------------------- assertions

    /**
     * @return true when the throwable itself or any of its causes is an IOException
     */
    public static boolean isOrCausedByIOException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * @return true when the throwable itself or any of its causes is an IOException
     * whose message contains the given text
     */
    public static boolean hasIOExceptionWithMessage(Throwable throwable, String messagePart) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof IOException && cause.getMessage() != null
                    && cause.getMessage().contains(messagePart)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    // ---------------------------------------------------------------- seed builders

    public static Profile createProfile(ProfileDao profileDao, String name, String about) {
        Profile profile = new Profile();
        profile.name = name;
        profile.about = about;
        profile.id = profileDao.insert(profile);
        return profile;
    }

    public static Note createNote(NoteDao noteDao, Long profileId, String content) {
        Note note = new Note();
        note.profileId = profileId;
        note.content = content;
        note.entryDateTime = new Date();
        note.id = noteDao.insert(note);
        return note;
    }

    public static Medicine createMedicine(MedicineDao medicineDao, Long noteId,
                                          String name, String description) {
        Medicine medicine = new Medicine();
        medicine.noteId = noteId;
        medicine.name = name;
        medicine.description = description;
        medicine.id = medicineDao.insert(medicine);
        return medicine;
    }

    /**
     * Insert a medicine reminder with the given schedule configuration.
     * Note: reminderDays only affects the UI, the WorkManager enqueue is driven
     * by reminderEnabled and startDateTime alone.
     */
    public static MedicineReminder createMedicineReminder(MedicineReminderDao medicineReminderDao,
                                                          Long medicineId, String message,
                                                          Date startDateTime,
                                                          boolean reminderEnabled,
                                                          Integer... reminderDays) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.medicineId = medicineId;
        medicineReminder.message = message;
        medicineReminder.startDateTime = startDateTime;
        medicineReminder.reminderEnabled = reminderEnabled;
        medicineReminder.reminderDays = new LinkedHashSet<>(Arrays.asList(reminderDays));
        medicineReminder.id = medicineReminderDao.insert(medicineReminder);
        return medicineReminder;
    }
}
