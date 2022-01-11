package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewNoteCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<NoteDao> mNoteDao;
    protected ProviderValue<NoteChangeNotifier> mNoteChangeNotifier;
    protected BehaviorSubject<String> mProfileIdValidSubject;
    protected BehaviorSubject<String> mEntryDateTimeValidSubject;
    protected BehaviorSubject<String> mContentValidSubject;

    public NewNoteCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteChangeNotifier = provider.lazyGet(NoteChangeNotifier.class);
        mProfileIdValidSubject = BehaviorSubject.create();
        mEntryDateTimeValidSubject = BehaviorSubject.create();
        mContentValidSubject = BehaviorSubject.create();
    }

    public Single<NoteState> execute(NoteState noteState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mNoteDao.get().insertNote(noteState);
            mNoteChangeNotifier.get().noteAdded(noteState);
            return noteState;
        }));
    }

    public boolean valid(NoteState noteState) {
        boolean isValid = false;
        if (noteState != null) {
            Note note = noteState.getNote();
            boolean profileIdValid;
            if (note.profileId != null) {
                profileIdValid = true;
                mProfileIdValidSubject.onNext("");
            } else {
                profileIdValid = false;
                mProfileIdValidSubject.onNext(mAppContext.getString(R.string.profile_is_required));
            }
            boolean entryDateTimeValid;
            if (note.entryDateTime != null) {
                entryDateTimeValid = true;
                mEntryDateTimeValidSubject.onNext("");
            } else {
                entryDateTimeValid = false;
                mEntryDateTimeValidSubject.onNext(mAppContext.getString(R.string.entry_date_time_is_required));
            }
            boolean contentValid;
            if (note.content != null && !note.content.isEmpty()) {
                contentValid = true;
                mContentValidSubject.onNext("");
            } else {
                contentValid = false;
                mContentValidSubject.onNext(mAppContext.getString(R.string.content_is_required));
            }
            isValid = profileIdValid && entryDateTimeValid && contentValid;
        }
        return isValid;
    }

    public Flowable<String> getContentValid() {
        return Flowable.fromObservable(mContentValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getEntryDateTimeValid() {
        return Flowable.fromObservable(mEntryDateTimeValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String profileIdValid = mProfileIdValidSubject.getValue();
        if (profileIdValid != null && !profileIdValid.isEmpty()) {
            return profileIdValid;
        }
        String entryDateTimeValid = mEntryDateTimeValidSubject.getValue();
        if (entryDateTimeValid != null && !entryDateTimeValid.isEmpty()) {
            return entryDateTimeValid;
        }
        String contentValid = mContentValidSubject.getValue();
        if (contentValid != null && !contentValid.isEmpty()) {
            return contentValid;
        }
        return "";
    }
}
