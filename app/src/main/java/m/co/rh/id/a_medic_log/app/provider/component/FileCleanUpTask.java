package m.co.rh.id.a_medic_log.app.provider.component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Helper to cleanup unused file
 */
public class FileCleanUpTask {
    private static final String TAG = FileCleanUpTask.class.getName();

    private final ExecutorService mExecutorService;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final NoteDao mNoteDao;

    public FileCleanUpTask(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mNoteDao = provider.get(NoteDao.class);
        cleanUp();
    }

    private void cleanUp() {
        Future<List<String>> noteAttachmentImageFileList = mExecutorService.submit(
                () -> {
                    File imageParent = mFileHelper.getNoteAttachmentImageParent();
                    File[] files = imageParent.listFiles();
                    List<String> fileNames = new ArrayList<>();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            if (!file.isDirectory()) {
                                fileNames.add(file.getName());
                            }
                        }
                    }
                    return fileNames;
                }
        );
        mExecutorService.execute(() -> {
            try {
                List<Future<Boolean>> taskList = new ArrayList<>();
                taskList.add(
                        mExecutorService.submit(() -> {
                            List<String> noteAttachmentImageNames = noteAttachmentImageFileList.get();
                            if (!noteAttachmentImageNames.isEmpty()) {
                                for (String imageName : noteAttachmentImageNames) {
                                    NoteAttachmentFile noteAttachmentFile = mNoteDao.findNoteAttachmentFileByFileName(imageName);
                                    if (noteAttachmentFile == null) {
                                        mFileHelper.deleteNoteAttachmentImage(imageName);
                                    }
                                }
                            }
                            return true;
                        })
                );
                for (Future<Boolean> task : taskList) {
                    task.get();
                }
            } catch (Exception e) {
                mLogger.d(TAG, "Error occurred when cleaning file", e);
            }
        });
    }
}
