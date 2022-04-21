package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
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
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            NoteState beforeUpdate =
                    mNoteQueryCmd.get().queryNoteInfo(noteState.getNoteId()).blockingGet();
            mNoteDao.get().updateNote(noteState);
            mNoteChangeNotifier.get().noteUpdated(beforeUpdate, noteState.clone());
            return noteState;
        }));
    }
}
