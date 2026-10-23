package m.co.rh.id.a_medic_log.base.repository;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class NoteRepository {

    private final ProviderValue<AppDatabase> mAppDatabase;
    private final ProviderValue<NoteDao> mNoteDao;
    private final ProviderValue<NoteAttachmentDao> mNoteAttachmentDao;
    private final ProviderValue<NoteAttachmentFileDao> mNoteAttachmentFileDao;
    private final ProviderValue<MedicineDao> mMedicineDao;
    private final ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private final ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private final ProviderValue<NoteTagDao> mNoteTagDao;

    public NoteRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteAttachmentDao = provider.lazyGet(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = provider.lazyGet(NoteAttachmentFileDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
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
                    noteTag.id = mNoteTagDao.get().insert(noteTag);
                }
            }
            List<NoteAttachmentState> noteAttachmentStates = noteState.getNoteAttachmentStates();
            if (noteAttachmentStates != null && !noteAttachmentStates.isEmpty()) {
                for (NoteAttachmentState noteAttachmentState : noteAttachmentStates) {
                    noteAttachmentState.setNoteId(noteId);
                    NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
                    noteAttachment.id = mNoteAttachmentDao.get().insert(noteAttachmentState.getNoteAttachment());
                    List<NoteAttachmentFile> files =
                            noteAttachmentState.getNoteAttachmentFiles();
                    if (files != null && !files.isEmpty()) {
                        for (NoteAttachmentFile file : files) {
                            file.attachmentId = noteAttachmentState.getId();
                            file.id = mNoteAttachmentFileDao.get().insert(file);
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
                            medicineReminder.id = mMedicineReminderDao.get().insert(medicineReminder);
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

    /**
     * Loads a fresh note state with all its nested data (tags, attachments,
     * medicines and their reminders); the returned state is never shared
     */
    public NoteState queryNoteInfo(long noteId) {
        Note note = mNoteDao.get().findNoteById(noteId);
        NoteState noteState = new NoteState();
        noteState.updateNote(note);
        List<NoteTag> noteTags = mNoteTagDao.get().findNoteTagsByNoteId(noteId);
        noteState.updateNoteTagSet(noteTags);
        noteState.updateNoteAttachments(queryNoteAttachmentStatesByNoteId(noteId));
        noteState.updateMedicineStates(queryMedicineStatesByNoteId(noteId));
        return noteState;
    }

    /**
     * Loads the medicine states of a note with their reminders; the returned
     * list is fresh and never shared with any state
     */
    public List<MedicineState> queryMedicineStatesByNoteId(long noteId) {
        List<Medicine> medicineList = mMedicineDao.get().findMedicinesByNoteId(noteId);
        ArrayList<MedicineState> medicineStates = new ArrayList<>();
        for (Medicine medicine : medicineList) {
            List<MedicineReminder> medicineReminders = mMedicineReminderDao.get().findMedicineRemindersByMedicineId(medicine.id);
            MedicineState medicineState = new MedicineState();
            medicineState.updateMedicine(medicine);
            medicineState.updateMedicineReminderList(medicineReminders);
            medicineStates.add(medicineState);
        }
        return medicineStates;
    }

    /**
     * Loads the note attachment states of a note with their files; the
     * returned list is fresh and never shared with any state
     */
    public List<NoteAttachmentState> queryNoteAttachmentStatesByNoteId(long noteId) {
        List<NoteAttachment> noteAttachments = mNoteAttachmentDao.get().findNoteAttachmentsByNoteId(noteId);
        List<NoteAttachmentState> noteAttachmentStates = new ArrayList<>();
        for (NoteAttachment noteAttachment : noteAttachments) {
            List<NoteAttachmentFile> noteAttachmentFiles = mNoteAttachmentFileDao.get().findNoteAttachmentFilesByAttachmentId(noteAttachment.id);
            NoteAttachmentState noteAttachmentState = new NoteAttachmentState();
            noteAttachmentState.updateNoteAttachment(noteAttachment);
            noteAttachmentState.updateNoteAttachmentFileList(noteAttachmentFiles);
            noteAttachmentStates.add(noteAttachmentState);
        }
        return noteAttachmentStates;
    }

    public TreeSet<NoteTag> findNoteTagsByNoteId(long noteId) {
        List<NoteTag> noteTags = mNoteTagDao.get().findNoteTagsByNoteId(noteId);
        return new TreeSet<>(noteTags);
    }

    public LinkedHashSet<String> searchNoteTag(String search) {
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
        List<NoteTag> noteTags = mNoteTagDao.get().searchNoteTag(search);
        if (!noteTags.isEmpty()) {
            for (NoteTag noteTag : noteTags) {
                linkedHashSet.add(noteTag.tag);
            }
        }
        return linkedHashSet;
    }

    /**
     * Counts the records owned by the given note; medicine, reminder and
     * attachment counts come from the note state, intakes are not part of
     * the state (paged history) so they are counted via a DAO query
     * <p>
     * Unlike ProfileRepository.queryProfileCounts (direct DAO counts, an
     * assembled full profile state would be too expensive), the note state
     * is small enough to assemble here and its attachment file rows hold
     * only file-name metadata
     */
    public NoteCounts queryNoteCounts(long noteId) {
        NoteState noteState = queryNoteInfo(noteId);
        int medicineCount = noteState.getMedicineList().size();
        int reminderCount = 0;
        for (MedicineState medicineState : noteState.getMedicineList()) {
            reminderCount += medicineState.getMedicineReminderList().size();
        }
        int attachmentCount = noteState.getNoteAttachmentStates().size();
        int intakeCount = mMedicineIntakeDao.get().countMedicineIntakesByNoteId(noteId);
        return new NoteCounts(medicineCount, reminderCount, attachmentCount, intakeCount);
    }

    /**
     * Counts of the records owned by a note
     */
    public static class NoteCounts {
        public final int medicineCount;
        public final int reminderCount;
        public final int attachmentCount;
        public final int intakeCount;

        public NoteCounts(int medicineCount, int reminderCount,
                         int attachmentCount, int intakeCount) {
            this.medicineCount = medicineCount;
            this.reminderCount = reminderCount;
            this.attachmentCount = attachmentCount;
            this.intakeCount = intakeCount;
        }
    }
}
