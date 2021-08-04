package com.handshape.classifier.service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
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

}
