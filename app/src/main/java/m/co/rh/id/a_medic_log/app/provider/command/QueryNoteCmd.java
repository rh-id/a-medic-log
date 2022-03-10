package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
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

public class QueryNoteCmd {
    protected ExecutorService mExecutorService;
    protected NoteDao mNoteDao;
    protected MedicineDao mMedicineDao;

    public QueryNoteCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mNoteDao = provider.get(NoteDao.class);
        mMedicineDao = provider.get(MedicineDao.class);
    }

    public Single<NoteState> queryNoteInfo(long noteId) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            Note note = mNoteDao.findNoteById(noteId);
            NoteState noteState = new NoteState();
            noteState.updateNote(note);
            noteState = queryNoteInfo(noteState).blockingGet();
            return noteState;
        }));
    }

    public Single<NoteState> queryNoteInfo(NoteState noteState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            Long noteId = noteState.getNoteId();
            if (noteId == null) return noteState;
            Note note = mNoteDao.findNoteById(noteId);
            noteState.updateNote(note);
            List<NoteTag> noteTags = mNoteDao.findNoteTagsByNoteId(noteId);
            noteState.updateNoteTagSet(noteTags);
            queryNoteAttachmentStateList(noteState);
            queryMedicineStateList(noteState);
            return noteState;
        }));
    }

    private void queryMedicineStateList(NoteState noteState) {
        Long noteId = noteState.getNoteId();
        List<Medicine> medicineList = mMedicineDao.findMedicinesByNoteId(noteId);
        if (!medicineList.isEmpty()) {
            ArrayList<MedicineState> medicineStates = new ArrayList<>();
            for (Medicine medicine : medicineList) {
                List<MedicineReminder> medicineReminders = mMedicineDao.findMedicineRemindersByMedicineId(medicine.id);
                MedicineState medicineState = new MedicineState();
                medicineState.updateMedicine(medicine);
                medicineState.updateMedicineReminderList(medicineReminders);
                medicineStates.add(medicineState);
            }
            noteState.updateMedicineStates(medicineStates);
        }
    }

    private void queryNoteAttachmentStateList(NoteState noteState) {
        Long noteId = noteState.getNoteId();
        List<NoteAttachment> noteAttachments = mNoteDao.findNoteAttachmentsByNoteId(noteId);
        if (!noteAttachments.isEmpty()) {
            List<NoteAttachmentState> noteAttachmentStates = new ArrayList<>();
            for (NoteAttachment noteAttachment : noteAttachments) {
                List<NoteAttachmentFile> noteAttachmentFiles = mNoteDao.findNoteAttachmentFilesByAttachmentId(noteAttachment.id);
                NoteAttachmentState noteAttachmentState = new NoteAttachmentState();
                noteAttachmentState.updateNoteAttachment(noteAttachment);
                noteAttachmentState.updateNoteAttachmentFileList(noteAttachmentFiles);
                noteAttachmentStates.add(noteAttachmentState);
            }
            noteState.updateNoteAttachments(noteAttachmentStates);
        }
    }

    public Single<List<NoteAttachmentState>> queryNoteAttachmentInfo(NoteState noteState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            queryNoteAttachmentStateList(noteState);
            return noteState.getNoteAttachmentStates();
        }));
    }

    public Single<List<MedicineState>> queryMedicineInfo(NoteState noteState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            queryMedicineStateList(noteState);
            return noteState.getMedicineList();
        }));
    }

    public Single<TreeSet<NoteTag>> queryNoteTag(long noteId) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            List<NoteTag> noteTags = mNoteDao.findNoteTagsByNoteId(noteId);
            return new TreeSet<>(noteTags);
        }));
    }

    public Single<LinkedHashSet<String>> searchNoteTag(String search) {
        return Single.fromFuture(mExecutorService.submit(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<NoteTag> noteTags = mNoteDao.searchNoteTag(search);
            if (!noteTags.isEmpty()) {
                for (NoteTag noteTag : noteTags) {
                    linkedHashSet.add(noteTag.tag);
                }
            }
            return linkedHashSet;
        }));
    }
}
