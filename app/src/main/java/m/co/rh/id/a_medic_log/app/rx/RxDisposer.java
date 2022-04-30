package m.co.rh.id.a_medic_log.app.rx;

import android.content.Context;

import co.rh.id.lib.rx3_utils.disposable.UniqueKeyDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * Helper class to help manage Rx disposable instances
 */
public class RxDisposer implements ProviderDisposable {
    private UniqueKeyDisposable mUniqueKeyDisposable;

    public RxDisposer() {
        mUniqueKeyDisposable = new UniqueKeyDisposable();
    }

    public void add(String uniqueKey, Disposable disposable) {
        mUniqueKeyDisposable.add(uniqueKey, disposable);
    }

    @Override
    public void dispose(Context context) {
        mUniqueKeyDisposable.dispose();
    }
}
