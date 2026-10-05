# AutoVNC - Android Auto Uyumlu VNC Viewer

**AutoVNC**, aracınızın multimedya ekranında (Android Auto) uzak masaüstü bilgisayarlarınızı, sunucularınızı veya Raspberry Pi sistemlerinizi görüntülemenizi ve doğrudan aracın dokunmatik ekranından kontrol etmenizi sağlayan yerel bir Android uygulamasıdır.

---

## 🚀 Özellikler

1. **Android Auto Entegrasyonu (Android for Cars App Library):**
   - Araç ekranına özel `NavigationTemplate` ve `SurfaceCallback` mimarisi.
   - Doğrudan aracın donanım yüzeyine (`Surface`) akıcı Canvas çizimi.
   - Çoklu ölçeklendirme modları: **Ekrana Sığdır (Fit)**, **Ekranı Doldur (Fill)** ve **Birebir (1:1)**.

2. **Telefondan Araç Ekranı Tuşlarını Gizleme / Açma (Yeni):**
   - Telefondaki ana ekrandan veya canlı bağlantı çubuğundan tek tıkla araç ekranındaki kontrol çubuğu gizlenebilir.
   - **Gizli Mod:** Araç ekranında sadece kesintisiz, %100 tam ekran uzak masaüstü kalır; buton kalabalığı kalkar.
   - **Görünür Mod:** Çıkış, Ölçek, Tıklama ve Tuşlar butonları araç ekranında gösterilir.

3. **Bitrate & FPS Ayarları (Yeni):**
   - **FPS (Kare Hızı) Sınırlayıcı:** `15 FPS` (Tasarruf), `30 FPS` (Standart), `45 FPS` (Akıcı) ve `60 FPS` (Ultra). RFB protokolünde bant genişliğini ve CPU kullanımını dinamik olarak sınırlar.
   - **Bitrate / Renk Kalitesi:**
     - `32-bit ARGB8888 (Kayıpsız / Maksimum Kalite)`: Kristal netliğinde piksel aktarımı.
     - `16-bit RGB565 (Dengeli / %50 Tasarruf)`: Veri aktarımını ve ağ trafiğini yarı yarıya indirerek zayıf ağlarda sıfır gecikmeli akış sağlar.

4. **Duolingo Tasarım Sistemi (Gamified UI):**
   - Baykuş Yeşili (`#58CC02`), Ateş Turuncusu (`#FF9600`), Kuğu Grisi (`#E5E5E5`).
   - 4px alt gölgeli taktil 3D butonlar ve basış efekti.
   - 18px radiuslu yumuşak kartlar ve 9999px hap şeklinde bilgi etiketleri.

5. **Gelişmiş Dokunmatik Kontroller:**
   - **Tıklama Haritalama:** Araç ekranındaki dokunma koordinatları, çözünürlük farkı gözetilmeksizin uzak masaüstünün tam pikseline eşlenir (`Matrix.invert`).
   - **Tıklama Modları:** Sol Tık, Sağ Tık ve Çift Tık geçişleri.
   - **Çift Parmak Yakınlaştırma (Pinch-to-Zoom):** Araç ekranında parmaklarınızla yakınlaşıp uzaklaşabilirsiniz.
   - **Kaydırma & Fare Tekerleği:** Yakınlaştırıldığında ekranı kaydırır; normal modda fare tekerleği (Scroll Up / Scroll Down) sinyali gönderir.

6. **Özel Tuş Kombinasyonları Menüsü:**
   - Araç ekranından veya telefondan tek dokunuşla gönderilebilir:
     - `Ctrl + Alt + Del` (Windows kilit ekranı veya Görev Yöneticisi için)
     - `Windows / Super Tuşu`
     - `Enter`, `Escape`, `Tab`, `Backspace`, `Space`
     - `Alt + Tab`
     - `Yön Tuşları (Yukarı, Aşağı, Sol, Sağ)`
     - `F5 (Yenile)`

7. **RFB 3.8 Standart VNC Motoru:**
   - Standart VNC kimlik doğrulaması (DES Challenge-Response bit aynalama algoritması).
   - `Raw`, `CopyRect` ve dinamik `DesktopSize` kodlama desteği.
   - Arka planda oturumu canlı tutan `ForegroundService`.

---

## 📲 Kurulum ve Android Auto Ayarları

### 1. Android Auto Geliştirici Modunu Açma
1. Telefonunuzun **Ayarlar** menüsünden **Android Auto** bölümünü açın.
2. Sayfanın en altına inin ve **Sürüm (Version)** yazısına art arda **10 kez** dokunun.
3. Ekrana gelen "Geliştirici modu etkinleştirilsin mi?" uyarısını **Tamam** diyerek onaylayın.
4. Sağ üstteki **3 nokta** simgesine dokunup **Geliştirici Ayarları (Developer settings)** seçeneğine girin.
5. Listeden **Bilinmeyen Kaynaklar (Unknown sources)** kutucuğunu işaretleyin.

### 2. APK'yı Yükleme
Telefonunuzu bilgisayara bağlayıp terminalden:
```bash
adb install -r /home/deku/AutoVNC/AutoVNC.apk
```

---

## 🖥️ Bilgisayar Üzerinde Android Auto Simülatörüyle Test (DHU)

Aracınıza gitmeden bilgisayarınız üzerinden araç multimedya ekranını test edebilirsiniz:
1. Telefonunuzda **Android Auto -> Geliştirici Ayarları -> Baş ana birim sunucusunu başlat (Start head unit server)** seçeneğini açın.
2. Terminalden portu yönlendirin ve simülatörü başlatın:
   ```bash
   adb forward tcp:5277 tcp:5277
   /home/deku/android-sdk/extras/google/auto/desktop-head-unit
   ```
3. Masaüstünüzde açılan Android Auto penceresinden **AutoVNC**'yi seçin.

---

## 🚗 Kullanım

1. **Telefonunuzdan** AutoVNC uygulamasını açın ve "+ Yeni VNC Sunucusu Ekle" butonuna basarak bilgisayarınızın yerel IP adresini (örn: `192.168.1.100`), FPS (15/30/45/60) ve renk kalitesini (16-bit/32-bit) belirleyin.
2. Dilerseniz "Test Et" butonuna basarak bağlantının çalıştığını telefonunuzda test edin.
3. Telefonunuzu aracınıza (kablolu veya kablosuz) bağlayarak Android Auto'yu başlatın.
4. Aracın multimedya ekranındaki uygulama menüsünden **AutoVNC** ikonuna dokunun.
5. Ekrandan bağlanmak istediğiniz sunucuyu seçin; uzak masaüstünüz aracınızın ekranında belirecektir!
