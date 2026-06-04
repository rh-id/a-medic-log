package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteTagChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.aprovider.Provider;

public class DeleteNoteTagCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected NoteTagDao mNoteTagDao;
    protected NoteTagChangeNotifier mNoteTagChangeNotifier;

    public DeleteNoteTagCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mNoteTagDao = provider.get(NoteTagDao.class);
        mNoteTagChangeNotifier = provider.get(NoteTagChangeNotifier.class);
    }

    public Single<NoteTag> execute(NoteTag noteTag) {
        return Single.fromCallable(() -> {
            mNoteTagDao.delete(noteTag);
            mNoteTagChangeNotifier.noteTagDeleted(noteTag);
            return noteTag;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
