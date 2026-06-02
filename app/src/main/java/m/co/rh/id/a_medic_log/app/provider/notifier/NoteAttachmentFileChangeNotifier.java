package m.co.rh.id.a_medic_log.app.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;

public class NoteAttachmentFileChangeNotifier {
    private Subject<NoteAttachmentFile> mAddedSubject;
    private Subject<NoteAttachmentFile> mDeletedSubject;

    public NoteAttachmentFileChangeNotifier() {
        mAddedSubject = PublishSubject.<NoteAttachmentFile>create().toSerialized();
        mDeletedSubject = PublishSubject.<NoteAttachmentFile>create().toSerialized();
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
