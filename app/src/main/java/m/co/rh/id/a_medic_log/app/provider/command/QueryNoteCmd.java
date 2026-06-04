package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;

public class QueryNoteCmd {
    protected ExecutorService mExecutorService;
    protected ProfileDao mProfileDao;
    protected NoteDao mNoteDao;
    protected NoteTagDao mNoteTagDao;
    protected NoteAttachmentDao mNoteAttachmentDao;
    protected NoteAttachmentFileDao mNoteAttachmentFileDao;
    protected MedicineDao mMedicineDao;
    protected MedicineReminderDao mMedicineReminderDao;

    public QueryNoteCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mProfileDao = provider.get(ProfileDao.class);
        mNoteDao = provider.get(NoteDao.class);
        mNoteTagDao = provider.get(NoteTagDao.class);
        mNoteAttachmentDao = provider.get(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = provider.get(NoteAttachmentFileDao.class);
        mMedicineDao = provider.get(MedicineDao.class);
        mMedicineReminderDao = provider.get(MedicineReminderDao.class);
    }

    public Single<NoteState> queryNoteInfo(long noteId) {
        return Single.fromCallable(() -> {
            Note note = mNoteDao.findNoteById(noteId);
            NoteState noteState = new NoteState();
            noteState.updateNote(note);
            return noteState;
        }).subscribeOn(Schedulers.from(mExecutorService))
        .flatMap(this::queryNoteInfo);
    }

    public Single<NoteState> queryNoteInfo(NoteState noteState) {
        return Single.fromCallable(() -> {
            Long noteId = noteState.getNoteId();
            if (noteId == null) return noteState;
            Note note = mNoteDao.findNoteById(noteId);
            noteState.updateNote(note);
            List<NoteTag> noteTags = mNoteTagDao.findNoteTagsByNoteId(noteId);
            noteState.updateNoteTagSet(noteTags);
            queryNoteAttachmentStateList(noteState);
            queryMedicineStateList(noteState);
            return noteState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    private void queryMedicineStateList(NoteState noteState) {
        Long noteId = noteState.getNoteId();
        List<Medicine> medicineList = mMedicineDao.findMedicinesByNoteId(noteId);
        if (!medicineList.isEmpty()) {
            ArrayList<MedicineState> medicineStates = new ArrayList<>();
            for (Medicine medicine : medicineList) {
                List<MedicineReminder> medicineReminders = mMedicineReminderDao.findMedicineRemindersByMedicineId(medicine.id);
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
        List<NoteAttachment> noteAttachments = mNoteAttachmentDao.findNoteAttachmentsByNoteId(noteId);
        if (!noteAttachments.isEmpty()) {
            List<NoteAttachmentState> noteAttachmentStates = new ArrayList<>();
            for (NoteAttachment noteAttachment : noteAttachments) {
                List<NoteAttachmentFile> noteAttachmentFiles = mNoteAttachmentFileDao.findNoteAttachmentFilesByAttachmentId(noteAttachment.id);
                NoteAttachmentState noteAttachmentState = new NoteAttachmentState();
                noteAttachmentState.updateNoteAttachment(noteAttachment);
                noteAttachmentState.updateNoteAttachmentFileList(noteAttachmentFiles);
                noteAttachmentStates.add(noteAttachmentState);
            }
            noteState.updateNoteAttachments(noteAttachmentStates);
        }
    }

    public Single<List<NoteAttachmentState>> queryNoteAttachmentInfo(NoteState noteState) {
        return Single.fromCallable(() -> {
            queryNoteAttachmentStateList(noteState);
            return (List<NoteAttachmentState>) noteState.getNoteAttachmentStates();
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<MedicineState>> queryMedicineInfo(NoteState noteState) {
        return Single.fromCallable(() -> {
            queryMedicineStateList(noteState);
            return (List<MedicineState>) noteState.getMedicineList();
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<TreeSet<NoteTag>> queryNoteTag(long noteId) {
        return Single.fromCallable(() -> {
            List<NoteTag> noteTags = mNoteTagDao.findNoteTagsByNoteId(noteId);
            return new TreeSet<>(noteTags);
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<String>> searchNoteTag(String search) {
        return Single.fromCallable(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<NoteTag> noteTags = mNoteTagDao.searchNoteTag(search);
            if (!noteTags.isEmpty()) {
                for (NoteTag noteTag : noteTags) {
                    linkedHashSet.add(noteTag.tag);
                }
            }
            return linkedHashSet;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<String> createShareMedicineText(NoteState mNoteState) {
        return Single.fromCallable(() -> {
            Long profileId = mNoteState.getProfileId();
            Profile profile;
            if (profileId != null) {
                profile = mProfileDao.findProfileById(profileId);
            } else {
                profile = null;
            }
            StringBuilder stringBuilder = new StringBuilder();
            if (profile != null) {
                stringBuilder.append(profile.name);
                stringBuilder.append(" ");
            }
            stringBuilder.append(mNoteState.getNoteEntryDateTimeDisplay());
            List<MedicineState> medicineStates = mNoteState.getMedicineList();
            if (!medicineStates.isEmpty()) {
                stringBuilder.append("\n\n");
                int size = medicineStates.size();
                for (int i = 0; i < size; i++, stringBuilder.append("\n")) {
                    stringBuilder.append(i + 1);
                    stringBuilder.append(". ");
                    MedicineState medicineState = medicineStates.get(i);
                    stringBuilder.append(medicineState.getMedicineName());
                }
            }
            return stringBuilder.toString();
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
