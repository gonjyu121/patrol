package spectator.patrol;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class BaseScheduler extends BukkitRunnable {
	JavaPlugin plugin;
    private int total;
    private CommandSender sender;
    

    public BaseScheduler(CommandSender sender,int total,JavaPlugin plugin) {
        this.plugin = plugin;
        this.sender = sender;
        this.total = total;
    }

    @Override
    public void run() {
        Player senderPlayer = (Player)sender;
        // 自分の分は抜く
        int max = sender.getServer().getOnlinePlayers().size() -1;
        if(max > 0){
            int cnt = 1;
            int interval = (int) Math.floor(total / max);
            int ticks = 0;
            senderPlayer.sendMessage("現在"+max+"人が遊んでいます。");
            // senderPlayer.sendMessage("一周"+total+"ticksなので");
            // senderPlayer.sendMessage("一人"+interval+"ticksで周回します");
            for (Player player : sender.getServer().getOnlinePlayers()) {
                if(!(player.getName().equals(senderPlayer.getName()))){
                    String excludePlayers = "KayaObana";
                    if(player.getName().equals(excludePlayers)) {
                        senderPlayer.sendMessage(cnt+".  "+player.getName()+"さん：除外");
                    } else {
                        new TpScheduler(sender, player, plugin).runTaskLater(plugin, ticks);
                        senderPlayer.sendMessage(cnt+".  "+player.getName()+"さん");
                        ticks += interval;
                    }
                    cnt++;
                }
            }
        } else {
            senderPlayer.sendMessage("他の人がいません・・・");
        }
    }
}