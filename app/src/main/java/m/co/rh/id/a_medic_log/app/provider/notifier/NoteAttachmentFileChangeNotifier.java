package m.co.rh.id.a_medic_log.app.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;

public class NoteAttachmentFileChangeNotifier {
    private PublishSubject<NoteAttachmentFile> mAddedSubject;
    private PublishSubject<NoteAttachmentFile> mDeletedSubject;

    public NoteAttachmentFileChangeNotifier() {
        mAddedSubject = PublishSubject.create();
        mDeletedSubject = PublishSubject.create();
    }

    public void noteAttachmentFileAdded(NoteAttachmentFile noteAttachmentFile) {
        mAddedSubject.onNext(noteAttachmentFile);
    }

    public void noteAttachmentFileDeleted(NoteAttachmentFile noteAttachmentFile) {
        mDeletedSubject.onNext(noteAttachmentFile);
    }

    public Flowable<NoteAttachmentFile> getAddedNoteAttachmentFile() {
        return Flowable.fromObservable(mAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<NoteAttachmentFile> getDeletedNoteAttachmentFile() {
        return Flowable.fromObservable(mDeletedSubject, BackpressureStrategy.BUFFER);
    }
}
