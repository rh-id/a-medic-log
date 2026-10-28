package m.co.rh.id.a_medic_log.app.provider.component;

import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Value holder of the complete export data of a single profile with all of
 * its notes, attachments, medicines, reminders and intakes.
 * Assembled by {@link ProfileExportDataGenerator}; rendered by
 * {@link ExcelExporter} and {@link CsvArchiveExporter}, and consumed by
 * {@link m.co.rh.id.a_medic_log.app.provider.command.ExportArchiveCmd}.
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
