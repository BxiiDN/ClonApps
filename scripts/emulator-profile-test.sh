#!/usr/bin/env bash
# Pengujian emulator untuk premis backend profil kerja dan smoke test ClonApps.
#
# PENTING: profil kerja di skrip ini dibuat dengan perintah ADB khusus pengujian
# (pm create-user --managed, dpm set-profile-owner). ClonApps sendiri TIDAK memakai jalur ini;
# di perangkat nyata ClonApps memakai layar provisioning Android dengan persetujuan pengguna.
#
# Argumen: <berkas-laporan> <fixture.apks> <apk-clonapps> <bundletool.jar>
set -uo pipefail
OUT=${1:-emulator-report.txt}
FIXTURE_APKS=${2:?berkas .apks fixture}
CLONAPPS_APK=${3:?APK ClonApps}
BUNDLETOOL=${4:?bundletool.jar}
FIX=com.clonapps.fixture
PASS=0; FAIL=0
: > "$OUT"
log() { echo "$*" | tee -a "$OUT"; }
ok() { PASS=$((PASS+1)); log "PASS: $*"; }
bad() { FAIL=$((FAIL+1)); log "FAIL: $*"; }
sh_() { adb shell "$@" | tr -d '\r'; }

instance_for() { # $1 = user id
  adb logcat -d -s ClonAppsFixture 2>/dev/null | tr -d '\r' | grep "user=UserHandle{$1} " | tail -1 | sed -n 's/.*instance=\([0-9a-f-]*\).*/\1/p'
}
launch_fixture() { # $1 = user id. Proses dihentikan dulu agar onCreate menulis log baru.
  sh_ am force-stop --user "$1" $FIX >/dev/null 2>&1
  adb logcat -c
  sh_ am start --user "$1" -W -n $FIX/.FixtureActivity >/dev/null
  sleep 4
}
read_instance() { # $1 = user id; coba hingga 3 kali
  local v=""
  for _ in 1 2 3; do
    launch_fixture "$1"
    v=$(instance_for "$1")
    [ -n "$v" ] && break
    sleep 3
  done
  echo "$v"
}

log "== ClonApps emulator test =="
log "model=$(sh_ getprop ro.product.model) sdk=$(sh_ getprop ro.build.version.sdk) abi=$(sh_ getprop ro.product.cpu.abilist)"
log "feature managed_users: $(sh_ pm list features | grep -c managed_users)"

# 1) Fixture dipasang sebagai split APK yang dipilih bundletool untuk spesifikasi perangkat ini
if java -jar "$BUNDLETOOL" install-apks --apks="$FIXTURE_APKS" >>"$OUT" 2>&1; then ok "bundletool install-apks (split APK)"; else bad "bundletool install-apks"; fi
NPATH=$(sh_ pm path $FIX | grep -c package:)
[ "$NPATH" -ge 2 ] && ok "fixture terpasang dengan $NPATH file APK (base + split)" || bad "fixture tidak memiliki split ($NPATH file)"

MAIN=$(read_instance 0)
[ -n "$MAIN" ] && ok "instance profil utama: $MAIN" || bad "instance profil utama tidak terbaca"
adb logcat -d -s ClonAppsFixture | tr -d '\r' | grep -q "native=native OK" && ok "native library dimuat di profil utama" || bad "native library tidak dimuat"

# 2) Profil kerja uji (jalur ADB khusus pengujian)
PID=$(sh_ pm create-user --profileOf 0 --managed ClonAppsTest | grep -o '[0-9]*$')
if [ -z "$PID" ]; then bad "pm create-user --managed gagal"; log "SUMMARY pass=$PASS fail=$FAIL"; exit 1; fi
ok "profil kerja uji dibuat: user $PID"
sh_ am start-user "$PID" >/dev/null; sleep 8

if sh_ pm install-existing --user "$PID" $FIX | grep -qi "installed"; then ok "fixture tersedia di profil kerja (install-existing)"; else bad "install-existing fixture"; fi
PROF=$(read_instance "$PID")
[ -n "$PROF" ] && ok "instance profil kerja: $PROF" || bad "instance profil kerja tidak terbaca"
if [ -n "$MAIN" ] && [ -n "$PROF" ] && [ "$MAIN" != "$PROF" ]; then ok "data terpisah: instance berbeda antarprofil"; else bad "data TIDAK terbukti terpisah"; fi
adb logcat -d -s ClonAppsFixture | tr -d '\r' | grep -q "native=native OK" && ok "native library dimuat di profil kerja" || bad "native library di profil kerja"

# 3) Menghapus salinan di profil kerja tidak menghapus aplikasi asli/datanya
sh_ pm uninstall --user "$PID" $FIX >>"$OUT"
sleep 3
sh_ pm list packages --user "$PID" | grep -q "package:$FIX$" && bad "salinan profil masih ada" || ok "salinan profil terhapus"
AGAIN=$(read_instance 0)
present=no
for _ in 1 2 3; do
  if sh_ pm path --user 0 $FIX | grep -q "package:"; then present=yes; break; fi
  sleep 3
done
[ "$present" = yes ] && ok "aplikasi asli tetap terpasang setelah salinan profil dihapus" || bad "aplikasi asli ikut terhapus"
[ -n "$MAIN" ] && [ "$AGAIN" = "$MAIN" ] && ok "data aplikasi asli utuh (instance sama)" || bad "data aplikasi asli berubah ($AGAIN vs $MAIN)"

# 4) Smoke test ClonApps di profil utama dan di profil kerja (sebagai profile owner uji)
adb install -r "$CLONAPPS_APK" >>"$OUT" 2>&1 && ok "ClonApps terpasang" || bad "ClonApps gagal dipasang"
adb logcat -c
sh_ am start -W -n com.clonapps/.MainActivity >/dev/null; sleep 5
[ -n "$(sh_ pidof com.clonapps)" ] && ok "ClonApps berjalan di profil utama" || bad "ClonApps tidak berjalan"
sh_ pm install-existing --user "$PID" com.clonapps >/dev/null
if sh_ dpm set-profile-owner --user "$PID" com.clonapps/.admin.ClonAppsAdminReceiver | tee -a "$OUT" | grep -qi "success"; then
  ok "ClonApps menjadi profile owner profil uji"
else
  bad "dpm set-profile-owner gagal"
fi
sh_ am start --user "$PID" -W -n com.clonapps/.MainActivity >/dev/null; sleep 5
sh_ am start -W -n com.clonapps/.MainActivity >/dev/null; sleep 5
if adb logcat -d -b crash | tr -d '\r' | grep -q "com.clonapps"; then bad "crash ClonApps terdeteksi"; adb logcat -d -b crash | tail -60 >>"$OUT"; else ok "tidak ada crash ClonApps"; fi

# Bersihkan
sh_ pm remove-user "$PID" >>"$OUT"
log "SUMMARY pass=$PASS fail=$FAIL"
[ "$FAIL" -eq 0 ]
