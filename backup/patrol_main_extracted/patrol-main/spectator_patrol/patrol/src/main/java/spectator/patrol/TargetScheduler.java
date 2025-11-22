package spectator.patrol;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TargetScheduler extends BukkitRunnable {
    private Player player;
    private CommandSender sender;

    public TargetScheduler(CommandSender sender,Player player) {
        this.player = player;
        this.sender = sender;
    }

    @Override
    public void run() {
        Player senderPlayer = (Player)sender;
        senderPlayer.setSpectatorTarget(player);
        String name = player.getName();
        senderPlayer.sendTitle(name,"さん視点",20,180,20);
    }
}