package com.handshape.classifier.service;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 *
 * @author jturner
 */
public class SimpleClassifierService {

    private File categoriesFile;
    private HttpServer server;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("This service takes two parameters: a port, and a path to a .properties file.");
            System.err.println("Defaulting to port 9090 and a file named 'categories.properties' in the current working directory.");
            //System.exit(-1);
            args = new String[]{"9090", "categories.properties"};
        }

        int port = Integer.parseInt(args[0]);
        SimpleClassifierService service = new SimpleClassifierService();
        service.setCategoriesFile(new File(args[1]));
        service.start(port);
    }

    public synchronized void start(int port) throws Exception {
        stop();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new EvaluationHandler());
        server.setExecutor(null);
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(5);
            server = null;
        }
    }

    class EvaluationHandler implements HttpHandler {

        LuceneEvaluator evaluator;

        public EvaluationHandler() throws IOException, ParseException {
            evaluator = new LuceneEvaluator(getCategoriesFile());
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println(exchange.getRequestURI());
            Map<String, String> evaluationData = splitQuery(exchange.getRequestURI());
            if (evaluationData != null && !evaluationData.isEmpty()) {
                int code = 200;
                String response;
                JsonObject responseObject = new JsonObject();
                try {
                    Set<String> categories = evaluator.evaluate(evaluationData);
                    JsonArray categoriesArray = new JsonArray(categories);
                    responseObject.put("categories", categoriesArray);
                } catch (Exception ex) {
                    Logger.getLogger(SimpleClassifierService.class.getName()).log(Level.SEVERE, null, ex);
                    response = "Error: " + ex.getMessage();
                    code = 500;
                }
                sendResponse(exchange, code, "application/json", responseObject.toJson());
            } else {
                Document doc = Jsoup.parse(getClass().getResourceAsStream("/www/index.html"), "UTF-8", exchange.getRequestURI().toASCIIString());
                Element form = doc.body().appendElement("form");
                form.attr("method", "GET");
                form.attr("action", exchange.getRequestURI().toASCIIString());
                for (String field : evaluator.getFieldList()) {
                    form.appendText(field);
                    form.appendElement("br");
                    form.appendElement("input").attr("type", "text").attr("class", "featureField").attr("name", field);
                    form.appendElement("br");
                }
                form.appendElement("input").attr("type", "submit");
                sendResponse(exchange, 200, "text/html", doc.outerHtml());
            }
        }

        private void sendResponse(HttpExchange exchange, int responseCode, String contentType, String response) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(responseCode, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        public Map<String, String> splitQuery(URI url) throws UnsupportedEncodingException {
            Map<String, String> query_pairs = new LinkedHashMap<>();
            String query = url.getRawQuery();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                    }
                }
            }
            return query_pairs;
        }
    }

    /**
     * @return the categoriesFile
     */
    public File getCategoriesFile() {
        return categoriesFile;
    }

    /**
     * @param categoriesFile the categoriesFile to set
     */
    public void setCategoriesFile(File categoriesFile) {
        this.categoriesFile = categoriesFile;
    }

}
