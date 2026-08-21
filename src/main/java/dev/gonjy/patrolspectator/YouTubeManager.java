package dev.gonjy.patrolspectator;

import org.bukkit.plugin.java.JavaPlugin;

// import com.google.api.client.auth.oauth2.TokenResponse;
// import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
// import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
// import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
// import com.google.api.client.http.javanet.NetHttpTransport;
// import com.google.api.client.json.JsonFactory;
// import com.google.api.client.json.jackson2.JacksonFactory;
// import com.google.api.services.youtube.YouTube;
// import com.google.api.services.youtube.model.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.logging.Level;

/**
 * YouTube Live Chat integration manager.
 * (Temporarily disabled for build stability)
 */
@SuppressWarnings("unused")
public class YouTubeManager {

    private final JavaPlugin plugin;
    // private YouTube youtubeService;
    private String liveChatId;
    private boolean enabled = false;

    // Credentials
    private String clientId;
    private String clientSecret;
    private String refreshToken;

    // Safety & Quota
    private static final int MAX_DAILY_QUOTA = 100;
    private int dailyMessageCount = 0;
    private LocalDate lastQuotaResetDate = LocalDate.now();

    // private static final JsonFactory JSON_FACTORY =
    // JacksonFactory.getDefaultInstance();

    public YouTubeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize(String clientId, String clientSecret, String refreshToken) {
        // Disabled
        this.enabled = false;
    }

    public void refreshLiveChatId() {
        // Disabled
    }

    public synchronized void sendMessage(String message) {
        // Disabled
    }

    public boolean isEnabled() {
        return enabled;
    }
}
