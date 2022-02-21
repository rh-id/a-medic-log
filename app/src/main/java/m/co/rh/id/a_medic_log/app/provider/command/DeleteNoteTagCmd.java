package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteTagChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.aprovider.Provider;

public class DeleteNoteTagCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected NoteDao mNoteDao;
    protected NoteTagChangeNotifier mNoteTagChangeNotifier;

    public DeleteNoteTagCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mNoteDao = provider.get(NoteDao.class);
        mNoteTagChangeNotifier = provider.get(NoteTagChangeNotifier.class);
    }

    public Single<NoteTag> execute(NoteTag noteTag) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mNoteDao.delete(noteTag);
            mNoteTagChangeNotifier.noteTagDeleted(noteTag);
            return noteTag;
        }));
    }
}
