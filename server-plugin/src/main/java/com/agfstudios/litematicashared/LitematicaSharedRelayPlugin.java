package com.agfstudios.litematicashared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class LitematicaSharedRelayPlugin extends JavaPlugin implements PluginMessageListener
{
    public static final String CHANNEL = "LitematicaShared";

    private static final int CLIENT_UPDATE = 1;
    private static final int CLIENT_SYNC_REQUEST = 2;
    private static final int SERVER_UPDATE = 1;
    private static final int SERVER_SYNC_DONE = 3;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, byte[]> latestStates = new LinkedHashMap<>();

    @Override
    public void onEnable()
    {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getLogger().info("Listening on plugin channel " + CHANNEL);
    }

    @Override
    public void onDisable()
    {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        this.latestStates.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message)
    {
        if (CHANNEL.equals(channel) == false || message == null || message.length < 1)
        {
            return;
        }

        try
        {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            int type = in.readUnsignedByte();

            if (type == CLIENT_UPDATE)
            {
                this.handleUpdate(in);
            }
            else if (type == CLIENT_SYNC_REQUEST)
            {
                this.sendSnapshot(player);
            }
        }
        catch (IOException | RuntimeException e)
        {
            getLogger().warning("Ignoring malformed shared placement packet from " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleUpdate(DataInputStream in) throws IOException
    {
        int idLength = in.readInt();

        if (idLength < 1 || idLength > 256)
        {
            throw new IOException("invalid id length");
        }

        byte[] idBytes = new byte[idLength];
        in.readFully(idBytes);
        String id = new String(idBytes, StandardCharsets.UTF_8);

        int jsonLength = in.available();
        if (jsonLength < 2 || jsonLength > 30000)
        {
            throw new IOException("invalid state length");
        }

        byte[] json = new byte[jsonLength];
        in.readFully(json);

        long revision = this.sequence.incrementAndGet();
        byte[] outgoing = encodeServerUpdate(revision, idBytes, json);
        this.latestStates.put(id, outgoing);
        this.broadcast(outgoing);
    }

    private void sendSnapshot(Player player)
    {
        for (byte[] state : this.latestStates.values())
        {
            send(player, state);
        }

        send(player, new byte[] { (byte) SERVER_SYNC_DONE });
    }

    private void broadcast(byte[] payload)
    {
        for (Player target : Bukkit.getOnlinePlayers())
        {
            if (target.getListeningPluginChannels().contains(CHANNEL))
            {
                send(target, payload);
            }
        }
    }

    private void send(Player player, byte[] payload)
    {
        if (player.isOnline() && player.getListeningPluginChannels().contains(CHANNEL))
        {
            player.sendPluginMessage(this, CHANNEL, payload);
        }
    }

    private static byte[] encodeServerUpdate(long revision, byte[] idBytes, byte[] json) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 + 8 + 4 + idBytes.length + json.length);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(SERVER_UPDATE);
        out.writeLong(revision);
        out.writeInt(idBytes.length);
        out.write(idBytes);
        out.write(json);
        out.flush();
        return bytes.toByteArray();
    }
}
