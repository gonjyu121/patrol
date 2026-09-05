#Requires AutoHotkey v2.0
#SingleInstance Force

; v4: bounded recovery only. No synthetic player activity or Falix power actions.
; Runtime settings/images/logs stay on the streaming PC, not on the shared drive.
global dataDir := A_AppData "\PatrolReconnectV4"
global gameDir := A_AppData "\.minecraft"
global armed := false, busy := false, used := 0, epoch := 0, targetHwnd := 0
global offset := 0, pending := "", seen := Map(), deadline := 0, phase := "idle"

if (A_Args.Length && A_Args[1] = "--self-test") {
    SelfTest()
    ExitApp(0)
}
DirCreate(dataDir)
global settings := dataDir "\settings.ini"
gameDir := IniRead(settings, "client", "gameDir", gameDir)
CoordMode("Pixel", "Screen")
CoordMode("Mouse", "Screen")
SetTimer(Tick, 1000)
MsgBox("再接続 v4（AutoHotkey v2）`n旧AHKは終了してください。`n`nF5: ゲームフォルダ選択`nF9: 戻るボタンの画像登録`nF10: 接続先サーバー名の画像登録`nF6: Minecraftを前面にして監視開始`nF7: 全停止（手動切断の前にも押す）`nF8: 終了`n`n初回設定は同梱READMEを参照。定期キー送信・Falixの起動は行いません。")

F5::ChooseFolder()
F6::Arm()
F7::Halt("手動停止")
F8::ExitApp()
F9::CaptureTemplate("back")
F10::CaptureTemplate("server")

IsFailure(text) {
    return !!RegExMatch(text, "i)(Connection reset|Connection timed out|Read timed out|Timed out|Connection lost|lost connection|SocketException|Connection closed|End of stream)")
}

IsExcluded(text) {
    return !!RegExMatch(text, "i)(another location|duplicate.?login|logged in from|banned|kicked|multiplayer\.disconnect\.(duplicate_login|kicked)|別の場所|重複ログイン|追放)")
}

Tell(message) {
    global dataDir
    try FileAppend(FormatTime(, "yyyy-MM-dd HH:mm:ss") " " message "`n", dataDir "\reconnect.log", "UTF-8")
    TrayTip(message, "Patrol Reconnect v4")
}

Halt(reason) {
    global armed, phase, epoch
    armed := false
    phase := "idle"
    epoch += 1
    Tell(reason)
}

ChooseFolder() {
    global gameDir, settings
    Halt("設定変更のため停止")
    selected := DirSelect(, 0, "logs\latest.log があるMinecraftのゲームフォルダ")
    if !selected
        return
    if !FileExist(selected "\logs\latest.log") {
        MsgBox("logs\latest.log がありません。起動構成のゲームディレクトリを選んでください。")
        return
    }
    gameDir := selected
    IniWrite(gameDir, settings, "client", "gameDir")
    Tell("ゲームフォルダを保存しました")
}

MinecraftActive() {
    hwnd := WinExist("A")
    try {
        exe := WinGetProcessName("ahk_id " hwnd)
        return (RegExMatch(exe, "i)^javaw?\.exe$") && InStr(WinGetTitle("ahk_id " hwnd), "Minecraft")) ? hwnd : 0
    }
    return 0
}

Arm() {
    global gameDir, dataDir, armed, targetHwnd, offset, pending, seen, used, phase, epoch
    Halt("監視を初期化")
    targetHwnd := MinecraftActive()
    if !targetHwnd {
        Tell("Minecraftを前面にしてF6を押してください")
        return
    }
    if !FileExist(gameDir "\logs\latest.log") {
        Tell("ログがありません。F5で実際のゲームフォルダを選択してください")
        return
    }
    if (!FileExist(dataDir "\back.bmp") || !FileExist(dataDir "\server.bmp")) {
        Tell("先にF9/F10で画像を登録してください")
        return
    }
    offset := FileGetSize(gameDir "\logs\latest.log")
    pending := ""
    seen := Map()
    Loop Files gameDir "\debug\disconnect-*-client.txt" {
        seen[A_LoopFileFullPath] := A_LoopFileTimeModified ":" A_LoopFileSize
    }
    used := 0
    phase := "idle"
    armed := true
    Tell("監視開始。今回の監視で最大3回。手動切断の前はF7")
}

ReadNew() {
    global gameDir, offset, pending
    f := FileOpen(gameDir "\logs\latest.log", "r", "UTF-8")
    try {
        if f.Length < offset {
            Halt("ログが再生成されました。接続後にF6で再開してください")
            offset := f.Length
            pending := ""
            return ""
        }
        f.Pos := offset
        text := pending f.Read()
        offset := f.Pos
        last := InStr(text, "`n", , -1)
        if !last {
            pending := text
            return ""
        }
        pending := SubStr(text, last + 1)
        return SubStr(text, 1, last)
    } finally {
        f.Close()
    }
}

Tick() {
    global armed, busy, seen, gameDir, phase, deadline, used, targetHwnd, epoch
    if (!armed || busy)
        return
    busy := true
    generation := epoch
    try {
        if !WinExist("ahk_id " targetHwnd) {
            Halt("対象Minecraftが終了しました")
            return
        }
        text := ReadNew()
        ; These reports are written by Minecraft under the game's debug directory.
        Loop Files gameDir "\debug\disconnect-*-client.txt" {
            signature := A_LoopFileTimeModified ":" A_LoopFileSize
            if (!seen.Has(A_LoopFileFullPath) || seen[A_LoopFileFullPath] != signature) {
                text .= "`n" FileRead(A_LoopFileFullPath, "UTF-8")
                seen[A_LoopFileFullPath] := signature
            }
        }
        if (!armed || generation != epoch)
            return
        if IsExcluded(text) {
            Halt("重複ログイン・キック等を検出。自動操作を停止")
            return
        }
        if phase = "idle" {
            if !IsFailure(text)
                return
            if used >= 3 {
                Halt("監視中の試行上限3回に達しました。手動確認してください")
                return
            }
            phase := "back"
            deadline := A_TickCount + 10000
            Tell("通信エラー検知。10秒後に切断画面を確認します（F7で取消）")
            return
        }
        if A_TickCount < deadline
            return
        ; Never steal focus or send keys to another app, window, or unknown screen.
        if MinecraftActive() != targetHwnd {
            Halt("対象Minecraftが前面ではないため停止。F6で再開できます")
            return
        }
        if phase = "back" {
            if !ClickTemplate("back", 1, generation) {
                Halt("戻るボタンを確認できないため操作中止。画面・画像設定を確認してください")
                return
            }
            if (!armed || generation != epoch)
                return
            phase := "server"
            deadline := A_TickCount + 4000
        } else if phase = "server" {
            if !ClickTemplate("server", 2, generation) {
                Halt("登録したサーバー名を確認できないため操作中止")
                return
            }
            used += 1
            phase := "settle"
            deadline := A_TickCount + 30000
            Tell("登録サーバーへ接続操作を実行（" used "/3）。成功は未確認")
        } else if phase = "settle" {
            ; Do not infer successful login from stale log lines or blindly retry.
            if (FindTemplate("back", &x, &y) || FindTemplate("server", &x, &y)) {
                Halt("30秒後もメニュー画面です。Falixの状態を手動確認してください")
                return
            }
            phase := "idle"
            Tell("追加のキー入力をせず監視へ戻ります。接続成功は自動判定していません")
        }
    } catch as e {
        Halt("監視エラー: " e.Message)
    } finally {
        busy := false
    }
}

FindTemplate(name, &x, &y) {
    global targetHwnd, dataDir
    WinGetClientPos(&left, &top, &width, &height, "ahk_id " targetHwnd)
    return ImageSearch(&x, &y, left, top, left + width - 1, top + height - 1, "*15 " dataDir "\" name ".bmp")
}

ClickTemplate(name, count, generation) {
    global armed, epoch, targetHwnd
    if !FindTemplate(name, &x, &y)
        return false
    ; F7 may interrupt the image search: re-check immediately before input.
    Critical("On")
    try {
        if (!armed || epoch != generation || MinecraftActive() != targetHwnd)
            return false
        Click(x + 120, y + 20, count)
        return true
    } finally {
        Critical("Off")
    }
}

CaptureTemplate(name) {
    global dataDir
    Halt("画像登録のため監視停止")
    hwnd := MinecraftActive()
    if !hwnd {
        Tell("Minecraftを前面にしてください")
        return
    }
    MouseGetPos(&x, &y)
    WinGetClientPos(&left, &top, &width, &height, "ahk_id " hwnd)
    if (x - 120 < left || y - 20 < top || x + 120 > left + width || y + 20 > top + height) {
        Tell("マウス周囲240x40がゲーム画面内に収まる位置にしてください")
        return
    }
    try {
        ; Store the non-hovered appearance so later matching is consistent.
        MouseMove(left + 2, top + 2, 0)
        Sleep(250)
        if MinecraftActive() != hwnd
            throw Error("画像登録中に対象ウィンドウが変わりました")
        SaveRegion(x - 120, y - 20, 240, 40, dataDir "\" name ".bmp")
        Tell(name " の画像を登録しました。F6で監視開始")
    } catch as e {
        Tell("画像保存失敗: " e.Message)
    }
}

SaveRegion(x, y, w, h, path) {
    screen := DllCall("GetDC", "ptr", 0, "ptr")
    dc := DllCall("gdi32\CreateCompatibleDC", "ptr", screen, "ptr")
    bitmap := DllCall("gdi32\CreateCompatibleBitmap", "ptr", screen, "int", w, "int", h, "ptr")
    old := DllCall("gdi32\SelectObject", "ptr", dc, "ptr", bitmap, "ptr")
    try {
        if !DllCall("gdi32\BitBlt", "ptr", dc, "int", 0, "int", 0, "int", w, "int", h, "ptr", screen, "int", x, "int", y, "uint", 0x00CC0020)
            throw Error("画面取得失敗")
        DllCall("gdi32\SelectObject", "ptr", dc, "ptr", old)
        info := Buffer(40, 0)
        NumPut("uint", 40, "int", w, "int", -h, "ushort", 1, "ushort", 32, info)
        pixels := Buffer(w * h * 4)
        if !DllCall("gdi32\GetDIBits", "ptr", screen, "ptr", bitmap, "uint", 0, "uint", h, "ptr", pixels, "ptr", info, "uint", 0)
            throw Error("画像変換失敗")
        header := Buffer(14, 0)
        NumPut("ushort", 0x4D42, "uint", 54 + pixels.Size, header)
        NumPut("uint", 54, header, 10)
        f := FileOpen(path, "w")
        try {
            f.RawWrite(header)
            f.RawWrite(info)
            f.RawWrite(pixels)
        } finally {
            f.Close()
        }
    } finally {
        DllCall("gdi32\SelectObject", "ptr", dc, "ptr", old)
        DllCall("gdi32\DeleteObject", "ptr", bitmap)
        DllCall("gdi32\DeleteDC", "ptr", dc)
        DllCall("ReleaseDC", "ptr", 0, "ptr", screen)
    }
}

SelfTest() {
    for text in ["java.net.SocketException: Connection reset", "Timed out", "Connection lost", "Read timed out"] {
        if !IsFailure(text)
            throw Error("Failure detection: " text)
    }
    for text in ["Disconnected", "Joining the game", "normal chat"] {
        if IsFailure(text)
            throw Error("False positive: " text)
    }
    for text in ["logged in from another location", "multiplayer.disconnect.duplicate_login", "Kicked by operator"] {
        if !IsExcluded(text)
            throw Error("Exclusion: " text)
    }
    if IsExcluded("Connection reset")
        throw Error("Incorrect exclusion")
    FileAppend("PASS: 11 detection/exclusion tests; no GUI, input, or server actions.`n", "*")
}
