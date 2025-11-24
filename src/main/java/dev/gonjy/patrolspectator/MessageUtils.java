package dev.gonjy.patrolspectator;

import org.bukkit.entity.Player;

/**
 * メッセージ表示やタイトル表示に関するユーティリティクラス。
 */
public class MessageUtils {

    private static PatrolSpectatorPlugin.TitleConf titleConf;

    /**
     * 設定を初期化します。
     * 
     * @param conf タイトル設定
     */
    public static void init(PatrolSpectatorPlugin.TitleConf conf) {
        titleConf = conf;
    }

    /**
     * 観光タイトル表示（名称を大きく／「観光地」は小さく）
     * 
     * @param p    プレイヤー
     * @param name 観光地名
     */
    public static void showTourTitle(Player p, String name) {
        if (p == null || titleConf == null || !titleConf.enabled)
            return;
        try {
            // 上段を名称（大）、下段を「観光地」
            p.sendTitle("§l" + name, "§7観光地", titleConf.fadeIn, titleConf.stay, titleConf.fadeOut);
        } catch (Throwable ignored) {
        }
    }

    /**
     * タイトルを大きく（上段）と小さく（下段）で表示します。
     *
     * @param p        プレイヤー
     * @param title    上段のタイトル
     * @param subtitle 下段のサブタイトル
     */
    public static void showTitleLargeSmall(Player p, String title, String subtitle) {
        if (p == null || titleConf == null || !titleConf.enabled)
            return;
        try {
            p.sendTitle(title, subtitle, titleConf.fadeIn, titleConf.stay, titleConf.fadeOut);
        } catch (Throwable ignored) {
        }
    }

    /**
     * カラーコード付きの太字テキストを生成します。
     *
     * @param hexColor 16進数カラーコード（例: "#A5D6A7"）
     * @param text     テキスト内容
     * @return フォーマット済みテキスト
     */
    public static String textBold(String hexColor, String text) {
        return "§l" + hexColor + text;
    }

    /**
     * カラーコード付きのテキストを生成します。
     *
     * @param hexColor 16進数カラーコード（例: "#FFFFFF"）
     * @param text     テキスト内容
     * @return フォーマット済みテキスト
     */
    public static String text(String hexColor, String text) {
        return hexColor + text;
    }
}
