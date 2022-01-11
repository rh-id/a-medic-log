package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteNoteCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<NoteDao> mNoteDao;
    protected ProviderValue<NoteChangeNotifier> mNoteChangeNotifier;
    protected ProviderValue<QueryNoteCmd> mNoteQueryCmd;

    public DeleteNoteCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteChangeNotifier = provider.lazyGet(NoteChangeNotifier.class);
        mNoteQueryCmd = provider.lazyGet(QueryNoteCmd.class);
    }

    public Single<NoteState> execute(Note note) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            NoteState noteState = new NoteState();
            noteState.updateNote(note.clone());
            noteState = mNoteQueryCmd.get().queryNoteInfo(noteState).blockingGet();
            mNoteDao.get().deleteNote(noteState);
            mNoteChangeNotifier.get().noteDeleted(noteState);
            return noteState;
        }));
    }
}
