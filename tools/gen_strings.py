# Membangkitkan values/strings.xml (Indonesia, utama) dan values-en/strings.xml (Inggris)
# dari satu tabel, lalu memastikan setiap nilai enum punya teks di kedua bahasa.
import re, os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = {}  # key -> (id, en)
def s(k, i, e): S[k] = (i, e)

s('app_name','ClonApps','ClonApps')
s('admin_description','Mengelola profil kerja yang dibuat ClonApps untuk menjalankan salinan aplikasi dengan data terpisah.','Manages the work profile created by ClonApps to run app copies with separate data.')
# navigasi
s('nav_home','Beranda','Home'); s('nav_pick','Pilih Aplikasi','Pick Apps'); s('nav_activity','Aktivitas','Activity')
s('nav_diagnostics','Diagnostik','Diagnostics'); s('nav_settings','Pengaturan','Settings')
s('detail_title','Detail Clone','Clone Details')
# umum
s('yes','Ya','Yes'); s('no','Tidak','No'); s('loading','Memuat…','Loading…'); s('error_title','Terjadi kesalahan','Something went wrong')
s('favorite','Favorit','Favorite')
s('type_to_confirm','Ketik %1$s untuk melanjutkan.','Type %1$s to continue.')
s('label_only_in_clonapps','Label ini hanya tampil di ClonApps. Nama paket dan identitas aplikasi tidak berubah.','This label is shown in ClonApps only. The package name and app identity do not change.')
# aksi
A = {
 'action_allow':('Izinkan','Allow'),'action_deny':('Tolak','Deny'),'action_back':('Kembali','Back'),'action_cancel':('Batal','Cancel'),
 'action_check_profile':('Periksa profil','Check profile'),'action_check_requirements':('Periksa persyaratan','Check requirements'),
 'action_clear':('Bersihkan','Clear'),'action_clone_again':('Clone lagi','Clone again'),'action_confirm_running':('Ya, aplikasi terbuka di profil kerja','Yes, it opened in the work profile'),
 'action_create_profile':('Buat profil kerja','Create work profile'),'action_dismiss':('Tutup','Dismiss'),'action_edit_label':('Ubah label','Edit label'),
 'action_export':('Ekspor pengaturan','Export settings'),'action_import':('Impor pengaturan','Import settings'),'action_favorite':('Jadikan favorit','Add to favorites'),
 'action_unfavorite':('Hapus dari favorit','Remove from favorites'),'action_finish_setup':('Selesaikan penyiapan profil','Finish profile setup'),
 'action_open_clonapps_in_profile':('Buka ClonApps di profil kerja','Open ClonApps in work profile'),'action_open_clone':('Buka clone','Open clone'),
 'action_open_clone_named':('Buka clone %1$s','Open clone %1$s'),'action_open_detail':('buka detail','open details'),
 'action_open_system_settings':('Buka Pengaturan Android','Open Android Settings'),'action_pause_profile':('Jeda profil','Pause profile'),
 'action_resume_profile':('Lanjutkan profil','Resume profile'),'action_pick_apps':('Pilih aplikasi','Pick apps'),'action_recheck':('Periksa ulang','Check again'),
 'action_refresh':('Muat ulang','Refresh'),'action_remove_clone':('Hapus clone','Remove clone'),'action_remove_profile':('Hapus seluruh profil','Remove entire profile'),
 'action_repair_pairing':('Pasangkan ulang','Re-pair'),'action_report_problem':('Laporkan masalah','Report a problem'),'action_resume_queue':('Lanjutkan antrean','Resume queue'),
 'action_retry':('Coba lagi','Retry'),'action_save':('Simpan','Save'),'action_save_matrix':('Simpan matriks uji (CSV)','Save test matrix (CSV)'),
 'action_save_report':('Simpan laporan','Save report'),'action_share_report':('Bagikan laporan','Share report'),'action_set_profile_lock':('Atur kunci profil kerja','Set work profile lock'),
 'action_shortcut':('Buat shortcut','Create shortcut'),'action_start_clone':('Mulai clone (%1$d siap)','Start cloning (%1$d ready)'),
 'action_unlock':('Buka kunci','Unlock'),'action_update_from_main':('Perbarui dari versi utama','Update from main version'),'action_verify_profile':('Verifikasi profil','Verify profile'),
}
for k,(i,e) in A.items(): s(k,i,e)
# beranda
s('profile_card_title','Profil tujuan','Target profile')
s('profile_card_detail','%1$s · %2$s · %3$s','%1$s · %2$s · %3$s')
s('profile_active','aktif','active'); s('profile_paused','dijeda','paused'); s('profile_locked','terkunci','locked'); s('profile_unlocked','tidak terkunci','unlocked')
s('profile_others','Profil lain terdeteksi (tidak disentuh ClonApps): %1$s','Other profiles detected (not touched by ClonApps): %1$s')
s('profile_unverified','Kepemilikan profil belum diverifikasi. Tekan "Verifikasi profil".','Profile ownership is not verified yet. Tap "Verify profile".')
s('profile_foreign_explain','Profil kerja yang ada dikelola aplikasi atau organisasi lain. ClonApps tidak akan menghapus, mengganti, atau mengambil alihnya. Android hanya mengizinkan satu profil kerja, jadi ClonApps tidak dapat membuat profil baru selama profil itu ada.','The existing work profile is managed by another app or organization. ClonApps will not remove, replace or take it over. Android allows only one work profile, so ClonApps cannot create a new one while it exists.')
s('provisioning_failed_title','Pembuatan profil tidak berhasil','Profile creation did not succeed')
s('capacity_title','Kapasitas','Capacity')
s('capacity_body','Backend profil kerja: %1$d profil, %2$d salinan per aplikasi. Anda boleh memilih banyak aplikasi sekaligus, tetapi tiap aplikasi hanya dapat punya satu salinan di profil ini.','Work profile backend: %1$d profile, %2$d copy per app. You can pick many apps at once, but each app can have only one copy in this profile.')
s('queue_active_title','Antrean belum selesai','Unfinished queue')
s('queue_active_body','%1$d pekerjaan menunggu atau sedang diproses.','%1$d job(s) waiting or in progress.')
s('home_clones_title','Aplikasi clone','Cloned apps')
s('home_empty_title','Belum ada clone','No clones yet')
s('home_empty_body','Pilih aplikasi terpasang untuk dipasang di profil kerja dengan akun dan data terpisah.','Pick installed apps to set up in the work profile with separate accounts and data.')
s('home_empty_need_profile','Buat atau pasangkan profil kerja ClonApps lebih dulu.','Create or pair a ClonApps work profile first.')
# pilih aplikasi
s('pick_search_hint','Cari nama atau package name','Search name or package name')
s('pick_filter_user','Aplikasi pengguna','User apps'); s('pick_filter_with_system','Termasuk aplikasi sistem','Include system apps')
s('pick_sort_az','A–Z','A–Z'); s('pick_sort_za','Z–A','Z–A')
s('pick_system_note','Aplikasi sistem hanya dapat diaktifkan bila sudah ada di image profil kerja; tidak disalin. Komponen sistem tanpa ikon peluncur tidak ditampilkan.','System apps can only be enabled if they already exist in the work profile image; they are not copied. System components without a launcher icon are hidden.')
s('loading_apps','Memuat daftar aplikasi…','Loading installed apps…')
s('pick_empty','Tidak ada aplikasi yang cocok.','No matching apps.')
s('pick_version','v%1$s (%2$d)','v%1$s (%2$d)')
s('pick_apk_size','APK %1$s','APK %1$s')
s('pick_splits','%1$d split','%1$d split(s)')
s('pick_system_badge','sistem','system')
s('pick_selected_count','%1$d dipilih','%1$d selected')
s('summary_title','Ringkasan sebelum proses','Summary before processing')
s('summary_note','Setiap aplikasi diperiksa sendiri. Hanya yang berstatus "Siap dicoba" yang masuk antrean; satu kegagalan tidak membatalkan aplikasi lain. Android akan meminta konfirmasi Anda untuk setiap instalasi.','Each app is checked separately. Only apps marked "Ready to try" are queued; one failure does not cancel the others. Android will ask for your confirmation for every install.')
s('summary_checking','Memeriksa…','Checking…')
s('summary_method','Mekanisme: %1$s','Mechanism: %1$s')
# detail
s('detail_not_found','Clone tidak ditemukan.','Clone not found.')
s('detail_placeholder','Pilih clone untuk melihat detail.','Select a clone to see details.')
s('detail_actions','Tindakan','Actions')
s('detail_verify_title','Verifikasi berjalan','Verify it runs')
s('detail_verify_body','Android tidak memberi ClonApps cara membaca apakah aplikasi lain benar-benar berjalan. Status "Terverifikasi berjalan" hanya diberikan setelah Anda mengonfirmasinya.','Android gives ClonApps no way to read whether another app is really running. "Verified running" is only set after you confirm it.')
s('detail_status_title','Status','Status')
s('detail_profile','Profil','Profile'); s('detail_install_state','Status instalasi','Install state'); s('detail_install_method','Mekanisme','Mechanism')
s('detail_installed_version','Versi di profil','Version in profile'); s('detail_original_version','Versi aplikasi asli','Original app version')
s('detail_original_missing','aplikasi asli tidak terpasang','original app not installed')
s('detail_last_install','Instalasi terakhir','Last install'); s('detail_launch_state','Status peluncuran','Launch state'); s('detail_last_launch','Peluncuran terakhir','Last launch')
s('detail_present_in_profile','Ada di profil','Present in profile')
s('detail_identity_note','Clone memakai package name dan tanda tangan asli. Label dan shortcut ClonApps tidak mengubah identitas paket.','The clone keeps the original package name and signature. ClonApps labels and shortcuts do not change the package identity.')
s('detail_last_job','Pekerjaan terakhir','Last job')
s('detail_failure','Kegagalan','Failure')
s('detail_integration_title','Akun dan integrasi','Accounts and integration')
s('detail_integration_body','Login dilakukan sendiri di dalam aplikasi clone. ClonApps tidak menyalin sesi, token, atau data aplikasi asli. Notifikasi, kamera, mikrofon, file, dan layanan Google diatur per profil oleh Android: izinkan sendiri saat aplikasi memintanya. Hasil pemeriksaan per profil ada di Diagnostik.','Sign in yourself inside the cloned app. ClonApps does not copy sessions, tokens or the original app data. Notifications, camera, microphone, files and Google services are per profile in Android: grant them when the app asks. Profile checks are in Diagnostics.')
s('remove_clone_title','Hapus clone %1$s?','Remove clone %1$s?')
s('remove_clone_body','Hanya salinan di profil kerja yang dihapus, beserta akun dan data di salinan itu. Aplikasi asli dan datanya di profil utama tidak disentuh. Android akan meminta konfirmasi.','Only the copy in the work profile is removed, with the accounts and data in that copy. The original app and its data in the main profile are not touched. Android will ask for confirmation.')
s('job_kind','Jenis','Kind'); s('job_phase','Fase','Phase'); s('job_attempts','Percobaan','Attempts'); s('job_note','Catatan','Note')
s('job_attempts_short','percobaan %1$d/%2$d','attempt %1$d/%2$d')
# kegagalan
s('failure_stage','Tahap','Stage'); s('failure_code','Kode','Code'); s('failure_cause','Penyebab diketahui','Known cause')
s('failure_unknown','Belum diketahui','Not yet known'); s('failure_recovery','Tindakan pemulihan','Recovery'); s('failure_detail','Detail sistem','System detail')
s('failure_retryable','Masuk akal dicoba lagi','Worth retrying')
# aktivitas
s('activity_tab_queue','Antrean','Queue'); s('activity_tab_log','Riwayat','History')
s('activity_queue_empty','Antrean kosong.','Queue is empty.'); s('activity_log_empty','Belum ada riwayat.','No history yet.')
s('activity_log_local_only','Riwayat hanya disimpan di perangkat ini, tanpa telemetri.','History is stored on this device only, with no telemetry.')
# diagnostik
D = {
 'diag_device':('Perangkat (terdeteksi saat ini)','Device (detected now)'),'diag_model':('Model','Model'),'diag_android':('Android','Android'),
 'diag_rom':('ROM (heuristik)','ROM (heuristic)'),'diag_rom_evidence':('Bukti ROM','ROM evidence'),'diag_build':('Build','Build'),'diag_abi':('ABI didukung','Supported ABIs'),
 'diag_ram':('RAM','RAM'),'diag_storage':('Penyimpanan data','Data storage'),'diag_screen':('Layar','Screen'),
 'diag_rom_note':('Nilai dibaca dari API Android saat ini, bukan diasumsikan dari model. Deteksi ROM hanya heuristik dan menampilkan buktinya.','Values are read from Android APIs now, not assumed from the model. ROM detection is heuristic and shows its evidence.'),
 'diag_work_profile':('Dukungan profil kerja','Work profile support'),'diag_provisioning_ui':('Layar provisioning sistem tersedia','System provisioning screen available'),
 'diag_ownership':('Kepemilikan profil','Profile ownership'),'diag_profile_handle':('Profil ClonApps','ClonApps profile'),'diag_other_profile':('Profil lain','Other profile'),
 'diag_backends':('Backend & kemampuan','Backends & capabilities'),'diag_profile_checks':('Pemeriksaan di dalam profil kerja','Checks inside the work profile'),
 'diag_profile_checks_empty':('Belum diperiksa. Pemeriksaan dijalankan oleh ClonApps di dalam profil kerja.','Not checked yet. The check runs via ClonApps inside the work profile.'),
 'diag_restrictions':('Pembatasan aktif','Active restrictions'),'diag_packages_in_profile':('Paket terlihat di profil','Packages visible in profile'),
 'diag_integration_note':('Hasil "tersedia" berarti ada aplikasi di profil yang dapat menangani intent tersebut. Ini bukan bukti fitur di aplikasi clone bekerja; uji langsung di aplikasi.','"Available" means some app in the profile can handle that intent. It does not prove the feature works in the cloned app; test it in the app.'),
 'diag_report':('Laporan diagnostik','Diagnostic report'),
 'diag_report_note':('Laporan disensor otomatis (token, email, nomor, jalur data pribadi dihapus) dan tidak dikirim ke mana pun kecuali Anda membagikannya.','The report is redacted automatically (tokens, emails, numbers, private data paths removed) and is not sent anywhere unless you share it.'),
 'diag_adb_title':('Panduan log via ADB','ADB log guide'),
 'diag_adb_intro':('Android tidak mengizinkan ClonApps membaca log atau crash aplikasi lain. Untuk pengujian, ambil log dari komputer dengan ADB (USB debugging aktif):','Android does not let ClonApps read other apps\' logs or crashes. For testing, collect logs from a computer with ADB (USB debugging on):'),
}
for k,(i,e) in D.items(): s(k,i,e)
s('diag_storage_value','%1$s bebas dari %2$s','%1$s free of %2$s')
# pengaturan
P = {
 'settings_theme':('Tema','Theme'),'settings_apps':('Daftar aplikasi','App list'),'settings_show_system':('Tampilkan aplikasi sistem secara default','Show system apps by default'),
 'settings_lock':('Kunci','Lock'),'settings_dashboard_lock':('Kunci tampilan ClonApps','Lock the ClonApps screen'),
 'settings_lock_needs_secure':('Aktifkan kunci layar perangkat terlebih dahulu.','Set up a device screen lock first.'),
 'settings_lock_scope':('Kunci ini hanya melindungi tampilan ClonApps. Aplikasi clone tetap dapat dibuka dari launcher.','This lock protects the ClonApps screen only. Cloned apps can still be opened from the launcher.'),
 'settings_profile_lock_note':('Untuk melindungi semua aplikasi di profil kerja, atur kunci terpisah untuk profil kerja. Android yang menerapkannya.','To protect every app in the work profile, set a separate work profile lock. Android enforces it.'),
 'settings_profile':('Profil kerja','Work profile'),
 'settings_pause_note':('Jeda berlaku untuk seluruh profil kerja, bukan per aplikasi. Android hanya mengizinkan launcher default atau aplikasi sistem melakukannya langsung. Bila ditolak, gunakan tombol "Aplikasi kerja" di Quick Settings atau launcher.','Pause applies to the whole work profile, not per app. Android only lets the default launcher or system apps do this directly. If refused, use the "Work apps" toggle in Quick Settings or the launcher.'),
 'settings_backup':('Ekspor & impor','Export & import'),
 'settings_backup_note':('Hanya pengaturan ClonApps (tema, label, favorit). Tidak ada akun, kunci pemasangan, atau data aplikasi clone — Android tidak menyediakan cara sah untuk mencadangkan data privat aplikasi lain.','ClonApps settings only (theme, labels, favorites). No accounts, pairing key or cloned app data — Android has no legitimate way to back up another app\'s private data.'),
 'settings_privacy':('Privasi','Privacy'),
 'settings_privacy_body':('ClonApps bekerja tanpa server, tanpa akun ClonApps, dan tanpa telemetri. Konfigurasi disimpan lokal dan tidak ikut backup. ClonApps tidak mengumpulkan kredensial atau isi aplikasi clone.','ClonApps works without servers, without a ClonApps account and without telemetry. Configuration stays local and is excluded from backup. ClonApps does not collect credentials or cloned app content.'),
 'settings_danger':('Zona berbahaya','Danger zone'),
 'settings_wipe_note':('Menghapus seluruh profil kerja ClonApps beserta semua clone di dalamnya. Profil lain tidak disentuh.','Removes the entire ClonApps work profile with every clone in it. Other profiles are not touched.'),
 'wipe_title':('Hapus seluruh profil kerja?','Remove the entire work profile?'),
 'wipe_body':('Yang akan hilang: semua aplikasi clone, akun yang login di dalamnya, file, foto, dan pengaturan di profil kerja. Aplikasi dan data di profil utama tetap aman. Tindakan ini tidak dapat dibatalkan.','What will be lost: all cloned apps, accounts signed in there, files, photos and settings in the work profile. Apps and data in the main profile stay safe. This cannot be undone.'),
 'wipe_keyword':('HAPUS','REMOVE'),
 'lock_title':('ClonApps terkunci','ClonApps is locked'),
}
for k,(i,e) in P.items(): s(k,i,e)
# pesan
M = {
 'msg_profile_created':('Profil kerja dibuat dan dipasangkan.','Work profile created and paired.'),
 'msg_profile_no_answer':('Profil kerja tidak memberi jawaban. Pastikan profil aktif dan tidak terkunci.','The work profile did not answer. Make sure it is active and unlocked.'),
 'msg_paired':('Dipasangkan ulang.','Re-paired.'),'msg_pair_failed':('Pemasangan gagal: %1$s','Pairing failed: %1$s'),
 'msg_pair_no_handler':('Tidak ada ClonApps pengelola di profil itu. Profil mungkin dikelola aplikasi lain.','No managing ClonApps in that profile. It may be managed by another app.'),
 'msg_pause_requested':('Permintaan jeda/lanjut dikirim ke Android.','Pause/resume request sent to Android.'),
 'msg_pause_not_permitted':('Android menolak: hanya launcher default yang boleh menjeda profil. Gunakan tombol "Aplikasi kerja".','Android refused: only the default launcher may pause the profile. Use the "Work apps" toggle.'),
 'msg_not_paired':('Profil belum dipasangkan.','Profile is not paired.'),'msg_no_profile':('Belum ada profil kerja ClonApps.','No ClonApps work profile yet.'),
 'msg_skipped_duplicates':('%1$d aplikasi dilewati karena sudah ada di antrean.','%1$d app(s) skipped because they are already queued.'),
 'msg_cancel_on_system_screen':('Proses sedang ditangani Android. Batalkan dari layar konfirmasi sistem.','Android is handling this now. Cancel from the system confirmation screen.'),
 'msg_retry_rejected':('Tidak dicoba lagi: %1$s','Not retried: %1$s'),
 'msg_profile_paused_launch':('Profil dijeda; Android akan menanyakan apakah profil diaktifkan.','Profile is paused; Android will ask to turn it on.'),
 'msg_launch_failed':('Clone tidak dapat dibuka. Lihat detail kegagalan.','The clone could not be opened. See failure details.'),
 'msg_shortcut_requested':('Permintaan shortcut dikirim ke launcher.','Shortcut request sent to the launcher.'),
 'msg_shortcut_unsupported':('Launcher ini tidak mendukung shortcut yang disematkan.','This launcher does not support pinned shortcuts.'),
 'msg_shortcut_error':('Shortcut gagal: %1$s','Shortcut failed: %1$s'),
 'msg_shortcut_stale':('Shortcut ini tidak lagi menunjuk ke clone yang terpasang.','This shortcut no longer points to an installed clone.'),
 'msg_import_ok':('Diimpor: %1$d entri, %2$d dilewati.','Imported: %1$d entries, %2$d skipped.'),
 'msg_import_invalid':('Berkas ditolak: %1$s','File rejected: %1$s'),'msg_import_unreadable':('Berkas tidak dapat dibaca.','File could not be read.'),
 'msg_saved':('Tersimpan.','Saved.'),'msg_save_failed':('Gagal menyimpan.','Save failed.'),
}
for k,(i,e) in M.items(): s(k,i,e)
# profil kerja (sisi profil)
s('profile_cmd_processing','ClonApps (profil kerja) sedang memproses…','ClonApps (work profile) is working…')
s('profile_cmd_installing','Menyiapkan %1$s di profil kerja…','Preparing %1$s in the work profile…')
s('profile_cmd_uninstalling','Menghapus %1$s dari profil kerja…','Removing %1$s from the work profile…')
s('profile_cmd_waiting_user','Menunggu konfirmasi Anda di layar Android…','Waiting for your confirmation on the Android screen…')
s('profile_cmd_pair_title','Pasangkan ulang ClonApps?','Re-pair ClonApps?')
s('profile_cmd_pair_body','ClonApps di profil pribadi meminta izin mengelola profil kerja ini lagi (memasang, membuka, dan menghapus clone). Izinkan hanya jika Anda yang memulainya.','ClonApps in the personal profile asks to manage this work profile again (install, open and remove clones). Allow only if you started this.')
s('profile_cmd_wipe_title','Hapus profil kerja ini?','Remove this work profile?')
s('profile_cmd_wipe_body','Semua aplikasi, akun, dan data di profil kerja ini akan dihapus permanen.','All apps, accounts and data in this work profile will be permanently removed.')
s('profile_side_title','ClonApps — profil kerja','ClonApps — work profile')
s('profile_side_status','Status','Status')
s('profile_side_owner','ClonApps mengelola profil ini','ClonApps manages this profile')
s('profile_side_paired','Terpasang dengan ClonApps pribadi','Paired with personal ClonApps')
s('profile_side_body_owner','Kelola clone dari ClonApps di profil pribadi. Bila penyiapan tidak selesai otomatis, tekan tombol di bawah.','Manage clones from ClonApps in the personal profile. If setup did not finish automatically, tap the button below.')
s('profile_side_body_foreign','ClonApps tidak mengelola profil ini. Tidak ada tindakan yang dilakukan di sini.','ClonApps does not manage this profile. Nothing is done here.')

# ---------- enum ----------
def enum_values(path, name):
    src = open(os.path.join(ROOT, path)).read()
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    m = re.search(r'enum class ' + name + r'\b[^{]*\{([^{}]*?)(;|\})', src, re.S)
    body = m.group(1)
    body = re.sub(r'//.*', '', body); body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    vals = []
    for tok in body.split(','):
        t = tok.strip()
        if not t: continue
        t = re.match(r'[A-Z_0-9]+', t)
        if t: vals.append(t.group(0))
    return vals

E = {}
def enum(prefix, path, name, table):
    vals = enum_values(path, name)
    missing = [v for v in vals if v not in table]
    if missing: sys.exit(f'Missing {prefix}: {missing}')
    extra = [k for k in table if k not in vals]
    if extra: sys.exit(f'Extra (not in enum) {prefix}: {extra}')
    for v in vals: s(f'{prefix}_{v.lower()}', *table[v])
    E[prefix] = vals

C='core/src/main/kotlin/com/clonapps/core/'
enum('status', C+'model/Status.kt', 'CloneStatus', {
 'NOT_CHECKED':('Belum diperiksa','Not checked'),'READY':('Siap dicoba','Ready to try'),'NEEDS_USER_ACTION':('Perlu tindakan Anda','Needs your action'),
 'IN_PROGRESS':('Sedang diproses','In progress'),'INSTALLED':('Terpasang','Installed'),'LAUNCH_REQUESTED':('Peluncuran diminta','Launch requested'),
 'VERIFIED_RUNNING':('Terverifikasi berjalan','Verified running'),'FAILED':('Gagal','Failed')})
enum('install', C+'model/Status.kt', 'InstallState', {'NONE':('Belum','None'),'INSTALLED':('Terpasang','Installed'),'REMOVED':('Dihapus','Removed'),'FAILED':('Gagal','Failed')})
enum('launch', C+'model/Status.kt', 'LaunchState', {'NONE':('Belum dibuka','Not opened'),'REQUESTED':('Diminta (belum terbukti berjalan)','Requested (not proven running)'),
 'USER_CONFIRMED_RUNNING':('Dikonfirmasi berjalan oleh pengguna','Confirmed running by user'),'FAILED':('Gagal / bermasalah','Failed / problem')})
enum('method', C+'model/Status.kt', 'InstallMethod', {'ENABLE_SYSTEM_APP':('Aktifkan aplikasi sistem di profil','Enable system app in profile'),
 'INSTALL_EXISTING_PACKAGE':('installExistingPackage (sistem)','installExistingPackage (system)'),
 'PACKAGE_INSTALLER_COPY':('PackageInstaller: base + split APK asli, dengan konfirmasi','PackageInstaller: original base + split APKs, with confirmation'),
 'ALREADY_PRESENT':('Sudah ada di profil','Already in profile')})
enum('ownership', C+'model/Status.kt', 'ProfileOwnership', {'NONE':('Belum ada profil kerja','No work profile yet'),
 'OURS_PAIRED':('Profil kerja ClonApps siap','ClonApps work profile ready'),'OURS_UNPAIRED':('Profil ClonApps belum terpasang dengan aplikasi ini','ClonApps profile not paired with this app'),
 'FOREIGN':('Profil lain sudah ada (bukan milik ClonApps)','Another profile exists (not ClonApps)')})
enum('stage', C+'model/Failure.kt', 'FailureStage', {
 'PROVISIONING':('Pembuatan profil','Provisioning'),'PROFILE':('Profil','Profile'),'PRECHECK':('Pemeriksaan awal','Pre-check'),
 'PACKAGE_INCOMPLETE':('Paket tidak lengkap','Incomplete package'),'ABI':('Ketidakcocokan ABI','ABI mismatch'),'DEPENDENCY':('Dependensi','Dependency'),
 'INSTALL':('Instalasi','Install'),'VERIFY':('Verifikasi','Verification'),'LAUNCH':('Peluncuran','Launch'),'LOGIN':('Login','Login'),
 'TARGET_RESTRICTION':('Pembatasan aplikasi target','Target app restriction'),'UNINSTALL':('Penghapusan','Removal'),
 'COMMUNICATION':('Komunikasi antarprofil','Cross-profile communication'),'CANCELLED':('Dibatalkan','Cancelled')})
enum('unknown', C+'model/Failure.kt', 'FailureStage', {
 'PROVISIONING':('Android tidak memberi alasan rinci ke aplikasi; ROM dapat membatasi profil kerja tanpa pesan.','Android gives apps no detailed reason; the ROM may restrict work profiles silently.'),
 'PROFILE':('Keadaan profil di luar yang dilaporkan API publik.','Profile state beyond what public APIs report.'),
 'PRECHECK':('Hal yang hanya terlihat saat instalasi sebenarnya.','Things only visible during the real install.'),
 'PACKAGE_INCOMPLETE':('Apakah toko aplikasi akan melengkapi split yang hilang.','Whether the app store would supply the missing splits.'),
 'ABI':('Apakah ada versi aplikasi untuk ABI perangkat ini.','Whether a build exists for this device ABI.'),
 'DEPENDENCY':('Paket pustaka bersama mana yang harus ada di profil.','Which shared library package must exist in the profile.'),
 'INSTALL':('Pesan sistem bisa umum; log PackageManager via ADB memberi detail.','System messages can be generic; PackageManager logs via ADB give details.'),
 'VERIFY':('Mengapa sistem tidak menampilkan paket setelah sukses.','Why the system does not show the package after success.'),
 'LAUNCH':('Isi crash aplikasi target tidak dapat dibaca ClonApps.','The target app crash content cannot be read by ClonApps.'),
 'LOGIN':('Alasan dari server aplikasi target tidak terlihat oleh ClonApps.','The target app server reason is not visible to ClonApps.'),
 'TARGET_RESTRICTION':('Kebijakan internal aplikasi target (lisensi, integritas, profil kerja).','The target app internal policy (license, integrity, work profile).'),
 'UNINSTALL':('Alasan rinci penolakan sistem.','The detailed reason for the system refusal.'),
 'COMMUNICATION':('Apakah profil dijeda, terkunci, atau ClonApps profil dihentikan.','Whether the profile is paused, locked, or profile ClonApps was stopped.'),
 'CANCELLED':('Tidak ada.','Nothing.')})
causes = {
 'MANAGED_USERS_FEATURE_MISSING':('Perangkat/ROM tidak mendeklarasikan fitur profil kerja (android.software.managed_users).','The device/ROM does not declare work profile support (android.software.managed_users).'),
 'PROVISIONING_NOT_ALLOWED':('Android menolak membuat profil kerja (isProvisioningAllowed=false), mis. sudah ada profil kerja lain atau dibatasi ROM.','Android refuses to create a work profile (isProvisioningAllowed=false), e.g. another one exists or the ROM restricts it.'),
 'PROVISIONING_UI_MISSING':('Layar penyiapan profil kerja sistem (ManagedProvisioning) tidak ada di ROM ini.','The system work profile setup screen (ManagedProvisioning) is missing on this ROM.'),
 'PROVISIONING_CANCELLED':('Layar penyiapan ditutup atau ditolak.','The setup screen was closed or declined.'),
 'PROVISIONING_FAILED':('Penyiapan tidak menghasilkan profil yang terlihat.','Setup did not produce a visible profile.'),
 'FOREIGN_PROFILE_EXISTS':('Profil kerja lain sudah ada dan tidak dikelola ClonApps.','Another work profile exists and is not managed by ClonApps.'),
 'PROFILE_NOT_FOUND':('Belum ada profil kerja ClonApps.','No ClonApps work profile yet.'),
 'PROFILE_PAUSED':('Profil kerja sedang dijeda.','The work profile is paused.'),
 'PROFILE_LOCKED':('Profil kerja terkunci; buka kunci dulu.','The work profile is locked; unlock it first.'),
 'PROFILE_NOT_PAIRED':('Kunci komunikasi dengan ClonApps di profil kerja tidak ada atau tidak cocok.','The communication key with ClonApps in the work profile is missing or does not match.'),
 'PROFILE_NOT_OWNER':('ClonApps bukan pengelola profil tersebut.','ClonApps is not the owner of that profile.'),
 'PROFILE_UNREACHABLE':('Perintah tidak sampai ke ClonApps di profil kerja.','The command did not reach ClonApps in the work profile.'),
 'INSUFFICIENT_STORAGE':('Ruang penyimpanan tidak cukup (perkiraan: 2× ukuran APK + 100 MB).','Not enough storage (estimate: 2× APK size + 100 MB).'),
 'BASE_APK_MISSING':('Base APK aplikasi asli tidak ditemukan.','The original app base APK was not found.'),
 'APK_UNREADABLE':('File APK tidak dapat dibaca.','An APK file cannot be read.'),
 'SPLIT_MISSING':('Split APK tidak lengkap atau tidak terbaca.','Split APKs are incomplete or unreadable.'),
 'ARCHIVE_MISMATCH':('APK di disk tidak cocok dengan versi terpasang (aplikasi baru diperbarui?).','The APK on disk does not match the installed version (app just updated?).'),
 'NO_MATCHING_ABI':('Native library aplikasi tidak cocok dengan ABI perangkat.','The app native libraries do not match the device ABI.'),
 'MISSING_SHARED_LIBRARY':('Aplikasi bergantung pada pustaka bersama yang mungkin belum ada di profil kerja.','The app depends on a shared library that may not exist in the work profile.'),
 'SYSTEM_APP_NOT_IN_PROFILE':('Aplikasi sistem ini tidak tersedia di image profil kerja.','This system app is not available in the work profile image.'),
 'IS_CLONAPPS_ITSELF':('ClonApps tidak meng-clone dirinya sendiri.','ClonApps does not clone itself.'),
 'PENDING_USER_ACTION':('Android meminta persetujuan Anda (mis. izin memasang aplikasi di profil kerja).','Android asks for your approval (e.g. allow installing apps in the work profile).'),
 'USER_CANCELLED':('Dibatalkan oleh pengguna.','Cancelled by the user.'),
 'UNKNOWN_SOURCES_BLOCKED':('Profil kerja melarang instalasi dari sumber tidak dikenal.','The work profile blocks installs from unknown sources.'),
 'INSTALL_BLOCKED_BY_POLICY':('Instalasi diblokir kebijakan atau pembatasan pengguna.','The install was blocked by policy or a user restriction.'),
 'INSTALL_EXISTING_REJECTED':('Android menolak installExistingPackage untuk profil ini.','Android rejected installExistingPackage for this profile.'),
 'SIGNATURE_CONFLICT':('Tanda tangan bertentangan dengan salinan yang sudah ada.','Signature conflicts with an existing copy.'),
 'VERSION_DOWNGRADE':('Versi di profil lebih baru dari aplikasi asli.','The profile version is newer than the original app.'),
 'TARGET_SDK_TOO_LOW':('Aplikasi menargetkan API terlalu lama; Android versi ini menolak memasangnya.','The app targets a too-old API; this Android version refuses to install it.'),
 'DEVICE_SDK_TOO_OLD':('Paket tidak kompatibel dengan perangkat ini.','The package is incompatible with this device.'),
 'PACKAGE_INVALID':('Paket tidak valid atau rusak.','The package is invalid or corrupt.'),
 'PERMISSION_CONFLICT':('Permission yang dideklarasikan bentrok dengan aplikasi lain di profil.','A declared permission conflicts with another app in the profile.'),
 'PROVIDER_CONFLICT':('Content provider bentrok dengan aplikasi lain di profil.','A content provider conflicts with another app in the profile.'),
 'VERIFICATION_FAILED':('Verifikasi paket (mis. Play Protect) gagal.','Package verification (e.g. Play Protect) failed.'),
 'INSTALL_TIMEOUT':('Instalasi tidak selesai dalam batas waktu.','The install did not finish in time.'),
 'INSTALL_CONFLICT':('Bentrok dengan paket yang sudah ada.','Conflict with an existing package.'),
 'SESSION_ERROR':('Session PackageInstaller bermasalah.','PackageInstaller session error.'),
 'NOT_PRESENT_AFTER_INSTALL':('Instalasi dilaporkan sukses tetapi paket tidak terlihat di profil.','Install reported success but the package is not visible in the profile.'),
 'INTERRUPTED':('Proses terputus sebelum hasil diketahui.','The process was interrupted before the result was known.'),
 'NO_LAUNCHER_ACTIVITY':('Tidak ada activity peluncur di profil kerja.','No launcher activity in the work profile.'),
 'LAUNCH_SECURITY_EXCEPTION':('Sistem menolak peluncuran (SecurityException).','The system refused the launch (SecurityException).'),
 'LAUNCH_ACTIVITY_NOT_FOUND':('Activity tujuan tidak ditemukan.','The target activity was not found.'),
 'TARGET_RESTRICTS_PROFILE':('Aplikasi target membatasi profil kerja, lisensi, atau integritas perangkat.','The target app restricts work profiles, licensing or device integrity.'),
 'AUTH_FAILED':('Perintah ditolak: tanda tangan, waktu, atau nonce tidak valid.','Command rejected: invalid signature, time or nonce.'),
 'BAD_REQUEST':('Permintaan tidak valid.','Invalid request.'),
 'UNKNOWN':('Belum diketahui.','Unknown.'),
}
enum('cause', C+'model/Failure.kt', 'FailureCause', causes)
enum('recovery', C+'model/Failure.kt', 'RecoveryAction', {
 'NONE':('Tidak ada tindakan yang diperlukan.','No action needed.'),'RETRY':('Coba lagi.','Try again.'),
 'FREE_STORAGE':('Kosongkan ruang penyimpanan lalu coba lagi.','Free up storage and try again.'),
 'UNPAUSE_PROFILE':('Lanjutkan profil kerja (tombol "Aplikasi kerja").','Resume the work profile ("Work apps" toggle).'),
 'UNLOCK_PROFILE':('Buka kunci profil kerja.','Unlock the work profile.'),'CREATE_PROFILE':('Buat profil kerja ClonApps.','Create the ClonApps work profile.'),
 'REPAIR_PAIRING':('Pasangkan ulang dari Beranda.','Re-pair from Home.'),
 'OPEN_PROFILE_SETTINGS':('Periksa profil di Pengaturan Android › Akun / Profil kerja.','Check profiles in Android Settings › Accounts / Work profile.'),
 'ALLOW_UNKNOWN_SOURCES_IN_PROFILE':('Izinkan ClonApps (profil kerja) memasang aplikasi saat Android meminta.','Allow ClonApps (work profile) to install apps when Android asks.'),
 'UPDATE_ORIGINAL_APP':('Perbarui atau pasang ulang aplikasi asli dari toko resminya, lalu coba lagi.','Update or reinstall the original app from its official store, then retry.'),
 'INSTALL_FROM_WORK_PLAY_STORE':('Pasang dari Play Store di profil kerja bila tersedia.','Install from the work profile Play Store if available.'),
 'INSTALL_DEPENDENCY_FIRST':('Pasang aplikasi/pustaka yang dibutuhkan ke profil kerja dulu.','Install the required app/library into the work profile first.'),
 'REMOVE_PROFILE_COPY_FIRST':('Hapus salinan lama di profil kerja, lalu clone lagi.','Remove the old copy in the work profile, then clone again.'),
 'COLLECT_ADB_LOG':('Ambil log via ADB (lihat Diagnostik).','Collect logs via ADB (see Diagnostics).'),
 'CONTACT_TARGET_DEVELOPER':('Keterbatasan aplikasi target; hubungi pengembangnya.','Target app limitation; contact its developer.'),
 'USE_OTHER_DEVICE_OR_ROM':('Tidak dapat diperbaiki di perangkat/ROM ini.','Cannot be fixed on this device/ROM.')})
enum('check', C+'validate/PackageValidator.kt', 'CheckId', {
 'NOT_SELF':('Bukan ClonApps','Not ClonApps itself'),'PROFILE_READY':('Profil tujuan','Target profile'),'PROFILE_ACTIVE':('Profil aktif','Profile active'),
 'INSTALL_CAPABILITY':('Kemampuan instalasi','Install capability'),'BASE_APK':('Base APK','Base APK'),'SPLITS_COMPLETE':('Split APK lengkap','Split APKs complete'),
 'ARCHIVE_IDENTITY':('Konsistensi paket','Package consistency'),'ABI':('ABI','ABI'),'SHARED_LIBRARIES':('Pustaka bersama','Shared libraries'),
 'STORAGE':('Ruang penyimpanan','Storage'),'SYSTEM_APP_IN_PROFILE':('Aplikasi sistem di profil','System app in profile'),'ALREADY_IN_PROFILE':('Sudah di profil','Already in profile')})
enum('outcome', C+'validate/PackageValidator.kt', 'CheckOutcome', {'PASS':('lolos','pass'),'WARN':('peringatan','warning'),'FAIL':('gagal','fail'),'UNKNOWN':('belum diketahui','unknown')})
enum('jobkind', C+'queue/CloneJob.kt', 'JobKind', {'INSTALL':('Pasang clone','Install clone'),'UNINSTALL':('Hapus clone','Remove clone')})
phases = {
 'QUEUED':(('Dalam antrean','Queued'),('Menunggu giliran.','Waiting for its turn.')),
 'CHECKING':(('Memeriksa','Checking'),('Memeriksa ulang persyaratan sebelum instalasi.','Re-checking requirements before install.')),
 'AWAITING_USER':(('Menunggu Anda','Waiting for you'),('Android menampilkan layar konfirmasi.','Android is showing a confirmation screen.')),
 'INSTALLING':(('Memproses di profil','Working in profile'),('ClonApps di profil kerja sedang memproses.','ClonApps in the work profile is working.')),
 'VERIFYING':(('Memverifikasi','Verifying'),('Mencocokkan keberadaan paket di profil.','Checking the package exists in the profile.')),
 'SUCCEEDED':(('Berhasil','Succeeded'),('Selesai dan terverifikasi ada di profil.','Done and verified present in the profile.')),
 'FAILED':(('Gagal','Failed'),('Lihat penyebab dan tindakan pemulihan.','See cause and recovery.')),
 'CANCELLED':(('Dibatalkan','Cancelled'),('Tidak diproses lebih lanjut.','Not processed further.')),
 'INTERRUPTED':(('Terputus','Interrupted'),('Akan dicocokkan ulang dengan keadaan profil sebelum dilanjutkan.','Will be reconciled with the profile state before continuing.')),
}
enum('phase', C+'queue/CloneJob.kt', 'JobPhase', {k:v[0] for k,v in phases.items()})
enum('phasehint', C+'queue/CloneJob.kt', 'JobPhase', {k:v[1] for k,v in phases.items()})
enum('theme', C+'store/Codecs.kt', 'ThemeMode', {'SYSTEM':('Ikuti sistem','Follow system'),'LIGHT':('Terang','Light'),'DARK':('Gelap','Dark')})
enum('backend', C+'backend/Backend.kt', 'BackendId', {'WORK_PROFILE':('Profil kerja Android','Android work profile'),
 'SYSTEM_CLONE_PROFILE':('Clone/dual app bawaan ROM','ROM built-in clone/dual apps'),'VIRTUALIZATION_ENGINE':('Engine virtualisasi','Virtualization engine')})
enum('backendnote', C+'backend/Backend.kt', 'BackendId', {
 'WORK_PROFILE':('Dipakai ClonApps. Identitas & tanda tangan paket asli tetap; data terpisah oleh Android. 1 salinan per aplikasi.','Used by ClonApps. Original package identity & signature kept; data separated by Android. 1 copy per app.'),
 'SYSTEM_CLONE_PROFILE':('Hanya dapat dikendalikan aplikasi sistem/ROM. ClonApps mendeteksi profil lain tetapi tidak mengubahnya.','Only controllable by system/ROM apps. ClonApps detects other profiles but does not change them.'),
 'VIRTUALIZATION_ENGINE':('Tidak disertakan setelah audit (lihat docs/BACKENDS.md): API tersembunyi, pemeliharaan, isolasi, dan lisensi.','Not included after audit (see docs/BACKENDS.md): hidden APIs, maintenance, isolation and licensing.')})
enum('availability', C+'backend/Backend.kt', 'BackendAvailability', {'AVAILABLE':('tersedia','available'),
 'DETECTED_NOT_CONTROLLABLE':('terdeteksi, tidak dapat dikendalikan','detected, not controllable'),'NOT_AVAILABLE':('tidak tersedia','not available'),'NOT_IMPLEMENTED':('tidak diimplementasikan','not implemented')})
enum('log', 'app/src/main/java/com/clonapps/data/Stores.kt', 'LogCode', {
 'PROVISION_STARTED':('Pembuatan profil dimulai','Profile creation started'),'PROVISION_OK':('Profil kerja dibuat','Work profile created'),'PROVISION_FAILED':('Pembuatan profil gagal','Profile creation failed'),
 'PROFILE_VERIFIED':('Profil diverifikasi','Profile verified'),'PAIRED':('Dipasangkan','Paired'),'PAIR_FAILED':('Pemasangan gagal','Pairing failed'),
 'ENQUEUED':('Masuk antrean','Queued'),'JOB_STARTED':('Pekerjaan dimulai','Job started'),'JOB_SUCCEEDED':('Pekerjaan berhasil','Job succeeded'),
 'JOB_FAILED':('Pekerjaan gagal','Job failed'),'JOB_CANCELLED':('Pekerjaan dibatalkan','Job cancelled'),'JOB_RECONCILED':('Dicocokkan ulang','Reconciled'),'JOB_RETRY':('Dicoba lagi','Retried'),
 'LAUNCH_REQUESTED':('Peluncuran diminta','Launch requested'),'LAUNCH_FAILED':('Peluncuran gagal','Launch failed'),
 'USER_CONFIRMED_RUNNING':('Pengguna mengonfirmasi berjalan','User confirmed running'),'USER_REPORTED_PROBLEM':('Pengguna melaporkan masalah','User reported a problem'),
 'CLONE_REMOVED':('Clone dihapus','Clone removed'),'SHORTCUT_REQUESTED':('Shortcut diminta','Shortcut requested'),
 'SETTINGS_EXPORTED':('Pengaturan diekspor','Settings exported'),'SETTINGS_IMPORTED':('Pengaturan diimpor','Settings imported'),
 'PROFILE_PAUSE_REQUESTED':('Jeda profil diminta','Profile pause requested'),'PROFILE_PAUSE_DENIED':('Jeda profil ditolak Android','Profile pause denied by Android'),
 'PROFILE_LOCK_REQUESTED':('Kunci profil kerja dibuka','Work profile lock opened'),'PROFILE_REMOVE_REQUESTED':('Penghapusan profil diminta','Profile removal requested'),
 'ORPHAN_RESULT':('Hasil tiba setelah proses dimulai ulang','Result arrived after restart'),'INFO_REFRESHED':('Info profil diperbarui','Profile info refreshed')})
enum('problem', 'app/src/main/java/com/clonapps/domain/CloneCoordinator.kt', 'ProblemKind', {
 'LOGIN':('Login gagal','Login failed'),'TARGET_RESTRICTION':('Aplikasi menolak profil kerja / integritas','App refuses work profile / integrity'),'LAUNCH':('Tidak terbuka atau langsung tertutup','Does not open or closes at once')})
for k,(i,e) in {'camera':('Kamera (ACTION_IMAGE_CAPTURE)','Camera (ACTION_IMAGE_CAPTURE)'),'filepicker':('Pemilih file (ACTION_OPEN_DOCUMENT)','File picker (ACTION_OPEN_DOCUMENT)'),
 'browser':('Membuka tautan (https)','Opening links (https)'),'share':('Berbagi konten (ACTION_SEND)','Sharing content (ACTION_SEND)'),
 'googleplayservices':('Layanan Google Play','Google Play services'),'playstore':('Play Store','Play Store'),'webviewprovider':('Penyedia WebView','WebView provider')}.items():
    s('integration_'+k, i, e)

def esc(t):
    t = t.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;')
    t = t.replace("\\'", "'").replace("'", "\\'").replace('"','\\"')
    if t.startswith('@') or t.startswith('?'): t = '\\' + t
    return t

def write(path, idx, header):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path,'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- ' + header + ' Dibangkitkan oleh tools/gen_strings.py; ubah di sana. -->\n<resources>\n')
        for k in sorted(S):
            attr = ' translatable="false"' if k == 'app_name' and idx == 0 else ''
            if k == 'app_name' and idx == 1: continue
            f.write(f'    <string name="{k}"{attr}>{esc(S[k][idx])}</string>\n')
        f.write('</resources>\n')

write(ROOT+'/app/src/main/res/values/strings.xml', 0, 'Bahasa utama: Indonesia.')
write(ROOT+'/app/src/main/res/values-en/strings.xml', 1, 'English.')
# Cek semua R.string di kode terdefinisi
code = ''
for d,_,fs in os.walk(ROOT+'/app/src/main/java'):
    for fn in fs: code += open(os.path.join(d,fn)).read()
used = set(re.findall(r'R\.string\.([a-z_0-9]+)', code))
miss = sorted(u for u in used if u not in S)
if miss: sys.exit('Undefined R.string: %s' % miss)
print('strings:', len(S)); [print(' ', k, len(v)) for k, v in E.items()]
