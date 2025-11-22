package spectator.patrol;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TpScheduler extends BukkitRunnable {
	JavaPlugin plugin;
    private Player player;
    private CommandSender sender;

    public TpScheduler(CommandSender sender,Player player,JavaPlugin plugin) {
        this.plugin = plugin;
        this.player = player;
        this.sender = sender;
    }

    @Override
    public void run() {
        Player senderPlayer = (Player)sender;
        senderPlayer.teleport(player);
        new SpecScheduler(sender, player, plugin).runTaskLater(plugin, 8);
    }
}