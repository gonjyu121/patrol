package spectator.patrol;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SpecScheduler extends BukkitRunnable {
    JavaPlugin plugin;
    private Player player;
    private CommandSender sender;

    public SpecScheduler(CommandSender sender,Player player,JavaPlugin plugin) {
        this.plugin = plugin;
        this.player = player;
        this.sender = sender;
    }

    @Override
    public void run() {
        Player senderPlayer = (Player)sender;
        senderPlayer.setGameMode(GameMode.SPECTATOR);
        new TargetScheduler(sender, player).runTaskLater(plugin, 5);
    }
}