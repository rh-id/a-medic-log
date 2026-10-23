package m.co.rh.id.a_medic_log.base.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

@RunWith(AndroidJUnit4.class)
public class NoteAttachmentRepositoryTest {

    private Provider mProvider;
    private NoteAttachmentRepository mNoteAttachmentRepository;
    private NoteDao mNoteDao;
    private NoteAttachmentDao mNoteAttachmentDao;
    private NoteAttachmentFileDao mNoteAttachmentFileDao;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new RepositoryTestProviderModule());
        mNoteAttachmentRepository = mProvider.get(NoteAttachmentRepository.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mNoteAttachmentDao = mProvider.get(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = mProvider.get(NoteAttachmentFileDao.class);
    }

    @After
    public void cleanup() {
        mProvider.dispose();
    }

    /**
     * Inserts a parent note row directly and returns a note attachment state
     * (with 2 files) owned by it, the attachment is inserted through the
     * attachment repository
     */
    private NoteAttachmentState insertNoteAttachment(String fileName1, String fileName2) {
        Note note = new Note();
        note.content = "note with attachment";
        note.id = mNoteDao.insert(note);
        NoteAttachment noteAttachment = new NoteAttachment();
        noteAttachment.name = "test attachment";
        noteAttachment.noteId = note.id;
        NoteAttachmentState noteAttachmentState = new NoteAttachmentState();
        noteAttachmentState.updateNoteAttachment(noteAttachment);
        NoteAttachmentFile file1 = new NoteAttachmentFile();
        file1.fileName = fileName1;
        NoteAttachmentFile file2 = new NoteAttachmentFile();
        file2.fileName = fileName2;
        List<NoteAttachmentFile> files = new ArrayList<>();
        files.add(file1);
        files.add(file2);
        noteAttachmentState.updateNoteAttachmentFileList(files);

        mNoteAttachmentRepository.insertNoteAttachment(noteAttachmentState);
        return noteAttachmentState;
    }

    @Test
    public void insertNoteAttachment_backFillsIdsAndPersistsFiles() {
        NoteAttachmentState noteAttachmentState = insertNoteAttachment("file1.jpg", "file2.jpg");

        // ids are back-filled on the passed payloads
        NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
        assertNotNull(noteAttachment.id);
        assertNotNull(noteAttachmentState.getId());
        List<NoteAttachmentFile> files = noteAttachmentState.getNoteAttachmentFiles();
        assertEquals(2, files.size());
        for (NoteAttachmentFile file : files) {
            assertNotNull(file.id);
            assertEquals(noteAttachment.id, file.attachmentId);
        }

        // rows are persisted
        assertEquals(1, mNoteAttachmentDao.findNoteAttachmentsByNoteId(noteAttachment.noteId).size());
        List<NoteAttachmentFile> persistedFiles =
                mNoteAttachmentFileDao.findNoteAttachmentFilesByAttachmentId(noteAttachment.id);
        assertEquals(2, persistedFiles.size());
        List<String> persistedFileNames = new ArrayList<>();
        for (NoteAttachmentFile persistedFile : persistedFiles) {
            persistedFileNames.add(persistedFile.fileName);
        }
        assertTrue(persistedFileNames.contains("file1.jpg"));
        assertTrue(persistedFileNames.contains("file2.jpg"));
    }

    @Test
    public void deleteNoteAttachment_removesAttachmentAndItsFiles() {
        NoteAttachmentState noteAttachmentState = insertNoteAttachment("file1.jpg", "file2.jpg");
        NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();

        mNoteAttachmentRepository.deleteNoteAttachment(noteAttachmentState);

        // attachment and its files are gone (FK ON DELETE CASCADE)
        assertEquals(0, mNoteAttachmentDao.findNoteAttachmentsByNoteId(noteAttachment.noteId).size());
        assertEquals(0, mNoteAttachmentFileDao.findNoteAttachmentFilesByAttachmentId(noteAttachment.id).size());
    }
}
