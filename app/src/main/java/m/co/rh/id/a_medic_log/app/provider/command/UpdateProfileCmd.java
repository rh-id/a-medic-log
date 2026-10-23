package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.state.ProfileState;
import m.co.rh.id.aprovider.Provider;

public class UpdateProfileCmd extends NewProfileCmd {

    public UpdateProfileCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ProfileState> execute(Profile profile) {
        return Single.fromCallable(() -> {
            ProfileState profileState = mProfileRepository.get().updateProfile(profile);
            mProfileChangeNotifier.get().profileUpdated(profileState);
            return profileState;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
