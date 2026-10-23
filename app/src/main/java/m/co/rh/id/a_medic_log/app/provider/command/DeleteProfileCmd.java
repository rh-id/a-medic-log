package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.repository.ProfileRepository;
import m.co.rh.id.a_medic_log.base.state.ProfileState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteProfileCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<ProfileDao> mProfileDao;
    protected ProviderValue<ProfileRepository> mProfileRepository;
    protected ProviderValue<ProfileChangeNotifier> mProfileChangeNotifier;

    public DeleteProfileCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mProfileRepository = provider.lazyGet(ProfileRepository.class);
        mProfileChangeNotifier = provider.lazyGet(ProfileChangeNotifier.class);
    }

    public Single<Profile> execute(Profile profile) {
        return Single.fromCallable(() -> {
            // load the full profile snapshot before the delete transaction,
            // once the delete commits the cascade has already removed the
            // rows from the database
            ProfileState profileState = mProfileRepository.get().queryProfileInfo(profile.id);
            mProfileDao.get().delete(profile);
            // the handler cancels the reminder work from this event payload
            mProfileChangeNotifier.get().profileDeleted(profileState);
            return profile;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
