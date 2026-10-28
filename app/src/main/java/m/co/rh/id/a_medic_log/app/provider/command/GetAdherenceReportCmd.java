package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.component.AdherenceReportGenerator;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Thin command wrapper over {@link AdherenceReportGenerator}: moves the
 * generator's blocking report generation onto the provider executor and
 * exposes it as a Single.
 */
public class GetAdherenceReportCmd {
    private ProviderValue<AdherenceReportGenerator> mGenerator;
    private ProviderValue<ExecutorService> mExecutorService;

    public GetAdherenceReportCmd(Provider provider) {
        mGenerator = provider.lazyGet(AdherenceReportGenerator.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
    }

    public Single<AdherenceReport> execute(long profileId, int rangeDays) {
        return Single.fromCallable(() -> mGenerator.get().generate(profileId, rangeDays))
                .subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
