package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.repository.NoteRepository;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteNoteCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<NoteDao> mNoteDao;
    protected ProviderValue<NoteRepository> mNoteRepository;
    protected ProviderValue<NoteChangeNotifier> mNoteChangeNotifier;

    public DeleteNoteCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteRepository = provider.lazyGet(NoteRepository.class);
        mNoteChangeNotifier = provider.lazyGet(NoteChangeNotifier.class);
    }

    public Single<NoteState> execute(Note note) {
        return Single.fromCallable(() -> {
            // load the full nested state before the delete transaction,
            // the cascade removes the related rows once the delete commits
            NoteState noteState = mNoteRepository.get().queryNoteInfo(note.id);
            mNoteRepository.get().deleteNote(noteState);
            mNoteChangeNotifier.get().noteDeleted(noteState);
            return noteState;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
