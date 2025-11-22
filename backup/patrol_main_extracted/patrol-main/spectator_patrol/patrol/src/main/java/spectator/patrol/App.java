package spectator.patrol;

import java.util.ArrayList;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class App extends JavaPlugin
{
    private int total = 100 * 13;
    private ArrayList<BukkitTask> tasks = new ArrayList<BukkitTask>();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // test コマンドの処理
        if(command.getName().equalsIgnoreCase("patrol")){
            Player senderPlayer = (Player)sender;
            if (args.length == 0) { //サブコマンドの個数が0、つまりサブコマンド無し
                
                senderPlayer.sendMessage("みんなの様子を巡回します");
                senderPlayer.setGameMode(GameMode.SPECTATOR);
                //int max = sender.getServer().getOnlinePlayers().size();
                BukkitTask task = new BaseScheduler(sender,total,this).runTaskTimer(this, 0, total);
                tasks.add(task);
                return true; //終わり
            } else { //サブコマンドの個数が0以外
                if (args[0].equalsIgnoreCase("end")) { //サブコマンドが「end」かどうか
                    for (BukkitTask task : tasks) {
                        // タスクを中止する
                        task.cancel();
                    }
                    //タスクをすべて忘れる
                    tasks.clear();
                    senderPlayer.sendMessage("巡回を終了します");
                    return true; //終わり
                } else { //サブコマンドが「end」以外
                    return true; //終わり
                }
            }
        } else {
            return false;
        }
    }

}
