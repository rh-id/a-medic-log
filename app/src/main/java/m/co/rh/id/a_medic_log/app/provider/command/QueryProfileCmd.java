package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

public class QueryProfileCmd {
    protected ExecutorService mExecutorService;
    protected ProfileDao mProfileDao;

    public QueryProfileCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mProfileDao = provider.get(ProfileDao.class);
    }

    public Single<Profile> findProfileById(long profileId) {
        return Single.fromFuture(mExecutorService.submit(() ->
                mProfileDao.findProfileById(profileId)));
    }
}
