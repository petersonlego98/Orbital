; NSIS wrapper for the Orbital per-user (no-admin) installer.
; It bundles the existing OrbitalSetup.exe together with its payload/ app-image,
; extracts both to a temporary directory and runs the setup executable, which
; performs the actual per-user install (shortcuts, uninstall key, progress UI).
;
; Build:
;   makensis /DSRC_DIR=... /DOUTPUT_FILE=... installer\orbital.nsi

!define APP_NAME "Orbital"
!define APP_VERSION "1.2.0"

Name "${APP_NAME} ${APP_VERSION}"
OutFile "${OUTPUT_FILE}"
InstallDir "$TEMP\orbital-bootstrap-${APP_VERSION}"
RequestExecutionLevel user
SilentInstall silent
ShowInstDetails hide
SetCompressor /SOLID lzma

!include "FileFunc.nsh"

Section "Bootstrap"
  ; Clean any stale bootstrap from a previous aborted run.
  RMDir /r "$INSTDIR"
  CreateDirectory "$INSTDIR"

  ; Stage the real installer and its payload side by side (OrbitalSetup.exe
  ; looks for a sibling payload/ directory).
  SetOutPath "$INSTDIR"
  File "${SRC_DIR}\OrbitalSetup.exe"
  File /r "${SRC_DIR}\payload"

  ; Forward any command-line switches (e.g. --silent --no-shortcuts --target)
  ; to the real installer so CI/upgrade flows keep working.
  ${GetParameters} $0
  ClearErrors
  ExecWait '"$INSTDIR\OrbitalSetup.exe" $0' $1
  IfErrors 0 +3
    SetErrorLevel 22
    Goto done
  SetErrorLevel $1

  done:
  ; Do not delete the bootstrap while the just-launched installer might still
  ; be referencing files from it on some Windows versions; schedule via a
  ; tiny helper batch instead.
  RMDir /r "$INSTDIR"
SectionEnd
