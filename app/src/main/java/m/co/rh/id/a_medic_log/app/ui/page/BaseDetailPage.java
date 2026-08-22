package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;

import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public abstract class BaseDetailPage extends StatefulView<Activity> implements RequireComponent<Provider> {

    @NavInject
    protected transient INavigator mNavigator;
    @NavInject
    protected transient NavRoute mNavRoute;
    protected transient StatefulViewProvider mSvProvider;
    protected transient ILogger mLogger;
    protected transient RxDisposer mRxDisposer;

    @Override
    public final void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        onProvideComponent(provider);
    }

    protected abstract void onProvideComponent(Provider provider);

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mLogger = null;
        mRxDisposer = null;
        onPageDispose(activity);
    }

    protected abstract void onPageDispose(Activity activity);
}
