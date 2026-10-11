package dev.gonjy.patrolspectator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * CloudSyncの認証が拒否された後、同じ資格情報で無駄な通信を繰り返さないための状態管理。
 */
final class CloudSyncFailureGuard {
    private final Logger log;
    private final AtomicBoolean authenticationRejected = new AtomicBoolean(false);

    CloudSyncFailureGuard(Logger log) {
        this.log = log;
    }

    boolean canRequest() {
        return !authenticationRejected.get();
    }

    void recordHttpFailure(String operation, int statusCode) {
        if (statusCode == 401 && authenticationRejected.compareAndSet(false, true)) {
            log.warning("[CloudSync] GitHub認証が拒否されました (HTTP 401)。"
                    + "設定を修正してサーバーを再起動するまで同期を停止します。");
            return;
        }
        if (statusCode != 401) {
            log.warning("[CloudSync] " + operation + "失敗 (HTTP " + statusCode + ")。次回の同期で再試行します。");
        }
    }
}
