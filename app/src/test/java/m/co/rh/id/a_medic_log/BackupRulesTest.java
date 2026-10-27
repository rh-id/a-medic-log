package m.co.rh.id.a_medic_log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Verifies that all app data (health database, attachments, preferences) is
 * excluded from Android Auto Backup (cloud) and from Android 12+ device-to-device
 * transfers: the manifest must reference both rule files, and both rule files
 * must exclude every backup domain with path=".".
 */
public class BackupRulesTest {

    private static final String MANIFEST_PATH = "src/main/AndroidManifest.xml";
    private static final String DATA_EXTRACTION_RULES_PATH = "src/main/res/xml/data_extraction_rules.xml";
    private static final String FULL_BACKUP_CONTENT_PATH = "src/main/res/xml/full_backup_content.xml";

    private static final List<String> EXPECTED_DOMAINS =
            Arrays.asList("root", "file", "database", "sharedpref", "external");

    /**
     * Walks up from the JVM working directory until it finds the module
     * directory (the one containing src/main/AndroidManifest.xml), so the
     * test passes no matter which directory the build launches it from.
     */
    private static File findModuleDir() {
        Set<String> attemptedRoots = new LinkedHashSet<>();
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            attemptedRoots.add(dir.getAbsolutePath());
            if (new File(dir, MANIFEST_PATH).isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        fail("Could not locate the app module directory: no parent of the working "
                + "directory contains " + MANIFEST_PATH + ". Attempted roots: " + attemptedRoots);
        return null; // never reached, fail() always throws
    }

    private static File moduleFile(String relativePath) {
        File file = new File(findModuleDir(), relativePath);
        assertTrue("Expected rule file is missing: " + file.getAbsolutePath(), file.isFile());
        return file;
    }

    /**
     * Namespace-unaware on purpose: the manifest uses android:-prefixed
     * attributes and the rule files are not namespaced, so attributes must
     * be looked up by their literal (prefix-included) names.
     */
    private static Document parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(file);
    }

    private static void assertExcludesAllDomains(Element section, String sectionLabel, String fileName) {
        NodeList excludes = section.getElementsByTagName("exclude");
        assertEquals(fileName + ": <" + sectionLabel + "> must declare exactly "
                        + EXPECTED_DOMAINS.size() + " <exclude> entries (one per domain), found "
                        + excludes.getLength(),
                EXPECTED_DOMAINS.size(), excludes.getLength());
        Set<String> missing = new LinkedHashSet<>(EXPECTED_DOMAINS);
        for (int i = 0; i < excludes.getLength(); i++) {
            Element exclude = (Element) excludes.item(i);
            String domain = exclude.getAttribute("domain");
            assertTrue(fileName + ": <" + sectionLabel + "> exclude #" + (i + 1)
                            + " has unexpected domain \"" + domain + "\" (expected one of "
                            + EXPECTED_DOMAINS + ")",
                    missing.remove(domain));
            assertEquals(fileName + ": <" + sectionLabel + "> exclude for domain \"" + domain
                            + "\" must cover the whole domain via path=\".\"",
                    ".", exclude.getAttribute("path"));
        }
        assertTrue(fileName + ": <" + sectionLabel + "> is missing excludes for domain(s) " + missing,
                missing.isEmpty());
    }

    @Test
    public void manifestReferencesBothBackupRules() throws Exception {
        Document manifest = parse(moduleFile(MANIFEST_PATH));
        assertEquals("AndroidManifest.xml: unexpected root element",
                "manifest", manifest.getDocumentElement().getTagName());
        NodeList applications = manifest.getDocumentElement().getElementsByTagName("application");
        assertEquals("AndroidManifest.xml: exactly one <application> element expected",
                1, applications.getLength());
        Element application = (Element) applications.item(0);
        assertEquals("AndroidManifest.xml: <application android:allowBackup> must stay \"true\" "
                        + "so the exclusion rules take effect",
                "true", application.getAttribute("android:allowBackup"));
        assertEquals("AndroidManifest.xml: <application android:dataExtractionRules> must "
                        + "reference @xml/data_extraction_rules (Android 12+ cloud backup & D2D)",
                "@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"));
        assertEquals("AndroidManifest.xml: <application android:fullBackupContent> must "
                        + "reference @xml/full_backup_content (pre-Android 12 auto backup)",
                "@xml/full_backup_content", application.getAttribute("android:fullBackupContent"));
    }

    @Test
    public void dataExtractionRulesExcludeAllDomainsForBothModes() throws Exception {
        Document rules = parse(moduleFile(DATA_EXTRACTION_RULES_PATH));
        assertEquals("data_extraction_rules.xml: unexpected root element",
                "data-extraction-rules", rules.getDocumentElement().getTagName());
        for (String mode : Arrays.asList("cloud-backup", "device-transfer")) {
            NodeList sections = rules.getDocumentElement().getElementsByTagName(mode);
            assertEquals("data_extraction_rules.xml: exactly one <" + mode + "> section expected",
                    1, sections.getLength());
            assertExcludesAllDomains((Element) sections.item(0), mode, "data_extraction_rules.xml");
        }
    }

    @Test
    public void fullBackupContentExcludesAllDomains() throws Exception {
        Document content = parse(moduleFile(FULL_BACKUP_CONTENT_PATH));
        assertEquals("full_backup_content.xml: unexpected root element",
                "full-backup-content", content.getDocumentElement().getTagName());
        assertExcludesAllDomains(content.getDocumentElement(),
                "full-backup-content", "full_backup_content.xml");
    }
}
