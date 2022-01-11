package m.co.rh.id.a_medic_log.base.state;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.entity.Note;

public class NoteState implements Serializable, Cloneable {
    private transient BehaviorSubject<Note> mNoteSubject;
    private transient BehaviorSubject<ArrayList<MedicineState>> mMedicineListSubject;
    private transient DateFormat mDateFormat;

    public NoteState() {
        mNoteSubject = BehaviorSubject.createDefault(new Note());
        mMedicineListSubject = BehaviorSubject.createDefault(new ArrayList<>());
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
        return Flowable.fromObservable(mNoteSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ArrayList<MedicineState>> getMedicineListFlow() {
        return Flowable.fromObservable(mMedicineListSubject, BackpressureStrategy.BUFFER);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(mNoteSubject.getValue());
        out.writeObject(mMedicineListSubject.getValue());
        out.writeObject(mDateFormat);
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        Note note = (Note) in.readObject();
        ArrayList<MedicineState> medicineStates = (ArrayList<MedicineState>) in.readObject();
        mDateFormat = (DateFormat) in.readObject();
        mNoteSubject = BehaviorSubject.createDefault(note);
        mMedicineListSubject = BehaviorSubject.createDefault(medicineStates);
    }

    public void updateNote(Note note) {
        mNoteSubject.onNext(note);
    }

    public void updateMedicineStates(List<MedicineState> medicineStates) {
        mMedicineListSubject.onNext(new ArrayList<>(medicineStates));
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
        ArrayList<MedicineState> medicineStates = mMedicineListSubject.getValue();
        if (medicineStates != null && !medicineStates.isEmpty()) {
            ArrayList<MedicineState> medicineStatesClone = new ArrayList<>();
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
