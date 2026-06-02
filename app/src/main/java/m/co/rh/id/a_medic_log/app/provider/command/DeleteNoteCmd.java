package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
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

    public DeleteNoteCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteChangeNotifier = provider.lazyGet(NoteChangeNotifier.class);
        mNoteQueryCmd = provider.lazyGet(QueryNoteCmd.class);
    }

    public Single<NoteState> execute(Note note) {
        return Single.fromCallable(() -> {
            NoteState noteState = new NoteState();
            noteState.updateNote(note.clone());
            return noteState;
        }).subscribeOn(Schedulers.from(mExecutorService.get()))
        .flatMap(noteState -> mNoteQueryCmd.get().queryNoteInfo(noteState)
                .flatMap(fullNoteState -> Single.fromCallable(() -> {
                    mNoteDao.get().deleteNote(fullNoteState);
                    mNoteChangeNotifier.get().noteDeleted(fullNoteState);
                    return fullNoteState;
                }).subscribeOn(Schedulers.from(mExecutorService.get()))));
    }
}
