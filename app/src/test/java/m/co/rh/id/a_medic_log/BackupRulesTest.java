package m.co.rh.id.a_medic_log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

public class BackupRulesTest {
    private static final String DATABASE_EXCLUDE = "domain=\"database\" path=\"a-medic-log.db\"";
    private static final String ATTACHMENT_EXCLUDE = "domain=\"file\" path=\"app/note/attachment\"";

    @Test
    public void backupRulesExcludeHealthDataAndManifestReferencesBothFormats() throws Exception {
        File manifest = new File("src/main/AndroidManifest.xml");
        File modernRules = new File("src/main/res/xml/data_extraction_rules.xml");
        File legacyRules = new File("src/main/res/xml/full_backup_content.xml");

        String manifestText = Files.readString(manifest.toPath());
        String modernText = Files.readString(modernRules.toPath());
        String legacyText = Files.readString(legacyRules.toPath());

        assertTrue(manifestText.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""));
        assertTrue(manifestText.contains("android:fullBackupContent=\"@xml/full_backup_content\""));
        assertEquals(2, count(modernText, DATABASE_EXCLUDE));
        assertEquals(2, count(modernText, ATTACHMENT_EXCLUDE));
        assertEquals(1, count(legacyText, DATABASE_EXCLUDE));
        assertEquals(1, count(legacyText, ATTACHMENT_EXCLUDE));
    }

    private static int count(String text, String value) {
        return (int) List.of(text.split(java.util.regex.Pattern.quote(value), -1)).size() - 1;
    }
}
