package dev.gonjy.patrolspectator;

import java.util.UUID;

/**
 * プレイヤーの保護データを管理するクラス
 */
public class ProtectionData {
    private UUID playerId;
    private long startTime;
    private long duration;
    private int radius;
    private long lastActivityTime;
    private boolean isActive;
    
    public ProtectionData(UUID playerId) {
        this.playerId = playerId;
        this.startTime = System.currentTimeMillis();
        this.duration = 1800000L; // 30分
        this.radius = 5; // 5ブロック
        this.lastActivityTime = System.currentTimeMillis();
        this.isActive = true;
    }
    
    public ProtectionData(UUID playerId, long duration, int radius) {
        this.playerId = playerId;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
        this.radius = radius;
        this.lastActivityTime = System.currentTimeMillis();
        this.isActive = true;
    }
    
    // Getters
    public UUID getPlayerId() { return playerId; }
    public long getStartTime() { return startTime; }
    public long getDuration() { return duration; }
    public int getRadius() { return radius; }
    public long getLastActivityTime() { return lastActivityTime; }
    public boolean isActive() { return isActive; }
    
    // Setters
    public void setDuration(long duration) { this.duration = duration; }
    public void setRadius(int radius) { this.radius = radius; }
    public void setLastActivityTime(long lastActivityTime) { this.lastActivityTime = lastActivityTime; }
    public void setActive(boolean active) { isActive = active; }
    
    // 保護残り時間を取得（ミリ秒）
    public long getRemainingTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, duration - elapsed);
    }
    
    // 保護が有効かチェック
    public boolean isValid() {
        return isActive && getRemainingTime() > 0;
    }
    
    // 保護時間を延長
    public void extendDuration(long additionalTime) {
        this.duration += additionalTime;
    }
    
    // 保護範囲を拡大
    public void extendRadius(int additionalRadius) {
        this.radius += additionalRadius;
    }
    
    // 活動時間を更新
    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }
}
