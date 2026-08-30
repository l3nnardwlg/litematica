package litematica.shared;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import malilib.util.position.BlockMirror;
import malilib.util.position.BlockPos;
import malilib.util.position.BlockRotation;
import litematica.data.DataManager;
import litematica.schematic.placement.SchematicPlacement;
import litematica.schematic.placement.SchematicPlacementManager;

public class SharedPlacementManager
{
    public static final SharedPlacementManager INSTANCE = new SharedPlacementManager();
    public static final String SHARED_NAME_PREFIX = "[shared] ";

    protected final Map<SchematicPlacement, String> idsByPlacement = new HashMap<>();
    protected final Map<String, SchematicPlacement> placementsById = new HashMap<>();
    protected final Map<String, Long> revisions = new HashMap<>();

    @Nullable protected SharedPlacementTransport transport;
    protected boolean applyingRemoteState;

    protected SharedPlacementManager()
    {
    }

    public void setTransport(@Nullable SharedPlacementTransport transport)
    {
        this.transport = transport;
    }

    public String share(SchematicPlacement placement)
    {
        String id = this.idsByPlacement.get(placement);

        if (id == null)
        {
            id = createStableId(placement.getName());
            this.idsByPlacement.put(placement, id);
            this.placementsById.put(id, placement);
            this.revisions.put(id, 0L);
        }

        this.publish(placement);
        return id;
    }

    public void unshare(SchematicPlacement placement)
    {
        String id = this.idsByPlacement.remove(placement);

        if (id != null)
        {
            this.placementsById.remove(id);
            this.revisions.remove(id);
        }
    }

    public boolean isShared(SchematicPlacement placement)
    {
        return this.idsByPlacement.containsKey(placement);
    }

    public void autoDiscoverPlacements()
    {
        if (this.transport == null)
        {
            return;
        }

        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicPlacements())
        {
            if (placement.getName().startsWith(SHARED_NAME_PREFIX) && this.isShared(placement) == false)
            {
                this.share(placement);
            }
        }
    }

    public void registerRemotePlacement(String id, SchematicPlacement placement, long revision)
    {
        this.idsByPlacement.put(placement, id);
        this.placementsById.put(id, placement);
        this.revisions.put(id, revision);
    }

    public void onPlacementModified(SchematicPlacement placement)
    {
        if (this.applyingRemoteState == false && this.isShared(placement))
        {
            this.publish(placement);
        }
    }

    public void publish(SchematicPlacement placement)
    {
        if (this.transport != null)
        {
            this.transport.publish(this.snapshot(placement).toJson());
        }
    }

    public void pollRemoteUpdates()
    {
        if (this.transport == null)
        {
            return;
        }

        JsonObject obj;

        while ((obj = this.transport.poll()) != null)
        {
            this.apply(SharedPlacementState.fromJson(obj));
        }
    }

    public SharedPlacementState snapshot(SchematicPlacement placement)
    {
        String id = this.idsByPlacement.get(placement);

        if (id == null)
        {
            id = createStableId(placement.getName());
            this.idsByPlacement.put(placement, id);
            this.placementsById.put(id, placement);
            this.revisions.put(id, 0L);
        }

        long revision = this.revisions.containsKey(id) ? this.revisions.get(id) + 1L : 1L;
        this.revisions.put(id, revision);
        return SharedPlacementState.fromPlacement(id, placement, revision);
    }

    public boolean apply(SharedPlacementState state)
    {
        SchematicPlacement placement = this.placementsById.get(state.id);

        if (placement == null)
        {
            return false;
        }

        long currentRevision = this.revisions.containsKey(state.id) ? this.revisions.get(state.id) : -1L;

        if (state.revision <= currentRevision)
        {
            return false;
        }

        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        this.revisions.put(state.id, state.revision);
        this.applyingRemoteState = true;

        try
        {
            BlockPos target = new BlockPos(state.x, state.y, state.z);
            manager.setOrigin(placement, target);

            BlockRotation rotation = state.rotation;
            if (placement.getRotation() != rotation)
            {
                manager.setRotation(placement, rotation);
            }

            BlockMirror mirror = state.mirror;
            if (placement.getMirror() != mirror)
            {
                manager.setMirror(placement, mirror);
            }

            manager.setPlacementEnabledState(placement, state.enabled);
            return true;
        }
        finally
        {
            this.applyingRemoteState = false;
        }
    }

    protected static String createStableId(String placementName)
    {
        String normalized = placementName.startsWith(SHARED_NAME_PREFIX)
                ? placementName.substring(SHARED_NAME_PREFIX.length()).trim().toLowerCase()
                : placementName.trim().toLowerCase();
        return UUID.nameUUIDFromBytes(("litematica-shared:" + normalized).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
