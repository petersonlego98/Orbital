/*
 * Orbital per-user installer / uninstaller.
 *
 * Copyright (C) 2026 Orbital contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Design constraints:
 *   - The embedded manifest requests "asInvoker": no UAC prompt, ever.
 *   - Writes only under %LOCALAPPDATA%, the user's Desktop / Start Menu and
 *     HKCU\...\Uninstall. Never touches Program Files or HKLM.
 *   - The Desktop shortcut is mandatory; the Start Menu shortcut is
 *     best-effort and never aborts the installation.
 *   - The payload is a sibling "payload/" directory (an unmodified jpackage
 *     app-image). No compression, no self-extraction, no external download.
 *
 * Built with MinGW g++/windres by installer/build.sh.
 */

#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0601
#endif

#include <windows.h>
#include <shlobj.h>
#include <objbase.h>
#include <objidl.h>
#include <commctrl.h>
#include <shellapi.h>

#include <algorithm>
#include <atomic>
#include <filesystem>
#include <string>
#include <system_error>
#include <thread>
#include <vector>

namespace fs = std::filesystem;

namespace{

constexpr wchar_t kUninstallRegBase[] = L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\";

std::wstring uninstallRegPath(const std::wstring& key){
    return std::wstring(kUninstallRegBase) + key;
}
constexpr wchar_t kUninstallExe[] = L"Uninstall.exe";
constexpr wchar_t kShortcutName[] = L"Orbital.lnk";
constexpr wchar_t kGameExe[] = L"Orbital.exe";

constexpr UINT WM_APP_STATUS   = WM_APP + 1;
constexpr UINT WM_APP_PROGRESS = WM_APP + 2;
constexpr UINT WM_APP_DONE     = WM_APP + 3;
constexpr int  IDC_CANCEL      = 1001;

HANDLE g_log = INVALID_HANDLE_VALUE;

void logLine(const std::wstring& text){
    if(g_log == INVALID_HANDLE_VALUE) return;
    std::string utf8;
    int needed = WideCharToMultiByte(CP_UTF8, 0, text.c_str(), -1, nullptr, 0, nullptr, nullptr);
    if(needed > 1){
        utf8.resize(static_cast<size_t>(needed - 1));
        WideCharToMultiByte(CP_UTF8, 0, text.c_str(), -1, utf8.data(), needed, nullptr, nullptr);
    }
    utf8 += "\r\n";
    DWORD written = 0;
    WriteFile(g_log, utf8.data(), static_cast<DWORD>(utf8.size()), &written, nullptr);
}

fs::path ownExePath(){
    std::vector<wchar_t> buf(32768);
    DWORD n = GetModuleFileNameW(nullptr, buf.data(), static_cast<DWORD>(buf.size()));
    if(n == 0) return {};
    return fs::path(std::wstring(buf.data(), n));
}

std::wstring knownFolder(REFKNOWNFOLDERID id){
    PWSTR path = nullptr;
    if(FAILED(SHGetKnownFolderPath(id, KF_FLAG_CREATE, nullptr, &path)) || path == nullptr) return {};
    std::wstring result(path);
    CoTaskMemFree(path);
    return result;
}

fs::path defaultTarget(){
    std::wstring local = knownFolder(FOLDERID_LocalAppData);
    if(local.empty()){
        wchar_t buf[MAX_PATH]{};
        if(GetEnvironmentVariableW(L"LOCALAPPDATA", buf, MAX_PATH) > 0) local = buf;
    }
    if(local.empty()) return {};
    return fs::path(local) / L"Programs" / L"Orbital";
}

struct Options{
    bool uninstall = false;
    bool forceInstall = false; // --install overrides Uninstall.exe auto-detection
    bool selfDelete = false;   // CI: exercise the hidden self-delete helper in silent mode
    bool silent = false;
    bool worker = false;   // CI: run the install on a background thread, like the GUI does
    bool shortcuts = true;
    bool registry = true;
    fs::path target;
    fs::path log;
    fs::path shortcutDir;      // CI override: write .lnk files here instead of real shell folders
    std::wstring registryKey = L"Orbital"; // CI override: HKCU\...\Uninstall\<key>
};

Options parseOptions(){
    Options o;
    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if(argv == nullptr) return o;

    for(int i = 1; i < argc; ++i){
        std::wstring a = argv[i];
        if(a == L"--uninstall") o.uninstall = true;
        else if(a == L"--install") o.forceInstall = true;
        else if(a == L"--self-delete") o.selfDelete = true;
        else if(a == L"--silent") o.silent = true;
        else if(a == L"--worker") o.worker = true;
        else if(a == L"--no-shortcuts") o.shortcuts = false;
        else if(a == L"--no-register") o.registry = false;
        else if(a == L"--target" && i + 1 < argc) o.target = argv[++i];
        else if(a.rfind(L"--target=", 0) == 0) o.target = a.substr(9);
        else if(a == L"--log" && i + 1 < argc) o.log = argv[++i];
        else if(a.rfind(L"--log=", 0) == 0) o.log = a.substr(6);
        else if(a == L"--shortcut-dir" && i + 1 < argc) o.shortcutDir = argv[++i];
        else if(a.rfind(L"--shortcut-dir=", 0) == 0) o.shortcutDir = a.substr(15);
        else if(a == L"--registry-key" && i + 1 < argc) o.registryKey = argv[++i];
        else if(a.rfind(L"--registry-key=", 0) == 0) o.registryKey = a.substr(15);
    }
    LocalFree(argv);

    if(!o.target.empty()){
        std::error_code ec;
        o.target = fs::absolute(o.target, ec);
    }
    return o;
}

struct Reporter{
    std::atomic<bool> cancel{false};
    virtual ~Reporter() = default;
    virtual void status(const std::wstring&){}
    virtual void progress(size_t, size_t){}
};

struct SilentReporter final : Reporter{
    void status(const std::wstring& text) override{ logLine(text); }
};

struct GuiReporter final : Reporter{
    HWND hwnd = nullptr;

    void status(const std::wstring& text) override{
        auto* copy = new std::wstring(text);
        PostMessageW(hwnd, WM_APP_STATUS, 0, reinterpret_cast<LPARAM>(copy));
    }
    void progress(size_t done, size_t total) override{
        PostMessageW(hwnd, WM_APP_PROGRESS, static_cast<WPARAM>(done), static_cast<LPARAM>(total));
    }
};

/*
 * COM is per-thread. The GUI installer runs doInstall on a worker thread, so
 * CoCreateInstance fails with CO_E_NOTINITIALIZED unless COM is initialized on
 * that exact thread. This scope makes createShortcut safe from any thread.
 */
struct ComScope{
    bool mustUninitialize = false;

    ComScope(){
        const HRESULT hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
        mustUninitialize = (hr == S_OK || hr == S_FALSE);
    }
    ~ComScope(){
        if(mustUninitialize) CoUninitialize();
    }
    ComScope(const ComScope&) = delete;
    ComScope& operator=(const ComScope&) = delete;
};

bool createShortcut(const fs::path& lnk, const fs::path& exe, const wchar_t* description){
    ComScope com;

    IShellLinkW* link = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER,
                                  IID_IShellLinkW, reinterpret_cast<void**>(&link));
    if(FAILED(hr) || link == nullptr) return false;

    bool ok = true;
    if(FAILED(link->SetPath(exe.c_str()))) ok = false;
    if(FAILED(link->SetWorkingDirectory(exe.parent_path().c_str()))) ok = false;
    if(FAILED(link->SetDescription(description))) ok = false;
    link->SetIconLocation(exe.c_str(), 0);

    IPersistFile* persist = nullptr;
    if(ok && SUCCEEDED(link->QueryInterface(IID_IPersistFile, reinterpret_cast<void**>(&persist)))){
        std::error_code ec;
        fs::create_directories(lnk.parent_path(), ec);
        if(FAILED(persist->Save(lnk.c_str(), TRUE))) ok = false;
        persist->Release();
    }else{
        ok = false;
    }
    link->Release();
    return ok;
}

bool writeUninstallKey(const fs::path& target, const std::wstring& registryKey){
    HKEY key = nullptr;
    if(RegCreateKeyExW(HKEY_CURRENT_USER, uninstallRegPath(registryKey).c_str(), 0, nullptr, 0,
                       KEY_WRITE, nullptr, &key, nullptr) != ERROR_SUCCESS) return false;

    auto setString = [&](const wchar_t* name, const std::wstring& value){
        return RegSetValueExW(key, name, 0, REG_SZ,
                              reinterpret_cast<const BYTE*>(value.c_str()),
                              static_cast<DWORD>((value.size() + 1) * sizeof(wchar_t))) == ERROR_SUCCESS;
    };
    auto setDword = [&](const wchar_t* name, DWORD value){
        return RegSetValueExW(key, name, 0, REG_DWORD,
                              reinterpret_cast<const BYTE*>(&value), sizeof(value)) == ERROR_SUCCESS;
    };

    bool ok = true;
    ok &= setString(L"DisplayName", L"Orbital");
    ok &= setString(L"DisplayVersion", L"1.0");
    ok &= setString(L"Publisher", L"Orbital contributors");
    ok &= setString(L"InstallLocation", target.wstring());
    ok &= setString(L"DisplayIcon", (target / kGameExe).wstring());
    ok &= setString(L"HelpLink", L"https://github.com/petersonlego98/Orbital");
    const std::wstring command = L"\"" + (target / kUninstallExe).wstring() + L"\" --uninstall";
    ok &= setString(L"UninstallString", command);
    ok &= setDword(L"NoModify", 1);
    ok &= setDword(L"NoRepair", 1);

    RegCloseKey(key);
    return ok;
}

void removeShortcuts(const Options& o){
    std::error_code ec;
    if(!o.shortcutDir.empty()){
        fs::remove(o.shortcutDir / L"Orbital-StartMenu.lnk", ec);
        fs::remove(o.shortcutDir / L"Orbital-Desktop.lnk", ec);
        return;
    }
    std::wstring programs = knownFolder(FOLDERID_Programs);
    std::wstring desktop = knownFolder(FOLDERID_Desktop);
    if(!programs.empty()) fs::remove(fs::path(programs) / kShortcutName, ec);
    if(!desktop.empty()) fs::remove(fs::path(desktop) / kShortcutName, ec);
}

void clearReadOnly(const fs::path& file){
    const DWORD attributes = GetFileAttributesW(file.c_str());
    if(attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_READONLY) != 0){
        SetFileAttributesW(file.c_str(), attributes & ~static_cast<DWORD>(FILE_ATTRIBUTE_READONLY));
    }
}

bool removeFileWithRetry(const fs::path& file){
    for(int attempt = 0; attempt < 5; ++attempt){
        clearReadOnly(file);
        std::error_code ec;
        fs::remove(file, ec);
        if(!ec || !fs::exists(file)) return true;
        Sleep(100);
    }
    return !fs::exists(file);
}

/*
 * jpackage marks Orbital.exe read-only, and std::filesystem::remove_all fails
 * on such files. Delete files individually (clearing the attribute, retrying
 * briefly for files held by another process), then remove directories from the
 * deepest level up. Returns true only when the tree is actually gone.
 */
bool removeTree(const fs::path& root, Reporter& reporter){
    std::error_code ec;
    if(!fs::exists(root, ec)) return true;

    std::vector<fs::path> files;
    std::vector<fs::path> directories;
    for(fs::recursive_directory_iterator it(root, fs::directory_options::skip_permission_denied, ec), end;
        !ec && it != end; it.increment(ec)){
        if(it->is_regular_file(ec)) files.push_back(it->path());
        else if(it->is_directory(ec)) directories.push_back(it->path());
    }

    bool ok = true;
    for(const auto& file : files){
        if(!removeFileWithRetry(file)){
            reporter.status(L"Aviso: nao foi possivel remover " + file.wstring());
            ok = false;
        }
    }
    std::sort(directories.begin(), directories.end(),
              [](const fs::path& a, const fs::path& b){
                  return a.wstring().size() > b.wstring().size();
              });
    for(const auto& directory : directories){
        clearReadOnly(directory);
        std::error_code ignored;
        fs::remove(directory, ignored);
    }
    // Windows directories can inherit FILE_ATTRIBUTE_READONLY; std::filesystem
    // then refuses to remove even an empty one.
    clearReadOnly(root);
    std::error_code ignored;
    fs::remove(root, ignored);

    return ok && !fs::exists(root, ec);
}

/*
 * The uninstaller cannot delete its own running image. It writes a tiny batch
 * file in %TEMP% that waits for this process to exit, removes the exe and the
 * now-empty install directory, and finally deletes itself. The helper runs
 * hidden (CREATE_NO_WINDOW), so no console window is shown.
 */
void scheduleSelfDelete(const fs::path& target){
    const fs::path self = ownExePath();
    if(self.empty()) return;

    wchar_t tempDir[MAX_PATH]{};
    if(GetTempPathW(MAX_PATH, tempDir) == 0) return;

    const fs::path batch = fs::path(tempDir) /
        (L"orbital-uninstall-" + std::to_wstring(GetCurrentProcessId()) + L".cmd");

    std::wstring script = L"@echo off\r\n"
                          L"cd /d \"%~dp0\"\r\n"
                          L"chcp 65001 >nul\r\n"
                          L"ping -n 2 127.0.0.1 >nul\r\n"
                          L"del /f /q \"" + self.wstring() + L"\"\r\n"
                          L"rmdir /s /q \"" + target.wstring() + L"\" 2>nul\r\n"
                          L"del /f /q \"%~f0\"\r\n";

    int needed = WideCharToMultiByte(CP_UTF8, 0, script.c_str(), -1, nullptr, 0, nullptr, nullptr);
    std::string bytes;
    if(needed > 1){
        bytes.resize(static_cast<size_t>(needed - 1));
        WideCharToMultiByte(CP_UTF8, 0, script.c_str(), -1, bytes.data(), needed, nullptr, nullptr);
    }

    HANDLE file = CreateFileW(batch.c_str(), GENERIC_WRITE, 0, nullptr,
                              CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if(file == INVALID_HANDLE_VALUE) return;
    DWORD written = 0;
    WriteFile(file, bytes.data(), static_cast<DWORD>(bytes.size()), &written, nullptr);
    CloseHandle(file);

    std::wstring commandLine = L"cmd.exe /c \"" + batch.wstring() + L"\"";
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi{};
    // Start the helper from %TEMP%. If it inherits the install directory as
    // its working directory, Windows keeps that directory open and `rmdir`
    // cannot remove it even after all files are gone.
    if(CreateProcessW(nullptr, commandLine.data(), nullptr, nullptr, FALSE,
                      CREATE_NO_WINDOW, nullptr, tempDir, &si, &pi)){
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    }
}

int doInstall(const Options& o, const fs::path& payload, Reporter& reporter){
    const fs::path target = o.target.empty() ? defaultTarget() : o.target;
    if(target.empty()) return 10;

    std::error_code ec;
    if(!fs::exists(payload / kGameExe, ec)){
        reporter.status(L"ERRO: payload/Orbital.exe nao encontrado ao lado do instalador.");
        return 11;
    }

    if(fs::exists(target, ec)){
        reporter.status(L"Removendo instalacao anterior...");
        if(!removeTree(target, reporter)) return 12;
    }
    fs::create_directories(target, ec);
    if(ec) return 13;

    size_t total = 0;
    for(fs::recursive_directory_iterator it(payload, fs::directory_options::skip_permission_denied, ec), end;
        !ec && it != end; it.increment(ec)){
        if(it->is_regular_file(ec)) ++total;
    }

    reporter.status(L"Copiando arquivos...");
    const std::wstring payloadRoot = payload.wstring();
    size_t done = 0;
    for(fs::recursive_directory_iterator it(payload, fs::directory_options::skip_permission_denied, ec), end;
        !ec && it != end; it.increment(ec)){
        if(reporter.cancel.load()) return 14;

        const fs::path& source = it->path();
        const std::wstring sourceText = source.wstring();
        const fs::path relative = sourceText.size() > payloadRoot.size() + 1
            ? fs::path(sourceText.substr(payloadRoot.size() + 1)) : fs::path();
        if(relative.empty()) continue;

        const fs::path destination = target / relative;
        const bool isDirectory = it->is_directory(ec);
        const bool isFile = !isDirectory && it->is_regular_file(ec);
        if(isDirectory){
            fs::create_directories(destination, ec);
        }else if(isFile){
            fs::create_directories(destination.parent_path(), ec);
            fs::copy_file(source, destination, fs::copy_options::overwrite_existing, ec);
            if(ec){
                reporter.status(L"ERRO ao copiar: " + source.wstring());
                return 15;
            }
            ++done;
            if(done % 5 == 0 || done == total) reporter.progress(done, total);
        }
    }

    const fs::path self = ownExePath();
    fs::copy_file(self, target / kUninstallExe, fs::copy_options::overwrite_existing, ec);
    if(ec) return 16;

    if(o.shortcuts){
        reporter.status(L"Criando atalhos...");
        const auto warnStartMenu = [&](){
            reporter.status(L"Aviso: nao foi possivel criar o atalho no Menu Iniciar; "
                            L"a instalacao continua com o atalho da Area de Trabalho.");
            logLine(L"WARNING=start-menu-shortcut");
        };

        if(!o.shortcutDir.empty()){
            fs::create_directories(o.shortcutDir, ec);

            // Desktop is the preferred, mandatory shortcut.
            if(!createShortcut(o.shortcutDir / L"Orbital-Desktop.lnk",
                               target / kGameExe, L"Orbital")) return 18;

            // Start Menu is best-effort only: never abort the installation.
            if(!createShortcut(o.shortcutDir / L"Orbital-StartMenu.lnk",
                               target / kGameExe, L"Orbital")) warnStartMenu();
        }else{
            const std::wstring programs = knownFolder(FOLDERID_Programs);
            const std::wstring desktop = knownFolder(FOLDERID_Desktop);

            // Desktop first: it is the shortcut the user actually wants.
            if(desktop.empty() || !createShortcut(fs::path(desktop) / kShortcutName,
                                                  target / kGameExe, L"Orbital")) return 18;

            // Start Menu is best-effort only: never abort the installation.
            if(!programs.empty() && !createShortcut(fs::path(programs) / kShortcutName,
                                                    target / kGameExe, L"Orbital")) warnStartMenu();
        }
    }

    if(o.registry){
        reporter.status(L"Registrando desinstalador (HKCU)...");
        if(!writeUninstallKey(target, o.registryKey)) return 19;
    }

    reporter.status(L"Arquivos copiados: " + std::to_wstring(done));
    reporter.status(L"Destino: " + target.wstring());
    return 0;
}

int doUninstall(const Options& o, Reporter& reporter){
    const fs::path target = o.target.empty() ? defaultTarget() : o.target;
    std::error_code ec;
    if(target.empty() || !fs::exists(target, ec)){
        reporter.status(L"Nada a remover.");
        return 0;
    }

    if(o.shortcuts){
        reporter.status(L"Removendo atalhos...");
        removeShortcuts(o);
    }
    if(o.registry){
        reporter.status(L"Removendo registro HKCU...");
        RegDeleteTreeW(HKEY_CURRENT_USER, uninstallRegPath(o.registryKey).c_str());
    }

    reporter.status(L"Removendo arquivos...");
    std::vector<fs::path> entries;
    for(fs::directory_iterator it(target, ec), end; !ec && it != end; it.increment(ec)){
        entries.push_back(it->path());
    }
    bool removed = true;
    for(const auto& entry : entries){
        if(entry.filename() == kUninstallExe) continue;
        std::error_code ignored;
        if(fs::is_regular_file(entry, ignored)){
            removed &= removeFileWithRetry(entry);
        }else{
            removed &= removeTree(entry, reporter);
        }
    }
    if(!removed) return 21;

    if(o.silent){
        if(o.selfDelete){
            // CI: exercise the exact hidden helper used by interactive uninstall.
            scheduleSelfDelete(target);
            reporter.status(L"Auto-remocao agendada em segundo plano.");
        }else{
            // CI mode is deterministic: keep the small Uninstall.exe so the test
            // target can be inspected.
            reporter.status(L"Modo silencioso: Uninstall.exe mantido para validacao.");
        }
        return 0;
    }

    scheduleSelfDelete(target);
    return 0;
}

HWND g_statusText = nullptr;
HWND g_progressBar = nullptr;
GuiReporter* g_guiReporter = nullptr;

LRESULT CALLBACK wndProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam){
    switch(message){
        case WM_APP_STATUS:{
            auto* text = reinterpret_cast<std::wstring*>(lParam);
            if(text != nullptr){
                SetWindowTextW(g_statusText, text->c_str());
                delete text;
            }
            return 0;
        }
        case WM_APP_PROGRESS:{
            SendMessageW(g_progressBar, PBM_SETRANGE32, 0, static_cast<LPARAM>(lParam));
            SendMessageW(g_progressBar, PBM_SETPOS, static_cast<WPARAM>(wParam), 0);
            return 0;
        }
        case WM_APP_DONE:
            PostQuitMessage(static_cast<int>(wParam));
            return 0;
        case WM_COMMAND:
            if(LOWORD(wParam) == IDC_CANCEL && g_guiReporter != nullptr){
                g_guiReporter->cancel.store(true);
            }
            return 0;
        case WM_DESTROY:
            if(g_guiReporter != nullptr) g_guiReporter->cancel.store(true);
            return 0;
        default:
            return DefWindowProcW(hwnd, message, wParam, lParam);
    }
}

HWND createMainWindow(HINSTANCE instance){
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = wndProc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_BTNFACE + 1);
    wc.lpszClassName = L"OrbitalSetupWindow";
    RegisterClassExW(&wc);

    HWND hwnd = CreateWindowExW(0, L"OrbitalSetupWindow", L"Instalador do Orbital",
                                WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                                CW_USEDEFAULT, CW_USEDEFAULT, 500, 190,
                                nullptr, nullptr, instance, nullptr);
    if(hwnd == nullptr) return nullptr;

    HFONT font = reinterpret_cast<HFONT>(GetStockObject(DEFAULT_GUI_FONT));
    auto label = [&](const wchar_t* text, int y){
        HWND control = CreateWindowExW(0, L"STATIC", text, WS_CHILD | WS_VISIBLE,
                                       16, y, 452, 22, hwnd, nullptr, instance, nullptr);
        SendMessageW(control, WM_SETFONT, reinterpret_cast<WPARAM>(font), TRUE);
        return control;
    };
    label(L"Instalando o Orbital...", 16);
    g_statusText = label(L"Preparando...", 42);
    g_progressBar = CreateWindowExW(0, PROGRESS_CLASSW, nullptr,
                                    WS_CHILD | WS_VISIBLE | PBS_SMOOTH,
                                    16, 72, 452, 22, hwnd, nullptr, instance, nullptr);
    HWND cancel = CreateWindowExW(0, L"BUTTON", L"Cancelar",
                                  WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                  378, 110, 90, 28, hwnd,
                                  reinterpret_cast<HMENU>(IDC_CANCEL), instance, nullptr);
    SendMessageW(cancel, WM_SETFONT, reinterpret_cast<WPARAM>(font), TRUE);
    return hwnd;
}

std::wstring errorMessage(int code){
    switch(code){
        case 10: return L"Nao foi possivel determinar a pasta LOCALAPPDATA.";
        case 11: return L"A pasta 'payload' com Orbital.exe nao esta ao lado do instalador.";
        case 12: return L"Falha ao remover a instalacao anterior.";
        case 13: return L"Falha ao criar a pasta de destino.";
        case 14: return L"Instalacao cancelada.";
        case 15: return L"Falha ao copiar um arquivo.";
        case 16: return L"Falha ao copiar o desinstalador.";
        case 17: return L"Falha ao criar um atalho adicional.";
        case 18: return L"Falha ao criar o atalho obrigatorio na Area de Trabalho.";
        case 19: return L"Falha ao registrar o desinstalador em HKCU.";
        case 20: return L"Falha ao criar a janela do instalador.";
        case 21: return L"Alguns arquivos nao puderam ser removidos.";
        default: return L"Erro desconhecido.";
    }
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int){
    // COM is initialized per-thread inside createShortcut (ComScope), because
    // the GUI install runs on a worker thread. Do not initialize it only here.
    InitCommonControls();

    Options options = parseOptions();

    // A copy named Uninstall.exe must uninstall when double-clicked. It has no
    // sibling payload/, so treating it as an installer produced error 11 and
    // removed nothing. --install explicitly restores the installer behaviour.
    const fs::path self = ownExePath();
    if(!options.forceInstall && !options.uninstall && !self.empty() &&
       lstrcmpiW(self.filename().c_str(), kUninstallExe) == 0){
        options.uninstall = true;
    }

    if(!options.log.empty()){
        g_log = CreateFileW(options.log.c_str(), FILE_APPEND_DATA, FILE_SHARE_READ,
                            nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    }

    int result = 0;
    const fs::path payload = ownExePath().parent_path() / L"payload";
    const fs::path target = options.target.empty() ? defaultTarget() : options.target;

    if(options.silent){
        SilentReporter reporter;
        auto run = [&](){
            return options.uninstall ? doUninstall(options, reporter)
                                    : doInstall(options, payload, reporter);
        };
        if(options.worker){
            // Reproduces the GUI threading model in CI: COM must work here too.
            std::thread background([&](){ result = run(); });
            background.join();
        }else{
            result = run();
        }
        logLine(L"MODE=" + std::wstring(options.uninstall ? L"uninstall" : L"install"));
        logLine(L"RESULT=" + std::to_wstring(result));
        logLine(result == 0 ? L"STATUS=ok" : L"STATUS=failed");
    }else if(options.uninstall){
        const std::wstring prompt = L"Remover o Orbital de:\n" + target.wstring() +
                                    L"\n\nOs arquivos do jogo, os atalhos e o registro de desinstalacao serao removidos.";
        if(MessageBoxW(nullptr, prompt.c_str(), L"Desinstalar o Orbital",
                       MB_ICONQUESTION | MB_OKCANCEL) == IDOK){
            SilentReporter reporter;
            result = doUninstall(options, reporter);
            MessageBoxW(nullptr, result == 0 ? L"Orbital removido." : errorMessage(result).c_str(),
                        L"Desinstalar o Orbital",
                        result == 0 ? MB_ICONINFORMATION : MB_ICONERROR);
        }else{
            result = 1;
        }
    }else{
        const std::wstring prompt = L"Instalar o Orbital em:\n" + target.wstring() +
                                    L"\n\nNao sera necessaria permissao de administrador.";
        if(MessageBoxW(nullptr, prompt.c_str(), L"Instalador do Orbital",
                       MB_ICONINFORMATION | MB_OKCANCEL) != IDOK){
            result = 1;
        }else{
            HWND window = createMainWindow(instance);
            if(window == nullptr){
                result = 20;
            }else{
                GuiReporter reporter;
                reporter.hwnd = window;
                g_guiReporter = &reporter;
                ShowWindow(window, SW_SHOW);
                UpdateWindow(window);

                std::thread worker([&](){
                    const int code = doInstall(options, payload, reporter);
                    PostMessageW(window, WM_APP_DONE, static_cast<WPARAM>(code), 0);
                });

                MSG msg{};
                while(GetMessageW(&msg, nullptr, 0, 0) > 0){
                    TranslateMessage(&msg);
                    DispatchMessageW(&msg);
                }
                worker.join();
                result = static_cast<int>(msg.wParam);
                g_guiReporter = nullptr;

                if(result == 0){
                    const std::wstring done = L"Orbital instalado em:\n" + target.wstring() +
                                              L"\n\nAtalhos criados no Menu Iniciar e na Area de Trabalho.";
                    MessageBoxW(nullptr, done.c_str(), L"Instalacao concluida", MB_ICONINFORMATION);
                }else{
                    MessageBoxW(nullptr, errorMessage(result).c_str(),
                                L"Falha na instalacao", MB_ICONERROR);
                }
            }
        }
    }

    if(g_log != INVALID_HANDLE_VALUE) CloseHandle(g_log);
    return result;
}
