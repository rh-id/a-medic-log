package m.co.rh.id.a_medic_log.app.provider;

import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class RxProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerAsync(RxDisposer.class, RxDisposer::new);
    }
}
