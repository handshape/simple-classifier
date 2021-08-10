package com.handshape.classifier.service;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Microservice that serves the simple classifier. Entry point for the
 * application.
 *
 * @author jturner
 */
public class SimpleClassifierService {

    private File categoriesFile;
    private HttpServer server;

    /**
     * Entry point for the application.
     *
     * @param args The command line args passed from the OS
     * @throws Exception if the requested port can't be opened or the config
     * file can't be read
     */
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

    /**
     * Starts the service on the given port.
     *
     * @param port the port on which the service should listen.
     */
    public synchronized void start(int port) throws IOException {
        stop();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new EvaluationHandler());
        server.setExecutor(null);
        server.start();
    }

    /**
     * Shuts down the server.
     */
    public synchronized void stop() {
        if (server != null) {
            server.stop(5);
            server = null;
        }
    }

    private class EvaluationHandler implements HttpHandler {

        LuceneEvaluator evaluator;

        public EvaluationHandler() throws IOException {
            evaluator = new LuceneEvaluator(getCategoriesFile());
        }

        @Override
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_INFERRED", 
        justification = "No need to store references to elements that don't get used afterwards.")
        public void handle(HttpExchange exchange) throws IOException {
            //System.out.println(exchange.getRequestURI());
            Map<String, String> evaluationData = null;
            switch (exchange.getRequestMethod()) {
                case "GET":
                    evaluationData = splitQuery(exchange.getRequestURI());
                    if (evaluationData != null && !evaluationData.isEmpty()) {
                        evaluateAndRespond(evaluationData, exchange);
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
                        return;
                    }
                    break;
                case "POST":
                    try ( InputStream body = exchange.getRequestBody()) {
                    if (exchange.getRequestHeaders().containsKey("Content-Type")) {
                        if (exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/json")) {
                            JsonObject jsonBody = Jsoner.deserialize(IOUtils.toString(body, "UTF-8"), new JsonObject());
                            evaluationData = jsonToMap(jsonBody);
                        } else if (exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/x-www-form-urlencoded")) {
                            evaluationData = parseUrlFormEncoded(IOUtils.toString(body, "UTF-8"));
                        }
                    } else {
                        evaluationData = parseUrlFormEncoded(IOUtils.toString(body, "UTF-8"));
                    }
                    break;
                }
                default:
                    break;
            }
            evaluateAndRespond(evaluationData, exchange);
        }

        private void evaluateAndRespond(Map<String, String> evaluationData, HttpExchange exchange) throws IOException {
            int code = 200;
            String response;
            String contentType = "text/plain";
            JsonObject responseObject = new JsonObject();
            try {
                Set<String> categories = evaluator.evaluate(evaluationData);
                JsonArray categoriesArray = new JsonArray(categories);
                responseObject.put("categories", categoriesArray);
                response = responseObject.toJson();
                contentType = "application/json";
            } catch (Exception ex) {
                Logger.getLogger(SimpleClassifierService.class.getName()).log(Level.SEVERE, null, ex);
                response = "Error: " + ex.getMessage();
                code = 500;
            }
            sendResponse(exchange, code, contentType, response);
        }

        private void sendResponse(HttpExchange exchange, int responseCode, String contentType, String response) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(responseCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes("UTF-8"));
                os.flush();
            }
        }

        private Map<String, String> splitQuery(URI url) throws UnsupportedEncodingException {
            String query = url.getRawQuery();
            return parseUrlFormEncoded(query);
        }

        private Map<String, String> parseUrlFormEncoded(String query) throws UnsupportedEncodingException {
            Map<String, String> query_pairs = new LinkedHashMap<>();
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

        private Map<String, String> jsonToMap(JsonObject jsonBody) {
            TreeMap<String, String> returnable = new TreeMap<>();
            for (Entry<String, Object> entry : jsonBody.entrySet()) {
                returnable.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return returnable;
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
