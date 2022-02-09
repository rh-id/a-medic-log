package m.co.rh.id.a_medic_log.base.rx;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class SerialBehaviorSubject<E extends Serializable> implements Serializable {
    private transient BehaviorSubject<E> mSubject;

    public SerialBehaviorSubject(E element) {
        mSubject = BehaviorSubject.createDefault(element);
    }

    public BehaviorSubject<E> getSubject() {
        return mSubject;
    }

    public E getValue() {
        return mSubject.getValue();
    }

    public void onNext(E element) {
        mSubject.onNext(element);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(mSubject.getValue());
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        E element = (E) in.readObject();
        mSubject = BehaviorSubject.createDefault(element);
    }
}
