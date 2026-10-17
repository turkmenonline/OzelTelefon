# Özel Telefon (iOS tarzı) — Android

Kotlin + Jetpack Compose. Sekmeler: Hızlı Arama, Son Aramalar, Kişiler, Klavye, Sesli Mesaj.
Çağrı ekranı: kişi fotoğrafı arka planlı, Hoparlör / Sessiz / Beklet / Klavye / Bitir.

## Kurulum
1. Android Studio'yu aç → **Open** → bu klasörü seç, Gradle sync bitsin.
2. Telefonda **USB hata ayıklama** açık, kabloyla bağla, ▶ Run.
3. İlk açılışta izinleri ver.

## ÖNEMLİ: Varsayılan telefon uygulaması yap
Arama sırasında telefonun kendi ekranı çıkıyorsa, bu uygulama varsayılan telefon uygulaması değil demektir
(üstte mavi şerit görünüyorsa henüz değil). Şeride dokun → "Özel Telefon"u onayla.
Sistem penceresi çıkmazsa uygulama seni Ayarlar'a götürür:
Ayarlar → Uygulamalar → Varsayılan uygulamalar → **Telefon uygulaması** → Özel Telefon.
Mavi şerit kaybolunca çağrılar bu uygulamanın ekranıyla açılır.

## Inter fontu
Inter fontları `app/src/main/assets/fonts/` içinde hazır.

## APK almak
Build → Build Bundle(s) / APK(s) → Build APK(s) → `app/build/outputs/apk/debug/app-debug.apk`

## Notlar
- Hızlı Arama, rehberde yıldızladığın kişileri gösterir.
- "Görüntülü" düğmesi iOS'taki FaceTime gibi pasif görünür (Android'de karşılığı yok).
- Gelen çağrı bildirimi için Android 14'te "tam ekran bildirim" iznini Ayarlar'dan açman gerekebilir.
