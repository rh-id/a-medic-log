package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteProfileCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<ProfileDao> mProfileDao;
    protected ProviderValue<ProfileChangeNotifier> mProfileChangeNotifier;

    public DeleteProfileCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mProfileChangeNotifier = provider.lazyGet(ProfileChangeNotifier.class);
    }

    public Single<Profile> execute(Profile profile) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mProfileDao.get().delete(profile);
            mProfileChangeNotifier.get().profileDeleted(profile);
            return profile;
        }));
    }
}
