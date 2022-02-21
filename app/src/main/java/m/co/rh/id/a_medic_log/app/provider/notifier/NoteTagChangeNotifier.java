package m.co.rh.id.a_medic_log.app.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;

public class NoteTagChangeNotifier {
    private PublishSubject<NoteTag> mAddedNoteTagSubject;
    private PublishSubject<NoteTag> mDeletedNoteTagSubject;

    public NoteTagChangeNotifier() {
        mAddedNoteTagSubject = PublishSubject.create();
        mDeletedNoteTagSubject = PublishSubject.create();
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
