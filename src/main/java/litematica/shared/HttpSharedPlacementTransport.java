package litematica.shared;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import litematica.Litematica;

/**
 * Small dependency-free HTTP transport for shared placements.
 *
 * Protocol:
 *   POST {baseUrl}/api/states  body: one SharedPlacementState JSON object
 *   GET  {baseUrl}/api/states  response: JSON array of latest state objects
 */
public class HttpSharedPlacementTransport implements SharedPlacementTransport
{
    protected final ConcurrentLinkedQueue<JsonObject> inbound = new ConcurrentLinkedQueue<>();
    protected final Map<String, Long> seenRevisions = new ConcurrentHashMap<>();
    protected final ScheduledExecutorService executor;
    protected final String baseUrl;
    @Nullable protected final String token;

    public HttpSharedPlacementTransport(String baseUrl, @Nullable String token)
    {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.token = token != null && token.isEmpty() == false ? token : null;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "litematica-shared-http");
            thread.setDaemon(true);
            return thread;
        });

        this.executor.scheduleWithFixedDelay(this::fetchStatesSafely, 0L, 500L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publish(JsonObject state)
    {
        JsonObject copy = state.deepCopy();
        this.executor.execute(() -> this.postStateSafely(copy));
    }

    @Override
    @Nullable
    public JsonObject poll()
    {
        return this.inbound.poll();
    }

    public void close()
    {
        this.executor.shutdownNow();
    }

    protected void postStateSafely(JsonObject state)
    {
        HttpURLConnection connection = null;

        try
        {
            connection = this.open("/api/states", "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = state.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);

            try (OutputStream out = connection.getOutputStream())
            {
                out.write(bytes);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300)
            {
                Litematica.LOGGER.warn("Shared placement POST failed with HTTP {}", status);
            }
        }
        catch (Exception e)
        {
            Litematica.LOGGER.debug("Shared placement POST failed", e);
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    protected void fetchStatesSafely()
    {
        HttpURLConnection connection = null;

        try
        {
            connection = this.open("/api/states", "GET");
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300)
            {
                return;
            }

            String payload = readAll(connection.getInputStream());
            JsonElement parsed = new JsonParser().parse(payload);

            if (parsed.isJsonArray() == false)
            {
                return;
            }

            JsonArray array = parsed.getAsJsonArray();

            for (JsonElement element : array)
            {
                if (element.isJsonObject() == false)
                {
                    continue;
                }

                JsonObject state = element.getAsJsonObject();
                if (state.has("id") == false || state.has("revision") == false)
                {
                    continue;
                }

                String id = state.get("id").getAsString();
                long revision = state.get("revision").getAsLong();
                Long seen = this.seenRevisions.get(id);

                if (seen == null || revision > seen)
                {
                    this.seenRevisions.put(id, revision);
                    this.inbound.add(state.deepCopy());
                }
            }
        }
        catch (Exception e)
        {
            Litematica.LOGGER.debug("Shared placement GET failed", e);
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    protected HttpURLConnection open(String path, String method) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(this.baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setUseCaches(false);

        if (this.token != null)
        {
            connection.setRequestProperty("Authorization", "Bearer " + this.token);
        }

        return connection;
    }

    protected static String readAll(InputStream input) throws Exception
    {
        StringBuilder builder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                builder.append(line);
            }
        }

        return builder.toString();
    }

    protected static String trimTrailingSlash(String value)
    {
        while (value.endsWith("/"))
        {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }
}
