package m.co.rh.id.a_medic_log.app.ui.component;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.annotation.NavRouteIndex;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class AppBarSV extends StatefulView<Activity> implements Externalizable, View.OnClickListener, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavRouteIndex
    private transient byte mRouteIndex;
    private String mTitle;
    private transient View.OnClickListener mNavigationOnClickListener;
    private Integer mMenuResId;
    private transient Toolbar.OnMenuItemClickListener mOnMenuItemClickListener;
    private transient Provider mSvProvider;
    private transient BehaviorSubject<String> mUpdateTitle;

    public AppBarSV() {
        this(null);
    }

    public AppBarSV(Integer menuResId) {
        mMenuResId = menuResId;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.app_bar, container, false);
        if (mUpdateTitle == null) {
            if (mTitle == null) {
                mUpdateTitle = BehaviorSubject.create();
            } else {
                mUpdateTitle = BehaviorSubject.createDefault(mTitle);
            }
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = BaseApplication.of(activity).getProvider().get(StatefulViewProvider.class);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        mSvProvider.get(RxDisposer.class).add("updateTitle",
                mUpdateTitle.subscribe(toolbar::setTitle));
        if (isInitialRoute()) {
            toolbar.setNavigationIcon(R.drawable.ic_menu_white);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
        }
        toolbar.setNavigationOnClickListener(this);
        if (mMenuResId != null) {
            toolbar.inflateMenu(mMenuResId);
        }
        toolbar.setOnMenuItemClickListener(this);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNavigationOnClickListener = null;
        mTitle = null;
        mNavigator = null;
    }

    public boolean isInitialRoute() {
        return mRouteIndex == 0;
    }

    public void setTitle(String title) {
        mTitle = title;
        if (mUpdateTitle != null) {
            mUpdateTitle.onNext(title);
        }
    }

    public void setNavigationOnClick(View.OnClickListener navigationOnClickListener) {
        mNavigationOnClickListener = navigationOnClickListener;
    }

    public void setMenuItemListener(Toolbar.OnMenuItemClickListener listener) {
        mOnMenuItemClickListener = listener;
    }

    @Override
    public void onClick(View view) {
        if (isInitialRoute()) {
            if (mNavigationOnClickListener != null) {
                mNavigationOnClickListener.onClick(view);
            }
        } else {
            mNavigator.pop();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mOnMenuItemClickListener != null) {
            return mOnMenuItemClickListener.onMenuItemClick(item);
        }
        return false;
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
        objectOutput.writeObject(mTitle);
        objectOutput.writeObject(mMenuResId);
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws ClassNotFoundException, IOException {
        super.readExternal(objectInput);
        mTitle = (String) objectInput.readObject();
        mMenuResId = (Integer) objectInput.readObject();
    }
}
