package dev.gonjy.patrolspectator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * メッセージ表示やタイトル表示に関するユーティリティクラス。
 */
public class MessageUtils {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
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
            showTitle(p,
                    Component.text(name).decorate(TextDecoration.BOLD),
                    Component.text("観光地", NamedTextColor.GRAY));
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
            showTitle(p, LEGACY_SECTION.deserialize(title), LEGACY_SECTION.deserialize(subtitle));
        } catch (Throwable ignored) {
        }
    }

    private static void showTitle(Player player, Component title, Component subtitle) {
        player.showTitle(Title.title(
                title,
                subtitle,
                Title.Times.times(
                        ticksToDuration(titleConf.fadeIn),
                        ticksToDuration(titleConf.stay),
                        ticksToDuration(titleConf.fadeOut))));
    }

    private static Duration ticksToDuration(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
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
