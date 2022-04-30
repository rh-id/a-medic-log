package m.co.rh.id.a_medic_log.base.state;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;

public class NoteAttachmentState implements Serializable, Cloneable {
    private SerialBehaviorSubject<NoteAttachment> mNoteAttachmentSubject;
    private SerialBehaviorSubject<ArrayList<NoteAttachmentFile>> mNoteAttachmentFileSetSubject;

    public NoteAttachmentState() {
        mNoteAttachmentSubject = new SerialBehaviorSubject<>(new NoteAttachment());
        mNoteAttachmentFileSetSubject = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void updateNoteAttachment(NoteAttachment noteAttachment) {
        mNoteAttachmentSubject.onNext(noteAttachment);
    }

    public void updateNoteAttachmentFileList(Collection<NoteAttachmentFile> noteAttachmentFiles) {
        mNoteAttachmentFileSetSubject.onNext(new ArrayList<>(noteAttachmentFiles));
    }

    public void addNoteAttachmentFile(NoteAttachmentFile noteAttachmentFile) {
        ArrayList<NoteAttachmentFile> noteAttachmentFiles = mNoteAttachmentFileSetSubject.getValue();
        if (noteAttachmentFiles.add(noteAttachmentFile)) {
            mNoteAttachmentFileSetSubject.onNext(noteAttachmentFiles);
        }
    }

    public void deleteNoteAttachmentFile(NoteAttachmentFile noteAttachmentFile) {
        ArrayList<NoteAttachmentFile> noteAttachmentFiles = mNoteAttachmentFileSetSubject.getValue();
        if (noteAttachmentFiles.remove(noteAttachmentFile)) {
            mNoteAttachmentFileSetSubject.onNext(noteAttachmentFiles);
        }
    }

    public NoteAttachment getNoteAttachment() {
        return mNoteAttachmentSubject.getValue();
    }

    public String getName() {
        return getNoteAttachment().name;
    }

    public void setName(String name) {
        getNoteAttachment().name = name;
    }

    public Long getId() {
        return getNoteAttachment().id;
    }

    public void setNoteId(long noteId) {
        getNoteAttachment().noteId = noteId;
    }

    public Date getNoteAttachmentCreatedDateTime() {
        return getNoteAttachment().createdDateTime;
    }

    public ArrayList<NoteAttachmentFile> getNoteAttachmentFiles() {
        return mNoteAttachmentFileSetSubject.getValue();
    }

    @Override
    public NoteAttachmentState clone() {
        NoteAttachmentState cloneResult = new NoteAttachmentState();
        NoteAttachment noteAttachment = mNoteAttachmentSubject.getValue();
        if (noteAttachment != null) {
            cloneResult.updateNoteAttachment(noteAttachment.clone());
        }
        ArrayList<NoteAttachmentFile> noteAttachmentFiles = mNoteAttachmentFileSetSubject.getValue();
        ArrayList<NoteAttachmentFile> clonedList = new ArrayList<>();
        if (noteAttachmentFiles != null && !noteAttachmentFiles.isEmpty()) {
            for (NoteAttachmentFile noteAttachmentFile : noteAttachmentFiles) {
                clonedList.add(noteAttachmentFile.clone());
            }
        }
        cloneResult.updateNoteAttachmentFileList(clonedList);
        return cloneResult;
    }
}
