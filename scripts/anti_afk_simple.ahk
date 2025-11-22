#SingleInstance Force

; シンプルなAFK防止スクリプト
; Minecraftフォーカス時のみ動作

; グローバル変数
global afkActive := false

; ホットキー設定
F6::StartAFK()
F7::StopAFK()
F8::ExitApp()

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
    global afkActive
    
    if (!afkActive) {
        return
    }
    
    ; Minecraftウィンドウがアクティブかチェック
    if (!WinExist("ahk_exe javaw.exe")) {
        return  ; Minecraftが起動していない
    }
    
    ; アクティブウィンドウがMinecraftかチェック
    if (WinExist("A") != WinExist("ahk_exe javaw.exe")) {
        return  ; Minecraftがフォーカスされていない
    }
    
    ; ランダムなAFKアクションを実行
    actionType := Random(1, 4)
    
    switch actionType {
        case 1:
            ; マウス移動 (視点がずれるため無効化)
            ; x := Random(100, 800)
            ; y := Random(100, 600)
            ; MouseMove(x, y, 10)
            
        case 2:
            ; 左クリック
            Click
            
        case 3:
            ; キー入力 (WASD)
            key := Random(1, 4)
            keys := ["w", "a", "s", "d"]
            Send("{" keys[key] " down}")
            Sleep(50)
            Send("{" keys[key] " up}")
            
        case 4:
            ; スペースキー
            Send("{Space down}")
            Sleep(50)
            Send("{Space up}")
    }
    
    ; ツールチップで動作確認
    ToolTip("AFK動作実行中", 100, 100, 1)
    SetTimer(() => ToolTip(), -1000)
}

; 初期化メッセージ
MsgBox("AFK防止スクリプト開始`n`nF6: AFK防止開始`nF7: AFK防止停止`nF8: 終了`n`nMinecraftにフォーカスしてからF6を押してください")
