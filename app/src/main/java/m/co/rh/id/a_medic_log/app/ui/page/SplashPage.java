package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class SplashPage extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider> {
    private transient INavigator mNavigator;
    private transient Provider mProvider;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mProvider = provider;
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mProvider.get(Handler.class)
                .postDelayed(() ->
                        mNavigator.retry(new HomePage()), 1000);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        return activity.getLayoutInflater().inflate(R.layout.page_splash, container, false);
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mNavigator = null;
        mProvider = null;
    }
}
