package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

public class UpdateProfileCmd extends NewProfileCmd {

    public UpdateProfileCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<Profile> execute(Profile profile) {
        return Single.fromCallable(() -> {
            mProfileRepository.get().updateProfile(profile);
            mProfileChangeNotifier.get().profileUpdated(profile);
            return profile;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
