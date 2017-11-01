package so.macalu.cache;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class DirCacheInformationTest {
    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();
    public static Path testDir;
    public static CacheInformation testCache;

    @BeforeClass
    public static void setUp() throws IOException {
        testDir = tempFolder.newFolder("test").toPath();
        testCache = new DirCacheInformation(testDir, true);
    }

    @Test
    public void testInitCreation() {
        File rootCacheLocation = new File(testDir.toFile(), ".jcache");
        assertTrue(rootCacheLocation.exists());
    }

    @Test
    public void testSubDirCreation() throws IOException {
        File testSub = new File(testDir.toFile(), "sub1");
        Files.createDirectory(testSub.toPath());

        CacheInformation subCache = new DirCacheInformation(testSub.toPath(), false);
        File rootCacheLocation = new File(testDir.toFile(), ".jcache");
        assertTrue(new File(rootCacheLocation, "sub1.json").exists());
    }

}