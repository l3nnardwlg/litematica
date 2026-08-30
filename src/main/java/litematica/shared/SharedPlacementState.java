package litematica.shared;

import com.google.gson.JsonObject;

import malilib.util.position.BlockMirror;
import malilib.util.position.BlockPos;
import malilib.util.position.BlockRotation;
import litematica.schematic.placement.SchematicPlacement;

public class SharedPlacementState
{
    public final String id;
    public final String schematicName;
    public final int x;
    public final int y;
    public final int z;
    public final BlockRotation rotation;
    public final BlockMirror mirror;
    public final boolean enabled;
    public final long revision;

    public SharedPlacementState(String id,
                                String schematicName,
                                int x,
                                int y,
                                int z,
                                BlockRotation rotation,
                                BlockMirror mirror,
                                boolean enabled,
                                long revision)
    {
        this.id = id;
        this.schematicName = schematicName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation;
        this.mirror = mirror;
        this.enabled = enabled;
        this.revision = revision;
    }

    public static SharedPlacementState fromPlacement(String id, SchematicPlacement placement, long revision)
    {
        BlockPos pos = placement.getPosition();
        return new SharedPlacementState(id,
                                        placement.getName(),
                                        pos.getX(),
                                        pos.getY(),
                                        pos.getZ(),
                                        placement.getRotation(),
                                        placement.getMirror(),
                                        placement.isEnabled(),
                                        revision);
    }

    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", this.id);
        obj.addProperty("schematicName", this.schematicName);
        obj.addProperty("x", this.x);
        obj.addProperty("y", this.y);
        obj.addProperty("z", this.z);
        obj.addProperty("rotation", this.rotation.name());
        obj.addProperty("mirror", this.mirror.name());
        obj.addProperty("enabled", this.enabled);
        obj.addProperty("revision", this.revision);
        return obj;
    }

    public static SharedPlacementState fromJson(JsonObject obj)
    {
        return new SharedPlacementState(obj.get("id").getAsString(),
                                        obj.get("schematicName").getAsString(),
                                        obj.get("x").getAsInt(),
                                        obj.get("y").getAsInt(),
                                        obj.get("z").getAsInt(),
                                        BlockRotation.valueOf(obj.get("rotation").getAsString()),
                                        BlockMirror.valueOf(obj.get("mirror").getAsString()),
                                        obj.get("enabled").getAsBoolean(),
                                        obj.get("revision").getAsLong());
    }
}
