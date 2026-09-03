#SingleInstance Force

; シンプルなAFK防止 ＆ バニラ自動再接続スクリプト (AutoHotkey v2) - v3
; Minecraftフォーカス時のみ動作

; グローバル変数
global afkActive := false
global reconnecting := false
global lastFileSize := 0
global logPath := A_AppData "\.minecraft\logs\latest.log"

; 起動メッセージ
MsgBox("AFK防止 ＆ 自動再接続スクリプト起動 (v3)`n`n"
     . "【ホットキー】`n"
     . "F6: AFK防止開始`n"
     . "F7: AFK防止停止`n"
     . "F8: 終了`n`n"
     . "【自動再接続機能】`n"
     . "マイクラがバニラ（無改造）でも、切断時に自動で再接続します（常時有効）。`n"
     . "※重複ログインによる切断時は、自動再接続を行いません。")

; ホットキー設定
F6::StartAFK()
F7::StopAFK()
F8::ExitApp()

; ログファイル監視の初期化（起動時に現在のサイズを記録）
if FileExist(logPath) {
    lastFileSize := FileGetSize(logPath)
}
; 1秒ごとにマイクラのログを監視するタイマーを開始
SetTimer(MonitorLog, 1000)


; ==========================================================
; AFK防止機能
; ==========================================================

; AFK防止開始
StartAFK() {
    global afkActive
    if (afkActive) {
        MsgBox("AFK防止は既に実行中です")
        return
    }
    
    afkActive := true
    MsgBox("AFK防止開始`nMinecraftにフォーカスしてください")
    SetTimer(PerformAFKAction, 2000)  ; 2秒間隔
}

; AFK防止停止
StopAFK() {
    global afkActive
    if (!afkActive) {
        MsgBox("AFK防止は既に停止中です")
        return
    }
    
    afkActive := false
    MsgBox("AFK防止停止")
    SetTimer(PerformAFKAction, 0)
}

; AFKアクション実行
PerformAFKAction() {
    global afkActive, reconnecting
    
    if (!afkActive || reconnecting) {
        return  ; 非アクティブ、または自動再接続中は実行しない
    }
    
    ; Minecraftウィンドウがアクティブかチェック
    if (!WinExist("ahk_exe javaw.exe")) {
        return  ; Minecraftが起動していない
    }
    
    ; アクティブウィンドウがMinecraftかチェック
    if (WinExist("A") != WinExist("ahk_exe javaw.exe")) {
        return  ; Minecraftがフォーカスされていない
    }
    
    ; ランダムなAFKアクションを実行（安全版：シフトキーとスペースキーのみ）
    actionType := Random(1, 2)
    
    switch actionType {
        case 1:
            ; シフトキー (スニーク)
            Send("{Shift down}")
            Sleep(150)
            Send("{Shift up}")
            
        case 2:
            ; スペースキー (ジャンプ)
            Send("{Space down}")
            Sleep(50)
            Send("{Space up}")
    }
    
    ; ツールチップで動作確認（ツールチップ番号1番を使用）
    ToolTip("AFK動作実行中", 100, 100, 1)
    SetTimer(() => ToolTip(,,,1), -1000)
}


; ==========================================================
; 自動再接続機能
; ==========================================================

; マイクラのログ監視タイマー
MonitorLog() {
    global lastFileSize, logPath, reconnecting
    
    if (reconnecting || !FileExist(logPath)) {
        return
    }
    
    currentSize := FileGetSize(logPath)
    if (currentSize == lastFileSize) {
        return
    }
    
    if (currentSize < lastFileSize) {
        lastFileSize := 0  ; ログがローテーション（再生成）された場合
    }
    
    try {
        file := FileOpen(logPath, "r", "UTF-8")
        if (file) {
            file.Seek(lastFileSize)
            newText := file.Read()
            lastFileSize := file.Length
            file.Close()
            
            ; 切断キーワードを検出
            if (RegExMatch(newText, "i)(Connection lost|Disconnected|Timed out|lost connection)")) {
                ; 「別の場所からログインした（重複ログイン）」の場合は自動再接続をスキップする
                if (RegExMatch(newText, "i)another location")) {
                    ToolTip("ℹ️ 重複ログインを検知したため、自動再接続をスキップします。", 100, 100, 2)
                    SetTimer(() => ToolTip(,,,2), -5000)
                    return
                }
                
                ; 再接続を実行
                TriggerReconnect()
            }
        }
    }
}

; 再接続キー入力処理（最大5回リトライ対応）
TriggerReconnect() {
    global reconnecting
    reconnecting := true
    
    ToolTip("⚠️ 切断を検知しました。5秒後に再接続を開始します...", 100, 100, 2)
    Sleep(5000)
    
    if (!WinExist("ahk_exe javaw.exe")) {
        ToolTip("❌ マイクラが起動していないため、再接続を中止します。", 100, 100, 2)
        SetTimer(() => ToolTip(,,,2), -3000)
        reconnecting := false
        return
    }
    
    ; マイクラを最前面にする
    WinActivate("ahk_exe javaw.exe")
    if (!WinWaitActive("ahk_exe javaw.exe", , 5)) {
        ToolTip("❌ マイクラのウィンドウをアクティブにできませんでした。", 100, 100, 2)
        SetTimer(() => ToolTip(,,,2), -3000)
        reconnecting := false
        return
    }
    
    ; ---- ステップ1: 切断画面の「サーバーリストへ戻る」を押す ----
    ; 「ログを開く」が最初にフォーカスされているため、Tabで次のボタンへ移動してからEnter
    ToolTip("👉 サーバーリストに戻ります...", 100, 100, 2)
    Send("{Tab}")   ; 「ログを開く」→「サーバーリストへ戻る」へフォーカス移動
    Sleep(300)
    Send("{Enter}") ; 「サーバーリストへ戻る」を押す
    Sleep(3000)     ; サーバーリスト画面の読み込みを待つ
    
    ; ---- ステップ2: サーバーに再接続（最大5回リトライ）----
    maxRetry := 5
    retryInterval := 15000  ; 失敗時に次の試行まで待つ時間（ミリ秒）
    
    Loop maxRetry {
        attempt := A_Index
        ToolTip("👉 接続試行 " attempt "/" maxRetry " 回目...", 100, 100, 2)
        
        ; Enterでサーバーに接続（サーバーリストで先頭のサーバーを選択）
        Send("{Enter}")
        
        ; 接続完了 or 接続失敗を待つ（最大20秒）
        Sleep(20000)
        
        ; まだマイクラが起動しているか確認
        if (!WinExist("ahk_exe javaw.exe")) {
            ToolTip("❌ マイクラが終了しました。再接続を中止します。", 100, 100, 2)
            SetTimer(() => ToolTip(,,,2), -3000)
            reconnecting := false
            return
        }
        
        ; ログファイルを確認して接続成功かチェック
        logPath := A_AppData "\.minecraft\logs\latest.log"
        if (FileExist(logPath)) {
            try {
                file := FileOpen(logPath, "r", "UTF-8")
                if (file) {
                    file.Seek(0, 2)  ; ファイル末尾へ
                    fileSize := file.Tell()
                    ; 最後の2000バイトだけ読む
                    readFrom := Max(0, fileSize - 2000)
                    file.Seek(readFrom)
                    recentText := file.Read()
                    file.Close()
                    
                    ; 「接続成功」を示すキーワードを検索
                    if (RegExMatch(recentText, "i)(Joining the game|has joined the game|logged in with entity id)")) {
                        ToolTip("✅ 接続成功！ (" attempt "回目)", 100, 100, 2)
                        SetTimer(() => ToolTip(,,,2), -5000)
                        reconnecting := false
                        return
                    }
                    
                    ; まだ失敗画面にいる場合は「サーバーリストへ戻る」→ 再試行
                    if (RegExMatch(recentText, "i)(Connection timed out|Failed to connect|ConnectException)")) {
                        if (attempt < maxRetry) {
                            ToolTip("🔄 接続失敗。" (retryInterval // 1000) "秒後に再試行します... (" attempt "/" maxRetry ")", 100, 100, 2)
                            Sleep(retryInterval)
                            ; 再度「ログを開く」→Tab→「サーバーリストへ戻る」
                            Send("{Tab}")
                            Sleep(300)
                            Send("{Enter}")
                            Sleep(3000)
                            continue
                        }
                    }
                }
            }
        }
        
        ; ログチェックで判定できなかった場合もリトライ
        if (attempt < maxRetry) {
            ToolTip("🔄 状態不明。" (retryInterval // 1000) "秒後に再試行します... (" attempt "/" maxRetry ")", 100, 100, 2)
            Sleep(retryInterval)
        }
    }
    
    ToolTip("❌ " maxRetry "回試みましたが再接続できませんでした。手動で確認してください。", 100, 100, 2)
    SetTimer(() => ToolTip(,,,2), -8000)
    reconnecting := false
}
