package com.handshape.classifier.service;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import static org.testng.Assert.*;

/**
 *
 * @author jturner
 */
public class SimpleClassifierServiceNGTest {

    public SimpleClassifierServiceNGTest() {
    }

    @org.testng.annotations.Test
    public void testIntegration() throws Exception {
        System.out.println("Integration test");

        SimpleClassifierService service = new SimpleClassifierService();
        try {
            // Test the evaluation part.
            service.setCategoriesFile(new File(getClass().getResource("/testcategories.properties").toURI()));
            service.start(8888);
            JsonKey categoryKey = Jsoner.mintJsonKey("categories", null);
            assertTrue(grabURL("http://localhost:8888/?text=elbows").getCollection(categoryKey).contains("positiveTest1"), "integration tests");
            Document doc = Jsoup.parse(new URL("http://localhost:8888/"), 1000);
            System.out.println(doc.toString());
            assertTrue(doc.select("input").size() == 3);
            assertTrue(doc.select("input[name=text]").size() == 1);
        } finally {
            service.stop();
        }
    }

    private JsonObject grabURL(String url) throws MalformedURLException, IOException, JsonException {
        return (JsonObject) Jsoner.deserialize(new InputStreamReader(new URL(url).openStream(), "UTF-8"));
    }
}
