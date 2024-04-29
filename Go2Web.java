import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class Go2Web {

    private static final String CACHE_FILENAME = "data.cache";
    private static Map<String, String> cache = new HashMap<>();

    public static void main(String[] args) {
        loadCache();
        if (args.length == 0 || "-h".equals(args[0])) {
            showHelp();
            return;
        }
        String option = args[0];
        if ("-u".equals(option) && args.length > 1) {
            fetchUrl(args[1]);
        } else if ("-s".equals(option) && args.length > 1) {
            searchGoogle(args[1]);
        } else {
            System.out.println("Unknown option. Use '-h' for help.");
        }
        saveCache();
    }

    private static void showHelp() {
        System.out.println("Commands:");
        System.out.println("  Go2Web -u <URL>            Fetch and display content from <URL>");
        System.out.println("  Go2Web -s <search-query>    Search <search-query> on Google and display results");
        System.out.println("  Go2Web -h                  Show this help message");
    }

    private static void displayWebContent(String html) {
        System.out.println("Content of the page:");
        Pattern headerPattern = Pattern.compile("<(h[1-6])[^>]*>(.*?)</\\1>", Pattern.CASE_INSENSITIVE);
        Pattern linkPattern = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE);

        Matcher headerMatcher = headerPattern.matcher(html);
        while (headerMatcher.find()) {
            System.out.println("  - " + headerMatcher.group(2).trim());
        }

        Matcher linkMatcher = linkPattern.matcher(html);
        while (linkMatcher.find()) {
            System.out.println("Link: " + linkMatcher.group(1).trim() + linkMatcher.group(2).trim());
        }
    }


    private static void fetchUrl(String url) {
        if (cache.containsKey(url)) {
            System.out.println("Cache hit for URL: " + url);
            displayWebContent(cache.get(url));
        } else {
            try {
                URL targetUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection)targetUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false); // Do not automatically follow redirects
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = connection.getHeaderField("Location");
                    System.out.println("Redirecting to: " + newUrl);
                    fetchUrl(newUrl);  // Recursive call to handle redirect
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = reader.readLine()) != null) {
                    response.append(inputLine);
                }
                reader.close();
                String responseBody = response.toString();
                cache.put(url, responseBody); // Cache the raw HTML
                displayWebContent(responseBody); // Parse and display the content
            } catch (IOException e) {
                System.out.println("Error fetching URL: " + e.getMessage());
            }
        }
    }


    private static String stripHtml(String html) {
        // Remove all HTML tags
        String noHtml = html.replaceAll("<[^>]*>", "");
        // Decode HTML entities
        return noHtml.replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&nbsp;", " ");
    }

    private static void searchGoogle(String searchTerm) {
        try {
            String searchUrl = "https://www.google.com/search?q=" + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
            HttpURLConnection connection = (HttpURLConnection) new URL(searchUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.addRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
                content.append(System.lineSeparator());
            }
            in.close();
            String responseContent = content.toString();
            String searchResults = extractLinks(responseContent);
            System.out.println(searchResults);
        } catch (IOException e) {
            System.out.println("Error performing search: " + e.getMessage());
        }
    }

    private static void loadCache() {
        File cacheFile = new File(CACHE_FILENAME);
        if (cacheFile.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(cacheFile))) {
                cache = (Map<String, String>) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error reading cache file: " + e.getMessage());
                cache = new HashMap<>(); // Initialize empty cache in case of error
            }
        } else {
            System.out.println("Cache file not found. Initializing new cache.");
            cache = new HashMap<>(); // Initialize empty cache if file does not exist
        }
    }

    private static void saveCache() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(CACHE_FILENAME))) {
            out.writeObject(cache);
        } catch (IOException e) {
            System.out.println("Error saving cache: " + e.getMessage());
        }
    }


    private static String extractLinks(String html) {
        List<String> links = new ArrayList<>();
        // Pattern to extract Google search results links
        Matcher matcher = Pattern.compile("href=\"/url\\?q=(.*?)&").matcher(html);

        while (matcher.find()) {
            String link = matcher.group(1); // Extract the URL part from the matcher
            try {
                // Decode the URL encoding in the link
                String decodedLink = URLDecoder.decode(link, StandardCharsets.UTF_8.name());
                // Filter out non-http links to ensure we're capturing valid URLs
                if (!decodedLink.startsWith("http")) {
                    continue;
                }
                links.add(decodedLink);
                if (links.size() == 10) {
                    break;
                }
            } catch (UnsupportedEncodingException e) {
                System.out.println("Error decoding URL: " + e.getMessage());
            }
        }
        return String.join("\n", links);
    }

}

