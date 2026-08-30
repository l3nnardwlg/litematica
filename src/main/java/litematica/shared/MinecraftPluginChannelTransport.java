package litematica.shared;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netty.buffer.Unpooled;
import malilib.util.data.json.JsonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

/**
 * Shared placement transport over Minecraft's existing plugin-message connection.
 *
 * No additional TCP/HTTP service is required. A tiny Bukkit/Paper plugin relays
 * messages sent on {@link #CHANNEL} between connected clients and assigns the
 * authoritative revision sequence.
 */
public class MinecraftPluginChannelTransport implements SharedPlacementTransport
{
    public static final MinecraftPluginChannelTransport INSTANCE = new MinecraftPluginChannelTransport();
    public static final String CHANNEL = "LitematicaShared";

    private static final int CLIENT_UPDATE = 1;
    private static final int CLIENT_SYNC_REQUEST = 2;
    private static final int SERVER_UPDATE = 1;
    private static final int SERVER_SYNC_DONE = 3;

    private final Queue<JsonObject> incoming = new ConcurrentLinkedQueue<>();
    private final Map<String, JsonObject> pendingUpdates = new LinkedHashMap<>();

    @Nullable private NetHandlerPlayClient lastConnection;
    private boolean ready;

    private MinecraftPluginChannelTransport()
    {
    }

    @Override
    public synchronized void publish(JsonObject state)
    {
        this.ensureRegistered();

        JsonElement idElement = state.get("id");
        if (idElement == null)
        {
            return;
        }

        String id = idElement.getAsString();

        if (this.ready == false)
        {
            // Coalesce local edits while waiting for the server snapshot. This
            // prevents a joining client from overwriting an already shared
            // placement before it has received the authoritative state.
            this.pendingUpdates.put(id, state);
            return;
        }

        this.sendUpdate(id, state);
    }

    @Override
    @Nullable
    public JsonObject poll()
    {
        this.ensureRegistered();
        return this.incoming.poll();
    }

    public boolean handles(SPacketCustomPayload packet)
    {
        return CHANNEL.equals(packet.getChannelName());
    }

    public synchronized void handlePayload(SPacketCustomPayload packet)
    {
        if (this.handles(packet) == false)
        {
            return;
        }

        PacketBuffer buffer = packet.getBufferData();
        if (buffer == null || buffer.readableBytes() < 1)
        {
            return;
        }

        int type = buffer.readUnsignedByte();

        if (type == SERVER_UPDATE)
        {
            if (buffer.readableBytes() < 12)
            {
                return;
            }

            long revision = buffer.readLong();
            int idLength = buffer.readInt();

            if (idLength < 1 || idLength > 256 || buffer.readableBytes() < idLength)
            {
                return;
            }

            byte[] idBytes = new byte[idLength];
            buffer.readBytes(idBytes);
            String id = new String(idBytes, StandardCharsets.UTF_8);

            byte[] jsonBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            JsonElement element = JsonUtils.parseJsonFromString(json);

            if (element != null && element.isJsonObject())
            {
                // If the server already has this placement, its snapshot wins
                // over any local state queued during initial connection.
                this.pendingUpdates.remove(id);

                JsonObject state = element.getAsJsonObject();
                state.addProperty("id", id);
                state.addProperty("revision", revision);
                this.incoming.add(state);
            }
        }
        else if (type == SERVER_SYNC_DONE)
        {
            this.ready = true;
            this.flushPendingUpdates();
        }
    }

    public synchronized void reset()
    {
        this.lastConnection = null;
        this.ready = false;
        this.pendingUpdates.clear();
        this.incoming.clear();
    }

    private void ensureRegistered()
    {
        NetHandlerPlayClient connection = Minecraft.getMinecraft().getConnection();

        if (connection == null)
        {
            if (this.lastConnection != null)
            {
                this.reset();
            }
            return;
        }

        if (connection == this.lastConnection)
        {
            return;
        }

        this.lastConnection = connection;
        this.ready = false;
        this.incoming.clear();
        this.pendingUpdates.clear();

        PacketBuffer register = new PacketBuffer(Unpooled.buffer());
        register.writeBytes(CHANNEL.getBytes(StandardCharsets.UTF_8));
        connection.sendPacket(new CPacketCustomPayload("REGISTER", register));

        PacketBuffer request = new PacketBuffer(Unpooled.buffer());
        request.writeByte(CLIENT_SYNC_REQUEST);
        connection.sendPacket(new CPacketCustomPayload(CHANNEL, request));
    }

    private void sendUpdate(String id, JsonObject state)
    {
        NetHandlerPlayClient connection = Minecraft.getMinecraft().getConnection();
        if (connection == null)
        {
            return;
        }

        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] jsonBytes = state.toString().getBytes(StandardCharsets.UTF_8);

        if (idBytes.length > 256 || jsonBytes.length > 30000)
        {
            return;
        }

        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer(1 + 4 + idBytes.length + jsonBytes.length));
        buffer.writeByte(CLIENT_UPDATE);
        buffer.writeInt(idBytes.length);
        buffer.writeBytes(idBytes);
        buffer.writeBytes(jsonBytes);
        connection.sendPacket(new CPacketCustomPayload(CHANNEL, buffer));
    }

    private void flushPendingUpdates()
    {
        if (this.ready == false || this.pendingUpdates.isEmpty())
        {
            return;
        }

        Map<String, JsonObject> copy = new LinkedHashMap<>(this.pendingUpdates);
        this.pendingUpdates.clear();

        for (Map.Entry<String, JsonObject> entry : copy.entrySet())
        {
            this.sendUpdate(entry.getKey(), entry.getValue());
        }
    }
}
