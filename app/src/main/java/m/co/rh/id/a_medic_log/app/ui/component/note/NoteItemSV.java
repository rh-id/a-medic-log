package m.co.rh.id.a_medic_log.app.ui.component.note;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteTagChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.page.NoteDetailPage;
import m.co.rh.id.a_medic_log.app.util.DeleteScopeSummaryBuilder;
import m.co.rh.id.a_medic_log.app.util.UiUtils;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.repository.NoteRepository;
import m.co.rh.id.a_medic_log.base.repository.ProfileRepository;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class NoteItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = NoteItemSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient NoteTagChangeNotifier mNoteTagChangeNotifier;
    private transient NoteRepository mNoteRepository;
    private transient ExecutorService mExecutorService;
    private transient ProfileRepository mProfileRepository;
    private SerialBehaviorSubject<Profile> mProfileSubject;
    private SerialBehaviorSubject<Note> mNoteSubject;
    private SerialBehaviorSubject<TreeSet<NoteTag>> mNoteTagSetSubject;
    private DateFormat mDateFormat;

    public NoteItemSV() {
        mProfileSubject = new SerialBehaviorSubject<>(new Profile());
        mNoteSubject = new SerialBehaviorSubject<>(new Note());
        mNoteTagSetSubject = new SerialBehaviorSubject<>(new TreeSet<>());
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mNoteTagChangeNotifier = mSvProvider.get(NoteTagChangeNotifier.class);
        mNoteRepository = mSvProvider.get(NoteRepository.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mProfileRepository = mSvProvider.get(ProfileRepository.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.item_note, container, false);
        rootLayout.setOnClickListener(this);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        Button buttonDelete = rootLayout.findViewById(R.id.button_delete);
        buttonEdit.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        TextView textEntryDate = rootLayout.findViewById(R.id.text_entry_date);
        TextView textContent = rootLayout.findViewById(R.id.text_content);
        ChipGroup noteTagChipGroup = rootLayout.findViewById(R.id.chip_group_note_tag);
        ViewGroup containerProfileDisplay = rootLayout.findViewById(R.id.container_profile_display);
        TextView textProfileName = rootLayout.findViewById(R.id.text_profile_name);
        mRxDisposer.add("createView_onProfileChanged", mProfileSubject.getSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(profile -> {
                    if (profile.name != null) {
                        containerProfileDisplay.setVisibility(View.VISIBLE);
                        textProfileName.setText(profile.name);
                    } else {
                        containerProfileDisplay.setVisibility(View.GONE);
                    }
                }));
        mRxDisposer.add("crateView_onNoteTagSetChanged",
                mNoteTagSetSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(noteTags -> {
                            noteTagChipGroup.removeAllViews();
                            if (!noteTags.isEmpty()) {
                                for (NoteTag noteTag : noteTags) {
                                    Chip chip = new Chip(activity);
                                    chip.setText(noteTag.tag);
                                    chip.setClickable(false);
                                    noteTagChipGroup.addView(chip);
                                }
                                noteTagChipGroup.setVisibility(View.VISIBLE);
                            } else {
                                noteTagChipGroup.setVisibility(View.GONE);
                            }
                        }));
        mRxDisposer.add("createView_onNoteChanged",
                mNoteSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(note -> {
                            textEntryDate.setText(mDateFormat.format(note.entryDateTime));
                            textContent.setText(note.content);
                            if (note.id != null) {
                                refreshProfile(note.profileId);
                                refreshNoteTagSet(note.id);
                            }
                        }));
        mRxDisposer.add("createView_onNoteTagAdded",
                mNoteTagChangeNotifier.getAddedNoteTag()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(noteTag -> {
                            Note note = mNoteSubject.getValue();
                            if (noteTag.noteId.equals(note.id)) {
                                TreeSet<NoteTag> noteTagTreeSet = mNoteTagSetSubject.getValue();
                                if (noteTagTreeSet.add(noteTag)) {
                                    mNoteTagSetSubject.onNext(noteTagTreeSet);
                                }
                            }
                        }));
        mRxDisposer.add("createView_onNoteTagDeleted",
                mNoteTagChangeNotifier.getDeletedNoteTag()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(noteTag -> {
                            Note note = mNoteSubject.getValue();
                            if (noteTag.noteId.equals(note.id)) {
                                TreeSet<NoteTag> noteTagTreeSet = mNoteTagSetSubject.getValue();
                                if (noteTagTreeSet.remove(noteTag)) {
                                    mNoteTagSetSubject.onNext(noteTagTreeSet);
                                }
                            }
                        }));
        return rootLayout;
    }

    private void refreshProfile(Long profileId) {
        if (profileId != null) {
            mRxDisposer.add("refreshProfile",
                    Single.fromCallable(() -> mProfileRepository.findProfileById(profileId))
                            .subscribeOn(Schedulers.from(mExecutorService))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((profile, throwable) -> {
                                if (throwable == null) {
                                    mProfileSubject.onNext(profile);
                                } else {
                                    mLogger.e(TAG, throwable.getMessage(), throwable);
                                }
                            })
            );
        }
    }

    private void refreshNoteTagSet(long noteId) {
        mRxDisposer.add("refreshNoteTagSet",
                Single.fromCallable(() -> mNoteRepository.findNoteTagsByNoteId(noteId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((noteTags, throwable) -> {
                            if (throwable == null) {
                                mNoteTagSetSubject.onNext(noteTags);
                            } else {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            }
                        })
        );
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    public void setNote(Note note) {
        mNoteSubject.onNext(note);
    }

    public Note getNote() {
        return mNoteSubject.getValue();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_edit) {
            mNavigator.push(Routes.NOTE_DETAIL_PAGE,
                    NoteDetailPage.Args.forUpdate(mNoteSubject.getValue().id));
        } else if (id == R.id.button_delete) {
            mRxDisposer.add("NoteItemSV.onClick_getNoteCounts",
                    Single.fromCallable(() -> mNoteRepository.queryNoteCounts(mNoteSubject.getValue().id))
                            .subscribeOn(Schedulers.from(mExecutorService))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((noteScope, throwable) -> {
                                Context context = mSvProvider.getContext();
                                String title = context.getString(R.string.title_confirm);
                                String content;
                                if (throwable == null) {
                                    content = buildDeleteMessage(context, noteScope);
                                } else {
                                    // scope count failed, still show the terse confirm dialog
                                    mLogger.e(TAG, throwable.getMessage(), throwable);
                                    content = context.getString(R.string.confirm_delete_note);
                                }
                                UiUtils.showConfirmDialog(mNavigator, mSvProvider, title, content,
                                        this::deleteNote);
                            }));
        }
    }

    /**
     * @return the delete confirmation message enriched with the cascade scope,
     * or the terse message when the note owns no cascaded records
     */
    private String buildDeleteMessage(Context context, NoteRepository.NoteCounts noteCounts) {
        Resources resources = context.getResources();
        String medicines = resources.getQuantityString(R.plurals.profile_count_medicines,
                noteCounts.medicineCount, noteCounts.medicineCount);
        String reminders = resources.getQuantityString(R.plurals.profile_count_reminders,
                noteCounts.reminderCount, noteCounts.reminderCount);
        String attachments = resources.getQuantityString(R.plurals.profile_count_attachments,
                noteCounts.attachmentCount, noteCounts.attachmentCount);
        String intakes = resources.getQuantityString(R.plurals.profile_count_intakes,
                noteCounts.intakeCount, noteCounts.intakeCount);
        String scope = DeleteScopeSummaryBuilder.buildScopeSummary(
                new DeleteScopeSummaryBuilder.Category(noteCounts.medicineCount, medicines),
                new DeleteScopeSummaryBuilder.Category(noteCounts.reminderCount, reminders),
                new DeleteScopeSummaryBuilder.Category(noteCounts.attachmentCount, attachments),
                new DeleteScopeSummaryBuilder.Category(noteCounts.intakeCount, intakes));
        if (scope == null) {
            return context.getString(R.string.confirm_delete_note);
        }
        return context.getString(R.string.confirm_delete_note_scope, scope);
    }

    private void deleteNote() {
        Context context = mSvProvider.getContext();
        String errorMessage = context.getString(R.string.error_deleting_note);
        String successMessage = context.getString(R.string.success_deleting_note);
        mRxDisposer.add("NoteItemSV.deleteNote_deleteNoteCmd",
                mSvProvider.get(DeleteNoteCmd.class)
                        .execute(mNoteSubject.getValue())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((note, throwable) -> {
                            if (throwable != null) {
                                RxUtils.logError(mLogger, TAG, errorMessage, throwable);
                            } else {
                                mLogger.i(TAG, successMessage);
                            }
                        }));
    }
}
