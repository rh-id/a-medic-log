package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteTagChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.aprovider.Provider;

public class NewNoteTagCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected NoteTagDao mNoteTagDao;
    protected NoteTagChangeNotifier mNoteTagChangeNotifier;
    protected BehaviorSubject<String> mTagValidSubject;

    public NewNoteTagCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mNoteTagDao = provider.get(NoteTagDao.class);
        mNoteTagChangeNotifier = provider.get(NoteTagChangeNotifier.class);
        mTagValidSubject = BehaviorSubject.create();
    }

    public Single<NoteTag> execute(NoteTag noteTag) {
        return Single.fromCallable(() -> {
            noteTag.id = mNoteTagDao.insert(noteTag);
            mNoteTagChangeNotifier.noteTagAdded(noteTag);
            return noteTag;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public boolean valid(NoteTag noteTag) {
        boolean isValid = false;
        if (noteTag != null) {
            boolean tagValid = false;
            if (noteTag.tag != null && !noteTag.tag.isEmpty()) {
                tagValid = true;
                mTagValidSubject.onNext("");
            } else {
                mTagValidSubject.onNext(mAppContext.getString(R.string.tag_is_required));
            }
            isValid = tagValid;
        }
        return isValid;
    }

    public Flowable<String> getTagValid() {
        return Flowable.fromObservable(mTagValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String tagValid = mTagValidSubject.getValue();
        if (tagValid != null && !tagValid.isEmpty()) {
            return tagValid;
        }
        return "";
    }
}
