package m.co.rh.id.a_medic_log.app.provider.notifier;

import java.io.Serializable;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.base.state.NoteState;

public class NoteChangeNotifier {
    private PublishSubject<NoteState> mAddedNoteSubject;
    private PublishSubject<NoteUpdatedEvent> mUpdatedNoteSubject;
    private PublishSubject<NoteState> mDeletedNoteSubject;

    public NoteChangeNotifier() {
        mAddedNoteSubject = PublishSubject.create();
        mUpdatedNoteSubject = PublishSubject.create();
        mDeletedNoteSubject = PublishSubject.create();
    }

    public void noteAdded(NoteState noteState) {
        mAddedNoteSubject.onNext(noteState);
    }

    public void noteUpdated(NoteState beforeUpdate, NoteState afterUpdate) {
        mUpdatedNoteSubject.onNext(new NoteUpdatedEvent(beforeUpdate, afterUpdate));
    }

    public void noteDeleted(NoteState noteState) {
        mDeletedNoteSubject.onNext(noteState);
    }

    public Flowable<NoteState> getAddedNote() {
        return Flowable.fromObservable(mAddedNoteSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<NoteUpdatedEvent> getUpdatedNote() {
        return Flowable.fromObservable(mUpdatedNoteSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<NoteState> getDeletedNote() {
        return Flowable.fromObservable(mDeletedNoteSubject, BackpressureStrategy.BUFFER);
    }

    public static class NoteUpdatedEvent implements Serializable {
        private NoteState mBefore;
        private NoteState mAfter;

        public NoteUpdatedEvent(NoteState before, NoteState after) {
            mBefore = before;
            mAfter = after;
        }

        public NoteState getBefore() {
            return mBefore;
        }

        public NoteState getAfter() {
            return mAfter;
        }
    }
}
