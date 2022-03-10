package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteTagCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryNoteCmd;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.adapter.SuggestionAdapter;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.rx.SerialBehaviorSubject;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

public class NoteTagDetailSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute, RequireComponent<Provider>, View.OnClickListener {

    private static final String TAG = NoteTagDetailSVDialog.class.getName();
    private transient NavRoute mNavRoute;
    private SerialBehaviorSubject<NoteTag> mNoteTag;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient QueryNoteCmd mQueryNoteCmd;
    private transient NewNoteTagCmd mNewNoteTagCmd;
    private transient TextWatcher mTagTextWatcher;
    private transient Function<String, Collection<String>> mSuggestionQuery;

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mQueryNoteCmd = mSvProvider.get(QueryNoteCmd.class);
        mNewNoteTagCmd = mSvProvider.get(NewNoteTagCmd.class);
        if (mNoteTag == null) {
            NoteTag noteTag = new NoteTag();
            if (shouldSave()) {
                noteTag.noteId = getNoteId();
            }
            mNoteTag = new SerialBehaviorSubject<>(noteTag);
        }
        mTagTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String tag = editable.toString();
                NoteTag noteTag = mNoteTag.getValue();
                noteTag.tag = tag;
                mNewNoteTagCmd.valid(noteTag);
            }
        };
        mSuggestionQuery = s ->
                mQueryNoteCmd.searchNoteTag(s).blockingGet();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.dialog_note_tag_detail, container, false);
        AutoCompleteTextView tagTextInput = rootLayout.findViewById(R.id.input_text_tag);
        tagTextInput.addTextChangedListener(mTagTextWatcher);
        tagTextInput.setThreshold(1);
        tagTextInput.setAdapter(new SuggestionAdapter
                (activity, android.R.layout.select_dialog_item, mSuggestionQuery));
        Button okButton = rootLayout.findViewById(R.id.button_ok);
        okButton.setOnClickListener(this);
        Button cancelButton = rootLayout.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        mRxDisposer.add("createView_onTagValid",
                mNewNoteTagCmd.getTagValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                tagTextInput.setError(s);
                            } else {
                                tagTextInput.setError(null);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mTagTextWatcher = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_ok) {
            if (shouldSave()) {
                if (mNewNoteTagCmd.valid(mNoteTag.getValue())) {
                    mRxDisposer.add("onClick_newNoteTag", mNewNoteTagCmd.execute(mNoteTag.getValue())
                            .subscribe((noteTag, throwable) -> {
                                Context context = mSvProvider.getContext();
                                String error = context.getString(R.string.error_failed_to_add_tag);
                                String success = context.getString(R.string.success_adding_tag);
                                if (throwable != null) {
                                    mLogger.e(TAG, error, throwable);
                                    getNavigator().pop();
                                } else {
                                    mLogger.i(TAG, success);
                                    getNavigator().pop(Result.with(noteTag));
                                }
                            })
                    );
                } else {
                    String error = mNewNoteTagCmd.getValidationError();
                    mLogger.i(TAG, error);
                }
            } else {
                if (mNewNoteTagCmd.valid(mNoteTag.getValue())) {
                    getNavigator().pop(Result.with(mNoteTag.getValue()));
                }
            }
        } else if (id == R.id.button_cancel) {
            getNavigator().pop();
        }
    }

    private boolean shouldSave() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.shouldSave;
        }
        return false;
    }

    private Long getNoteId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.noteId;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args save(long noteId) {
            Args args = new Args();
            args.noteId = noteId;
            args.shouldSave = true;
            return args;
        }

        public static Args dontSave() {
            Args args = new Args();
            args.shouldSave = false;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private boolean shouldSave;
        private Long noteId;
    }

    public static class Result implements Serializable {
        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        static Result with(NoteTag noteTag) {
            Result result = new Result();
            result.noteTag = noteTag;
            return result;
        }

        private NoteTag noteTag;

        public NoteTag getNoteTag() {
            return noteTag;
        }
    }
}
