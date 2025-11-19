#!/bin/bash

# プラグイン更新スクリプト
# 使用方法: ./update_plugins.sh

set -e  # エラー時に停止

echo "🚀 プラグイン更新スクリプト開始"
echo "=================================="

# プラグインディレクトリに移動
cd "$(dirname "$0")/plugins"

# バックアップディレクトリを作成
BACKUP_DIR="backup_$(date +%Y%m%d_%H%M%S)"
echo "📁 バックアップディレクトリ作成: $BACKUP_DIR"
mkdir -p "$BACKUP_DIR"

# 既存プラグインをバックアップ
echo "💾 既存プラグインをバックアップ中..."
cp *.jar "$BACKUP_DIR/" 2>/dev/null || echo "⚠️  バックアップ対象のプラグインが見つかりません"

# プラグインのダウンロードURL定義
declare -A PLUGIN_URLS=(
    ["Geyser-Spigot"]="https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
    ["Floodgate"]="https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
    ["ViaVersion"]="https://github.com/ViaVersion/ViaVersion/releases/latest/download/ViaVersion.jar"
    ["ViaBackwards"]="https://github.com/ViaVersion/ViaBackwards/releases/latest/download/ViaBackwards.jar"
    ["ViaRewind"]="https://github.com/ViaVersion/ViaRewind/releases/latest/download/ViaRewind.jar"
)

# プラグインをダウンロード
echo "⬇️  プラグインをダウンロード中..."

# GitHub APIから最新アセットURLを取得してダウンロード（jq非依存）
download_latest_github() {
    local repo="$1"          # 例: ViaVersion/ViaVersion
    local filename_prefix="$2" # 例: ViaVersion
    local out="$3"           # 保存ファイル名
    local api="https://api.github.com/repos/${repo}/releases/latest"
    echo "   ↪ API: $api"
    json="$(curl -fsSL -H 'User-Agent: patrol-updater' "$api" || true)"
    if [ -z "$json" ]; then
        echo "   ⚠️  GitHub API取得に失敗"
        return 1
    fi
    # 1) プレフィックス付きのJarを優先
    url=$(printf "%s" "$json" \
        | grep -Eo '"browser_download_url": "[^"]+\.jar"' \
        | sed -E 's/.*: "([^"]+)"/\1/' \
        | grep -Ei "/${filename_prefix}-[^/]+\\.jar$" \
        | head -n1)
    # 2) 見つからなければ最初のJar（sources/javadoc除外）
    if [ -z "$url" ]; then
        url=$(printf "%s" "$json" \
            | grep -Eo '"browser_download_url": "[^"]+\.jar"' \
            | sed -E 's/.*: "([^"]+)"/\1/' \
            | grep -Ei '\.jar$' \
            | grep -viE 'sources|javadoc' \
            | head -n1)
    fi
    if [ -z "$url" ]; then
        echo "   ⚠️  アセットURLが見つかりません（$repo）"
        return 1
    fi
    echo "   ↪ URL: $url"
    if curl -fsSL -o "${out}.tmp" -H 'User-Agent: patrol-updater' -L "$url"; then
        mv "${out}.tmp" "$out"
        echo "✅ $out ダウンロード完了"
        return 0
    fi
    echo "   ⚠️  ダウンロード失敗: $url"
    rm -f "${out}.tmp"
    return 1
}

for plugin in "${!PLUGIN_URLS[@]}"; do
    url="${PLUGIN_URLS[$plugin]}"
    filename="${plugin}.jar"
    echo "📥 $plugin をダウンロード中..."
    case "$plugin" in
        ViaVersion)
            download_latest_github "ViaVersion/ViaVersion" "ViaVersion" "$filename" || wget -q -O "$filename" "$url" || echo "❌ $plugin のダウンロードに失敗しました" ;;
        ViaBackwards)
            download_latest_github "ViaVersion/ViaBackwards" "ViaBackwards" "$filename" || wget -q -O "$filename" "$url" || echo "❌ $plugin のダウンロードに失敗しました" ;;
        ViaRewind)
            download_latest_github "ViaVersion/ViaRewind" "ViaRewind" "$filename" || wget -q -O "$filename" "$url" || echo "❌ $plugin のダウンロードに失敗しました" ;;
        *)
            if wget -q -O "${filename}.tmp" "$url"; then
                mv "${filename}.tmp" "$filename"
                echo "✅ $plugin ダウンロード完了"
            else
                echo "❌ $plugin のダウンロードに失敗しました"
                rm -f "${filename}.tmp"
            fi
            ;;
    esac
done

# PatrolSpectatorPluginをビルド
echo "🔨 PatrolSpectatorPluginをビルド中..."
cd ..
if mvn -q -DskipTests package; then
    LATEST_JAR=$(ls -t target/patrol-spectator-plugin-*.jar | head -n1)
    cp "$LATEST_JAR" plugins/
    echo "✅ PatrolSpectatorPlugin ビルド完了"
else
    echo "❌ PatrolSpectatorPlugin のビルドに失敗しました"
fi

cd plugins

# 結果を表示
echo ""
echo "📊 更新結果:"
echo "=================================="
ls -la *.jar | while read line; do
    echo "📦 $line"
done

echo ""
echo "🎉 プラグイン更新完了！"
echo "📁 バックアップ: $BACKUP_DIR"
echo "💡 サーバーを再起動して更新を反映してください"