package litematica.shared;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

/**
 * Transport boundary for shared placements.
 *
 * The placement synchronization itself is intentionally independent of a
 * concrete backend. A tiny websocket/http bridge can implement this interface
 * and forward serialized placement states between clients.
 */
public interface SharedPlacementTransport
{
    void publish(JsonObject state);

    @Nullable
    JsonObject poll();
}
