package m.co.rh.id.a_medic_log.app.provider;

import android.content.Context;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module specifically for stateful view lifecycle
 */
public class StatefulViewProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new RxProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // leave blank
    }
}
