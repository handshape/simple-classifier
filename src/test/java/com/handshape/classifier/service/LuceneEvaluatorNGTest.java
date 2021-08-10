package com.handshape.classifier.service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.io.FileUtils;
import static org.testng.Assert.*;

/**
 *
 * @author jturner
 */
public class LuceneEvaluatorNGTest {

    public LuceneEvaluatorNGTest() {
    }

    /**
     * Test of evaluate method, of class LuceneEvaluator.
     */
    @org.testng.annotations.Test
    public void testEvaluate() throws IOException, URISyntaxException {
        System.out.println("evaluate");
        Map<String, String> evaluationData = new TreeMap<>();
        evaluationData.put("text", "elbows shoulders knees and toes");
        evaluationData.put("title", "an unexpected journey");
        try ( LuceneEvaluator instance = new LuceneEvaluator(new File(getClass().getResource("/testcategories.properties").toURI()))) {
            assertEquals(instance.evaluate(evaluationData), Arrays.asList(new String[]{
                "positiveTest1",
                "positiveTest2",
                "positiveTest3",
                "positiveTest4"
            }));
        }
    }

    /**
     * Test of getFieldList method, of class LuceneEvaluator.
     */
    @org.testng.annotations.Test
    public void testGetFieldList() throws IOException, URISyntaxException {
        System.out.println("getFieldList");
        try ( LuceneEvaluator instance = new LuceneEvaluator(new File(getClass().getResource("/testcategories.properties").toURI()))) {
            assertEquals(instance.getFieldList(), new TreeSet(Arrays.asList(new String[]{
                "title",
                LuceneEvaluator.DEFAULT_FIELD_NAME
            })));
        }
    }

    /**
     * Integration test of file-watching behaviour.
     */
    @org.testng.annotations.Test
    public void testFileWatcher() throws IOException, URISyntaxException, InterruptedException {
        System.out.println("Integration Test - file watching");
        TreeMap<String, String> testMap = new TreeMap<>();
        testMap.put("text", "shabbadoo babbaloo");
        testMap.put("testfield", "foo bar baz");
        File tempFile = File.createTempFile("integration", ".properties", new File("."));
        tempFile.deleteOnExit();
        FileUtils.write(tempFile, "alpha:shabbadoo\n", "UTF-8", true);
        try ( LuceneEvaluator instance = new LuceneEvaluator(tempFile)) {
            assertEquals(instance.getFieldList(), new TreeSet(Arrays.asList(new String[]{
                LuceneEvaluator.DEFAULT_FIELD_NAME
            })));
            assertEquals(instance.evaluate(testMap), new TreeSet(Arrays.asList(new String[]{
                "alpha"
            })));
            long oldTime = instance.getLastLoadTime();
            FileUtils.write(tempFile, "beta:babbaloo AND testfield:foo\n", "UTF-8", true);
            while (oldTime == instance.getLastLoadTime()) {
                Thread.sleep(100);
                if (System.currentTimeMillis() > oldTime + 10000) {
                    System.out.println("WARNING - Change not detected ten seconds after being written to disk. This platform may not support registration of filesystem monitors!");
                    System.out.println("Forcing a reload.");
                    instance.loadCategories();
                    assertNotEquals(instance.getLastLoadTime(), oldTime);
                }
            }
            assertEquals(instance.getFieldList(), new TreeSet(Arrays.asList(new String[]{
                "testfield",
                LuceneEvaluator.DEFAULT_FIELD_NAME
            })));
            assertEquals(instance.evaluate(testMap), new TreeSet(Arrays.asList(new String[]{
                "alpha",
                "beta"
            })));

        }
    }

}
