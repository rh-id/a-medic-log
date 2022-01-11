package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

public class UpdateProfileCmd extends NewProfileCmd {

    public UpdateProfileCmd(Context context, Provider provider) {
        super(context, provider);
    }

    @Override
    public Single<Profile> execute(Profile profile) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mProfileDao.get().updateProfile(profile);
            mProfileChangeNotifier.get().profileUpdated(profile);
            return profile;
        }));
    }
}
