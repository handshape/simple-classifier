package com.handshape.classifier.service;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import static org.testng.Assert.*;

/**
 *
 * @author jturner
 */
public class SimpleClassifierServiceNGTest {
    private static final String CHARSET = "UTF-8";

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
            System.out.println("  Started Service");
            JsonKey categoryKey = Jsoner.mintJsonKey("categories", null);
            // GET params
            System.out.println("  Testing GET");
            assertTrue(grabURL("http://localhost:8888/?text=elbows").getCollection(categoryKey).contains("positiveTest1"), "integration tests");
            // POST JSON
            System.out.println("  Testing POST JSON");
            assertTrue(postURL("http://localhost:8888/", "{\"text\":\"elbows\"}", "application/json; "+CHARSET).getCollection(categoryKey).contains("positiveTest1"), "integration tests");
            // POST url form encoding
            System.out.println("  Testing POST x-www-form-urlencoded");
            assertTrue(postURL("http://localhost:8888/", "text=elbows", "application/x-www-form-urlencoded").getCollection(categoryKey).contains("positiveTest1"), "integration tests");

            // Ensure that the form gets yielded.
            System.out.println("  Testing form autogeneration");
            Document doc = Jsoup.parse(new URL("http://localhost:8888/"), 1000);
            System.out.println(doc.toString());
            assertTrue(doc.select("input").size() == 3);
            assertTrue(doc.select("input[name=text]").size() == 1);
        } finally {
            service.stop();
        }
    }

    private JsonObject grabURL(String url) throws MalformedURLException, IOException, JsonException {
        return (JsonObject) Jsoner.deserialize(new InputStreamReader(new URL(url).openStream(), CHARSET));
    }

    private JsonObject postURL(String urlSpec, String body, String contentType) throws MalformedURLException, ProtocolException, IOException {
        URL url = new URL(urlSpec);

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        con.setRequestProperty("Content-Type", contentType);
        con.setRequestProperty("Accept", "application/json");

        con.setDoOutput(true);

        try ( OutputStream os = con.getOutputStream()) {
            byte[] input = body.getBytes(CHARSET);
            os.write(input, 0, input.length);
        }

        try ( BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), CHARSET))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return Jsoner.deserialize(response.toString(), new JsonObject());
        }
    }
}
