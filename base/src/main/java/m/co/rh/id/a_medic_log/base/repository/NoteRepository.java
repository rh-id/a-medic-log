package m.co.rh.id.a_medic_log.base.repository;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

import java.util.List;
import java.util.Set;

public class NoteRepository {

    private final ProviderValue<AppDatabase> mAppDatabase;
    private final ProviderValue<NoteDao> mNoteDao;
    private final ProviderValue<NoteAttachmentDao> mNoteAttachmentDao;
    private final ProviderValue<NoteAttachmentFileDao> mNoteAttachmentFileDao;
    private final ProviderValue<MedicineDao> mMedicineDao;
    private final ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private final ProviderValue<NoteTagDao> mNoteTagDao;

    public NoteRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteAttachmentDao = provider.lazyGet(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = provider.lazyGet(NoteAttachmentFileDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mNoteTagDao = provider.lazyGet(NoteTagDao.class);
    }

    public void insertNote(NoteState noteState) {
        mAppDatabase.get().runInTransaction(() -> {
            Note note = noteState.getNote();
            long noteId = mNoteDao.get().insert(note);
            note.id = noteId;
            Set<NoteTag> noteTags = noteState.getNoteTagSet();
            if (noteTags != null && !noteTags.isEmpty()) {
                for (NoteTag noteTag : noteTags) {
                    noteTag.noteId = noteId;
                    mNoteTagDao.get().insert(noteTag);
                }
            }
            List<NoteAttachmentState> noteAttachmentStates = noteState.getNoteAttachmentStates();
            if (noteAttachmentStates != null && !noteAttachmentStates.isEmpty()) {
                for (NoteAttachmentState noteAttachmentState : noteAttachmentStates) {
                    noteAttachmentState.setNoteId(noteId);
                    mNoteAttachmentDao.get().insert(noteAttachmentState.getNoteAttachment());
                    List<m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile> files =
                            noteAttachmentState.getNoteAttachmentFiles();
                    if (files != null && !files.isEmpty()) {
                        for (m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile file : files) {
                            file.attachmentId = noteAttachmentState.getId();
                            mNoteAttachmentFileDao.get().insert(file);
                        }
                    }
                }
            }
            List<MedicineState> medicineStates = noteState.getMedicineList();
            if (medicineStates != null && !medicineStates.isEmpty()) {
                for (MedicineState medicineState : medicineStates) {
                    medicineState.setNoteId(noteId);
                    Medicine medicine = medicineState.getMedicine();
                    long medicineId = mMedicineDao.get().insert(medicine);
                    medicine.id = medicineId;
                    List<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                    if (medicineReminders != null && !medicineReminders.isEmpty()) {
                        for (MedicineReminder medicineReminder : medicineReminders) {
                            medicineReminder.medicineId = medicineId;
                            mMedicineReminderDao.get().insert(medicineReminder);
                        }
                    }
                }
            }
        });
    }

    public void updateNote(NoteState noteState) {
        mAppDatabase.get().runInTransaction(() -> {
            mNoteDao.get().update(noteState.getNote());
        });
    }

    public void deleteNote(NoteState noteState) {
        mAppDatabase.get().runInTransaction(() -> {
            mNoteDao.get().delete(noteState.getNote());
        });
    }
}