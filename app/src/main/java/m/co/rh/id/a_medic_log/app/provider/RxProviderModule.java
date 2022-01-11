package m.co.rh.id.a_medic_log.app.provider;

import android.content.Context;

import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class RxProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerAsync(RxDisposer.class, RxDisposer::new);
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // leave blank
    }
}
