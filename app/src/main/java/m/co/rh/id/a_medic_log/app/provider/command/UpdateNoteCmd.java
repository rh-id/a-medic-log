package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class UpdateNoteCmd extends NewNoteCmd {
    private ProviderValue<QueryNoteCmd> mNoteQueryCmd;

    public UpdateNoteCmd(Provider provider) {
        super(provider);
        mNoteQueryCmd = provider.lazyGet(QueryNoteCmd.class);
    }

    public Single<NoteState> execute(NoteState noteState) {
        return mNoteQueryCmd.get().queryNoteInfo(noteState.getNoteId())
                .flatMap(beforeUpdate -> Single.fromCallable(() -> {
                    mNoteDao.get().updateNote(noteState);
                    mNoteChangeNotifier.get().noteUpdated(beforeUpdate, noteState.clone());
                    return noteState;
                }).subscribeOn(Schedulers.from(mExecutorService.get())));
    }
}
