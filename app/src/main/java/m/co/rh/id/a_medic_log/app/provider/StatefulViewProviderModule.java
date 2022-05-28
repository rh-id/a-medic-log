package m.co.rh.id.a_medic_log.app.provider;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module specifically for stateful view lifecycle
 */
public class StatefulViewProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new RxProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());
    }
}
