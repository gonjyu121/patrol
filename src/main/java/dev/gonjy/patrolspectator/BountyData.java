package dev.gonjy.patrolspectator;

import java.util.UUID;

/**
 * 賞金首データを保持するクラス
 */
public class BountyData {
    private final UUID targetId;
    private final String targetName;
    private final double amount;
    private final long expirationTime;
    private final String issuer; // 賞金をかけた人（システムの場合は "System"）

    public BountyData(UUID targetId, String targetName, double amount, long expirationTime, String issuer) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.amount = amount;
        this.expirationTime = expirationTime;
        this.issuer = issuer;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public double getAmount() {
        return amount;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public String getIssuer() {
        return issuer;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}
