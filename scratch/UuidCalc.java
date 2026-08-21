import java.util.UUID;
import java.nio.charset.StandardCharsets;

public class UuidCalc {
    public static void main(String[] args) {
        String[] names = {"minato20131201", "gjtx", "banto2008", "TOWA2006", "miya2012", "Hopedguide89503"};
        for (String name : names) {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            System.out.println(name + ": " + uuid.toString());
        }
    }
}
