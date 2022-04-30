package m.co.rh.id.a_medic_log.base.state;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.TreeSet;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;

public class NoteState implements Serializable, Cloneable {
    private SerialBehaviorSubject<Note> mNoteSubject;
    private SerialBehaviorSubject<TreeSet<NoteTag>> mNoteTagSetSubject;
    private SerialBehaviorSubject<ArrayList<NoteAttachmentState>> mNoteAttachmentsSubject;
    private SerialBehaviorSubject<ArrayList<MedicineState>> mMedicineListSubject;
    private DateFormat mDateFormat;

    public NoteState() {
        mNoteSubject = new SerialBehaviorSubject<>(new Note());
        mMedicineListSubject = new SerialBehaviorSubject<>(new ArrayList<>());
        mNoteTagSetSubject = new SerialBehaviorSubject<>(new TreeSet<>());
        mNoteAttachmentsSubject = new SerialBehaviorSubject<>(new ArrayList<>());
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    public void setNoteProfileId(long profileId) {
        getNote().profileId = profileId;
    }

    public void setNoteEntryDateTime(Date dateTime) {
        getNote().entryDateTime = dateTime;
    }

    public void updateNoteEntryDateTime(Date dateTime) {
        setNoteEntryDateTime(dateTime);
        mNoteSubject.onNext(mNoteSubject.getValue());
    }

    public void setNoteContent(String content) {
        getNote().content = content;
    }

    public String getNoteEntryDateTimeDisplay() {
        Date date = getNote().entryDateTime;
        if (date == null) return null;
        return mDateFormat.format(date);
    }

    public String getNoteContent() {
        return getNote().content;
    }

    public Note getNote() {
        return mNoteSubject.getValue();
    }

    public void updateNoteTagSet(Collection<NoteTag> noteTags) {
        mNoteTagSetSubject.onNext(new TreeSet<>(noteTags));
    }

    public TreeSet<NoteTag> getNoteTagSet() {
        return mNoteTagSetSubject.getValue();
    }

    public void updateNoteAttachments(Collection<NoteAttachmentState> noteAttachmentStates) {
        mNoteAttachmentsSubject.onNext(new ArrayList<>(noteAttachmentStates));
    }

    public ArrayList<NoteAttachmentState> getNoteAttachmentStates() {
        return mNoteAttachmentsSubject.getValue();
    }

    public Flowable<ArrayList<NoteAttachmentState>> getNoteAttachmentStatesFlow() {
        return Flowable.fromObservable(mNoteAttachmentsSubject.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void addNoteAttachmentState(NoteAttachmentState noteAttachmentState) {
        ArrayList<NoteAttachmentState> noteAttachmentStates = getNoteAttachmentStates();
        noteAttachmentStates.add(noteAttachmentState);
        mNoteAttachmentsSubject.onNext(noteAttachmentStates);
    }

    public void updateNoteAttachmentState(int index, NoteAttachmentState noteAttachmentState) {
        ArrayList<NoteAttachmentState> noteAttachmentStates = getNoteAttachmentStates();
        noteAttachmentStates.remove(index);
        noteAttachmentStates.add(index, noteAttachmentState);
        mNoteAttachmentsSubject.onNext(noteAttachmentStates);
    }

    public void removeNoteAttachmentState(int index) {
        ArrayList<NoteAttachmentState> noteAttachmentStates = getNoteAttachmentStates();
        noteAttachmentStates.remove(index);
        mNoteAttachmentsSubject.onNext(noteAttachmentStates);
    }

    public ArrayList<MedicineState> getMedicineList() {
        return mMedicineListSubject.getValue();
    }

    public Date getNoteEntryDateTime() {
        return getNote().entryDateTime;
    }

    public void addMedicineList(MedicineState medicineState) {
        ArrayList<MedicineState> medicineStates = getMedicineList();
        medicineStates.add(medicineState);
        mMedicineListSubject.onNext(medicineStates);
    }

    public void updateMedicineList(int index, MedicineState medicineState) {
        ArrayList<MedicineState> medicineStates = getMedicineList();
        medicineStates.remove(index);
        medicineStates.add(index, medicineState);
        mMedicineListSubject.onNext(medicineStates);
    }

    public void removeMedicineList(int index) {
        ArrayList<MedicineState> medicineStates = getMedicineList();
        medicineStates.remove(index);
        mMedicineListSubject.onNext(medicineStates);
    }

    public Flowable<Note> getNoteFlow() {
        return Flowable.fromObservable(mNoteSubject.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Flowable<TreeSet<NoteTag>> getNoteTagSetFlow() {
        return Flowable.fromObservable(mNoteTagSetSubject.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Flowable<ArrayList<MedicineState>> getMedicineListFlow() {
        return Flowable.fromObservable(mMedicineListSubject.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void updateNote(Note note) {
        mNoteSubject.onNext(note);
    }

    public void updateMedicineStates(Collection<MedicineState> medicineStates) {
        mMedicineListSubject.onNext(new ArrayList<>(medicineStates));
    }

    public void addNoteTag(NoteTag newNoteTag) {
        TreeSet<NoteTag> noteTags = mNoteTagSetSubject.getValue();
        noteTags.add(newNoteTag);
        mNoteTagSetSubject.onNext(noteTags);
    }

    public Long getProfileId() {
        return getNote().profileId;
    }

    public Long getNoteId() {
        return getNote().id;
    }

    public void setNoteId(Long noteId) {
        getNote().id = noteId;
    }

    @Override
    public NoteState clone() {
        NoteState noteState = new NoteState();
        Note note = mNoteSubject.getValue();
        if (note != null) {
            note = note.clone();
        }
        noteState.updateNote(note);
        TreeSet<NoteTag> noteTags = mNoteTagSetSubject.getValue();
        if (noteTags != null && !noteTags.isEmpty()) {
            TreeSet<NoteTag> noteTagsClone = new TreeSet<>();
            for (NoteTag noteTag : noteTags) {
                noteTagsClone.add(noteTag.clone());
            }
            noteTags = noteTagsClone;
        } else {
            noteTags = new TreeSet<>();
        }
        noteState.updateNoteTagSet(noteTags);
        ArrayList<MedicineState> medicineStates = mMedicineListSubject.getValue();
        if (medicineStates != null && !medicineStates.isEmpty()) {
            ArrayList<MedicineState> medicineStatesClone = new ArrayList<>(medicineStates.size());
            for (MedicineState medicineState : medicineStates) {
                medicineStatesClone.add(medicineState.clone());
            }
            medicineStates = medicineStatesClone;
        } else {
            medicineStates = new ArrayList<>();
        }
        noteState.updateMedicineStates(medicineStates);
        return noteState;
    }
}
