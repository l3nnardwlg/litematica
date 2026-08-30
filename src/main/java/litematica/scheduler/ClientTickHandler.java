package litematica.scheduler;

import malilib.gui.util.GuiUtils;
import malilib.util.game.wrap.GameWrap;
import litematica.data.DataManager;
import litematica.schematic.verifier.SchematicVerifierManager;
import litematica.shared.SharedPlacementManager;
import litematica.util.EasyPlaceUtils;

public class ClientTickHandler implements malilib.event.ClientTickHandler
{
    protected int tickCounter;

    @Override
    public void onClientTick()
    {
        if (GameWrap.getClientPlayer() == null || GameWrap.getClientWorld() == null)
        {
            return;
        }

        DataManager.getRenderLayerRange().followPlayerIfEnabled(GameWrap.getClientPlayer());
        DataManager.getSchematicPlacementManager().processQueuedChunks();
        TaskScheduler.getInstanceClient().runTasks();
        SharedPlacementManager.INSTANCE.pollRemoteUpdates();

        if ((this.tickCounter) % 10 == 0)
        {
            SchematicVerifierManager.INSTANCE.scheduleReChecks();
            SharedPlacementManager.INSTANCE.syncLocalChanges();
        }

        if (GuiUtils.noScreenOpen())
        {
            EasyPlaceUtils.easyPlaceOnUseTick();
        }

        ++this.tickCounter;
    }
}
