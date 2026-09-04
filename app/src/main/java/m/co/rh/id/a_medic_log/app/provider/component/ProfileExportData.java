package m.co.rh.id.a_medic_log.app.provider.component;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Complete export data of a single profile with all of its notes, attachments,
 * medicines, reminders and intakes.
 * Assembled from the database queries and shared by {@link m.co.rh.id.a_medic_log.app.provider.command.ExportArchiveCmd}
 * and {@link m.co.rh.id.a_medic_log.app.provider.command.ExportSpreadsheetCmd} exporters.
 */
public class ProfileExportData {
    private final Profile mProfile;
    private final List<NoteExportData> mNotes;

    public ProfileExportData(Profile profile, List<NoteExportData> notes) {
        mProfile = profile;
        mNotes = notes;
    }

    public Profile getProfile() {
        return mProfile;
    }

    public List<NoteExportData> getNotes() {
        return mNotes;
    }

    /**
     * Assemble the complete export data of a profile from the database.
     * This performs blocking database reads, must be called on a background thread.
     *
     * @param profile the profile to assemble, must not be null
     * @return complete export data of the profile
     */
    public static ProfileExportData assemble(Profile profile, NoteDao noteDao, NoteTagDao noteTagDao,
                                             NoteAttachmentDao noteAttachmentDao,
                                             NoteAttachmentFileDao noteAttachmentFileDao,
                                             MedicineDao medicineDao,
                                             MedicineReminderDao medicineReminderDao,
                                             MedicineIntakeDao medicineIntakeDao) {
        List<Note> notes = noteDao.findNotesByProfileId(profile.id);
        List<NoteExportData> noteExportDataList = new ArrayList<>(notes.size());
        for (Note note : notes) {
            List<NoteTag> noteTags = noteTagDao.findNoteTagsByNoteId(note.id);
            List<NoteAttachmentExportData> attachments = new ArrayList<>();
            List<NoteAttachment> noteAttachments = noteAttachmentDao.findNoteAttachmentsByNoteId(note.id);
            for (NoteAttachment noteAttachment : noteAttachments) {
                List<NoteAttachmentFile> noteAttachmentFiles =
                        noteAttachmentFileDao.findNoteAttachmentFilesByAttachmentId(noteAttachment.id);
                attachments.add(new NoteAttachmentExportData(noteAttachment, noteAttachmentFiles));
            }
            List<MedicineExportData> medicines = new ArrayList<>();
            List<Medicine> medicineList = medicineDao.findMedicinesByNoteId(note.id);
            for (Medicine medicine : medicineList) {
                List<MedicineReminder> medicineReminders =
                        medicineReminderDao.findMedicineRemindersByMedicineId(medicine.id);
                List<MedicineIntake> medicineIntakes =
                        medicineIntakeDao.findMedicineIntakesByMedicineId(medicine.id);
                medicines.add(new MedicineExportData(medicine, medicineReminders, medicineIntakes));
            }
            noteExportDataList.add(new NoteExportData(note, noteTags, attachments, medicines));
        }
        return new ProfileExportData(profile, noteExportDataList);
    }

    /**
     * Format a date time as ISO date string, ex: 2026-09-02T08:05:00+0700
     *
     * @param date the date to format
     * @return ISO date string, or empty string when date is null
     */
    public static String formatIsoDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(date);
    }

    /**
     * Build a single line human readable summary of a medicine reminder,
     * ex: "Mon,Tue 08:00 — take 1 pill"
     *
     * @param medicineReminder the reminder to summarize
     * @return reminder summary string
     */
    public static String buildReminderSummary(MedicineReminder medicineReminder) {
        if (medicineReminder == null) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        LinkedHashSet<Integer> reminderDays = medicineReminder.reminderDays;
        if (reminderDays != null && !reminderDays.isEmpty()) {
            String[] shortWeekDays = DateFormatSymbols.getInstance().getShortWeekdays();
            boolean firstDay = true;
            for (Integer dayOfWeek : reminderDays) {
                if (dayOfWeek == null || dayOfWeek < Calendar.SUNDAY || dayOfWeek > Calendar.SATURDAY) {
                    continue;
                }
                if (!firstDay) {
                    stringBuilder.append(",");
                }
                stringBuilder.append(shortWeekDays[dayOfWeek]);
                firstDay = false;
            }
            if (!firstDay) {
                stringBuilder.append(" ");
            }
        }
        if (medicineReminder.startDateTime != null) {
            stringBuilder.append(new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(medicineReminder.startDateTime));
        }
        if (medicineReminder.message != null && !medicineReminder.message.isEmpty()) {
            stringBuilder.append(" — ").append(medicineReminder.message);
        }
        return stringBuilder.toString();
    }

    /**
     * Export data of a single note with its tags, attachments and medicines.
     */
    public static class NoteExportData {
        private final Note mNote;
        private final List<NoteTag> mTags;
        private final List<NoteAttachmentExportData> mAttachments;
        private final List<MedicineExportData> mMedicines;

        public NoteExportData(Note note, List<NoteTag> tags,
                              List<NoteAttachmentExportData> attachments,
                              List<MedicineExportData> medicines) {
            mNote = note;
            mTags = tags;
            mAttachments = attachments;
            mMedicines = medicines;
        }

        public Note getNote() {
            return mNote;
        }

        public List<NoteTag> getTags() {
            return mTags;
        }

        public List<NoteAttachmentExportData> getAttachments() {
            return mAttachments;
        }

        public List<MedicineExportData> getMedicines() {
            return mMedicines;
        }
    }

    /**
     * Export data of a single note attachment with its files.
     */
    public static class NoteAttachmentExportData {
        private final NoteAttachment mNoteAttachment;
        private final List<NoteAttachmentFile> mFiles;

        public NoteAttachmentExportData(NoteAttachment noteAttachment, List<NoteAttachmentFile> files) {
            mNoteAttachment = noteAttachment;
            mFiles = files;
        }

        public NoteAttachment getNoteAttachment() {
            return mNoteAttachment;
        }

        public List<NoteAttachmentFile> getFiles() {
            return mFiles;
        }
    }

    /**
     * Export data of a single medicine with its reminders and intakes.
     */
    public static class MedicineExportData {
        private final Medicine mMedicine;
        private final List<MedicineReminder> mReminders;
        private final List<MedicineIntake> mIntakes;

        public MedicineExportData(Medicine medicine, List<MedicineReminder> reminders,
                                  List<MedicineIntake> intakes) {
            mMedicine = medicine;
            mReminders = reminders;
            mIntakes = intakes;
        }

        public Medicine getMedicine() {
            return mMedicine;
        }

        public List<MedicineReminder> getReminders() {
            return mReminders;
        }

        public List<MedicineIntake> getIntakes() {
            return mIntakes;
        }
    }
}
