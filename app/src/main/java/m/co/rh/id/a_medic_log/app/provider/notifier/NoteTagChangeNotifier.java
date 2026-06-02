package m.co.rh.id.a_medic_log.app.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;

public class NoteTagChangeNotifier {
    private Subject<NoteTag> mAddedNoteTagSubject;
    private Subject<NoteTag> mDeletedNoteTagSubject;

    public NoteTagChangeNotifier() {
        mAddedNoteTagSubject = PublishSubject.<NoteTag>create().toSerialized();
        mDeletedNoteTagSubject = PublishSubject.<NoteTag>create().toSerialized();
    }

    public void noteTagAdded(NoteTag noteTag) {
        mAddedNoteTagSubject.onNext(noteTag);
    }

    public Flowable<NoteTag> getAddedNoteTag() {
        return Flowable.fromObservable(mAddedNoteTagSubject, BackpressureStrategy.BUFFER);
    }

    public void noteTagDeleted(NoteTag noteTag) {
        mDeletedNoteTagSubject.onNext(noteTag);
    }

    public Flowable<NoteTag> getDeletedNoteTag() {
        return Flowable.fromObservable(mDeletedNoteTagSubject, BackpressureStrategy.BUFFER);
    }
}
