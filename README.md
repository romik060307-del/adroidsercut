# Assistive AI — Android floating assistant

Proyek Android native (Java) untuk membuat bola pintas AI yang melayang di atas aplikasi lain. Dibuat sebagai source project agar bisa diunggah ke GitHub dan dibangun menjadi APK melalui GitHub Actions.

## Fitur yang sudah disiapkan

- Bola pintas AI yang dapat digeser dan dibuka dari aplikasi lain.
- Panel ringkas dengan pilihan Gemini atau DeepSeek.
- Kirim pertanyaan teks ke backend Cloudflare Worker.
- Tangkap layar untuk dianalisis setelah pengguna menyetujui dialog izin tangkap layar resmi Android.
- Kunci API provider berada di Cloudflare Worker Secrets, bukan di kode Android atau APK.
- Notifikasi foreground service saat bola pintas aktif.
- Workflow GitHub Actions untuk menghasilkan `app-debug.apk`.

## 1. Deploy backend Cloudflare Worker

Worker wajib disiapkan lebih dahulu agar aplikasi dapat menghubungi model AI.

1. Buat akun Cloudflare dan pasang Node.js serta Wrangler di komputer.
2. Buka terminal pada folder `cloudflare-worker`.
3. Jalankan:

   ```bash
   npm install -g wrangler
   wrangler login
   wrangler deploy
   ```

4. Setelah Worker dibuat, tambahkan key provider sebagai **secret**, bukan sebagai teks di file proyek:

   ```bash
   wrangler secret put GEMINI_API_KEY
   wrangler secret put DEEPSEEK_API_KEY
   wrangler secret put APP_ACCESS_TOKEN
   ```

   Isi setiap prompt dengan key baru yang sudah kamu buat/rotasi. `APP_ACCESS_TOKEN` adalah token akses buatanmu sendiri untuk membatasi pemakaian endpoint Worker.

5. Worker URL biasanya berbentuk `https://assistive-ai-gateway.<akun>.workers.dev`.

> Jangan menaruh API key di Java, `gradle.properties`, GitHub repository, file ZIP, atau APK. Kunci yang dibundel ke aplikasi Android dapat diekstrak dari APK. Simpan hanya di Worker Secrets.

## 2. Build APK melalui GitHub

1. Buat repository GitHub baru.
2. Ekstrak ZIP ini, lalu unggah **isi folder proyek** ke root repository. Pastikan `settings.gradle`, `build.gradle`, `app/`, dan `.github/` terlihat langsung di root.
3. Buka tab **Actions**.
4. Pilih workflow **Build Assistive AI APK**.
5. Tekan **Run workflow**.
6. Setelah workflow berhasil, buka hasil run dan unduh artifact bernama `AssistiveAI-debug-apk`. Di dalamnya ada `app-debug.apk`.

Workflow menggunakan Java 17 dan Gradle 8.9. Proyek ini menargetkan Android API 35 dan mendukung Android 8.0 (API 26) ke atas.

## 3. Pengaturan pertama di HP

1. Instal APK dan buka Assistive AI.
2. Isi URL Worker, contoh `https://nama-worker.workers.dev`.
3. Isi token akses yang sama dengan nilai `APP_ACCESS_TOKEN` di Worker.
4. Tekan **Simpan koneksi**.
5. Tekan **Aktifkan Assistive Touch** dan izinkan aplikasi tampil di atas aplikasi lain.
6. Tekan bola AI ketika sedang memakai aplikasi lain.

Untuk analisis layar, tekan **Tangkap layar** dan setujui dialog Android. Android menampilkan indikator/izin tangkap layar; aplikasi tidak dapat mengambil layar secara diam-diam. Setelah screenshot dikirim ke Worker, gambar diteruskan ke provider AI yang dipilih.

## 4. Catatan penting soal screenshot dan privasi

- Jangan menangkap layar yang menampilkan password, kode OTP, data perbankan, atau informasi pribadi orang lain.
- Gambar layar dikirim melalui internet ke Worker dan model AI yang kamu pilih untuk dianalisis.
- Persetujuan tangkap layar berasal dari sistem Android. Pengguna harus menyetujuinya saat diminta.
- DeepSeek dan Gemini menggunakan endpoint/model yang dapat berubah. Jika provider mengembalikan error model atau key, periksa dokumentasi provider dan log Worker.
- Provider AI dapat mengenakan biaya atau memiliki batas pemakaian sesuai akun/key yang digunakan.

## 5. Catatan agar bola pintas tetap berjalan

Bola pintas dijalankan sebagai foreground service dan menampilkan notifikasi selama aktif. Android maupun sistem penghemat baterai pabrikan tetap dapat menghentikan service. Pada beberapa perangkat Vivo, pengguna mungkin perlu mengizinkan autostart/latar belakang dan mengecualikan Assistive AI dari penghemat baterai melalui Pengaturan perangkat. Nama menu berbeda-beda menurut versi Funtouch OS.

Aplikasi tidak bisa menjamin bola selalu hidup setelah force-stop, reboot, pencabutan izin overlay, atau penghentian oleh sistem. Setelah HP dimulai ulang, buka aplikasi dan aktifkan kembali bila perlu.

## Batasan versi awal

- Ini adalah starter project yang siap dibangun, tetapi belum diuji pada perangkat Vivo fisik di sesi ini.
- Tidak ada mode mendengarkan mikrofon terus-menerus atau mengambil layar tanpa tindakan pengguna.
- Fitur chat belum menyimpan riwayat percakapan lintas sesi.
- Worker ini adalah gateway minimal. Untuk penggunaan publik, tambahkan Cloudflare rate limiting dan pantau pemakaian agar endpoint tidak disalahgunakan.
