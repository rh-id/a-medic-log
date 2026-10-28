package m.co.rh.id.a_medic_log.app.provider.component;

import java.util.ArrayList;
import java.util.List;

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
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Assembles the complete {@link ProfileExportData} of a single profile from
 * the database: its notes with their tags, attachments with their files and
 * medicines with their reminders and intakes.
 * Shared by {@link m.co.rh.id.a_medic_log.app.provider.command.ExportArchiveCmd}
 * and {@link m.co.rh.id.a_medic_log.app.provider.command.ExportSpreadsheetCmd}.
 * This component is registered in the provider as a lazy singleton; resolve it
 * with {@link Provider#lazyGet}. Performs blocking database reads, background
 * thread only.
 */
public class ProfileExportDataGenerator {
    private ProviderValue<NoteDao> mNoteDao;
    private ProviderValue<NoteTagDao> mNoteTagDao;
    private ProviderValue<NoteAttachmentDao> mNoteAttachmentDao;
    private ProviderValue<NoteAttachmentFileDao> mNoteAttachmentFileDao;
    private ProviderValue<MedicineDao> mMedicineDao;
    private ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;

    public ProfileExportDataGenerator(Provider provider) {
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteTagDao = provider.lazyGet(NoteTagDao.class);
        mNoteAttachmentDao = provider.lazyGet(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = provider.lazyGet(NoteAttachmentFileDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
    }

    /**
     * Assemble the complete {@link ProfileExportData} of a single profile from
     * the database: its notes with their tags, attachments with their files
     * and medicines with their reminders and intakes.
     * This performs blocking database reads, must be called on a background thread.
     *
     * @param profile the profile to assemble, must not be null
     * @return complete export data of the profile
     */
    public ProfileExportData generate(Profile profile) {
        List<Note> notes = mNoteDao.get().findNotesByProfileId(profile.id);
        List<ProfileExportData.NoteExportData> noteExportDataList = new ArrayList<>(notes.size());
        for (Note note : notes) {
            List<NoteTag> noteTags = mNoteTagDao.get().findNoteTagsByNoteId(note.id);
            List<ProfileExportData.NoteAttachmentExportData> attachments = new ArrayList<>();
            List<NoteAttachment> noteAttachments =
                    mNoteAttachmentDao.get().findNoteAttachmentsByNoteId(note.id);
            for (NoteAttachment noteAttachment : noteAttachments) {
                List<NoteAttachmentFile> noteAttachmentFiles =
                        mNoteAttachmentFileDao.get().findNoteAttachmentFilesByAttachmentId(noteAttachment.id);
                attachments.add(new ProfileExportData.NoteAttachmentExportData(noteAttachment, noteAttachmentFiles));
            }
            List<ProfileExportData.MedicineExportData> medicines = new ArrayList<>();
            List<Medicine> medicineList = mMedicineDao.get().findMedicinesByNoteId(note.id);
            for (Medicine medicine : medicineList) {
                List<MedicineReminder> medicineReminders =
                        mMedicineReminderDao.get().findMedicineRemindersByMedicineId(medicine.id);
                List<MedicineIntake> medicineIntakes =
                        mMedicineIntakeDao.get().findMedicineIntakesByMedicineId(medicine.id);
                medicines.add(new ProfileExportData.MedicineExportData(medicine, medicineReminders, medicineIntakes));
            }
            noteExportDataList.add(new ProfileExportData.NoteExportData(note, noteTags, attachments, medicines));
        }
        return new ProfileExportData(profile, noteExportDataList);
    }
}
