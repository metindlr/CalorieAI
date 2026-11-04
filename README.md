# CalorieAI — Yemek fotoğrafından kalori hesaplayan Android uygulaması

Kotlin · Jetpack Compose · Room · DataStore · Gemini 2.5 Flash (ücretsiz katman) · AdMob + UMP

## Kurulum
1. Android Studio (Ladybug+) ile klasörü aç, Gradle sync'in bitmesini bekle.
2. `local.properties.example` → `local.properties` olarak kopyala, `GEMINI_API_KEY` yaz
   (https://aistudio.google.com/apikey).
3. Çalıştır. Reklamlar varsayılan olarak Google TEST id'leriyle gelir.

## Yayına çıkmadan önce (ZORUNLU)
- AdMob'dan gerçek App ID + 3 reklam birimi oluştur, `local.properties`'e yaz. Test id ile yayınlama.
- Gemini anahtarı APK'dan çıkarılabilir: Cloud Run / Cloudflare Worker / Firebase Functions
  üzerinde ince bir proxy kur, anahtarı orada tut, uygulamaya Firebase App Check ekle.
- Ücretsiz Gemini katmanında girdiler Google tarafından model iyileştirmede kullanılabilir ve
  limitler düşüktür; kullanıcı sayısı artınca ücretli katmana geç. Gizlilik politikana yaz.
- Play Console: Gizlilik politikası URL'si, Data Safety formu, reklam beyanı, "Health" beyanı.
- Uygulama tıbbi cihaz/tavsiye değildir; uyarı metinleri ekranlarda mevcuttur, kaldırma.

## Eklenen büyüme özellikleri
| Özellik | Dosya | Kurulum |
|---|---|---|
| Premium abonelik (reklamsız + sınırsız AI) | `Billing.kt` | Play Console → Abonelikler → ürün kimliği **`premium`**. Lisans test hesabıyla dene. Üretimde satın alımı sunucuda da doğrula. |
| Hatırlatma bildirimleri (kahvaltı, öğle, su, akşam) | `Reminders.kt` | Profil ekranındaki anahtar. Android 13+ izni uygulama ister. |
| Günlük seri 🔥 | `MainVM.kt` | Otomatik. |
| Barkod tarama (Open Food Facts) | `Barcode.kt` | Otomatik; AI kotası harcamaz. |
| Ana ekran widget'ı | `Widget.kt` | Ana ekrana "CalorieAI" widget'ını ekle. |
| Güvenli API proxy | `proxy/worker.js` | `wrangler deploy`, `wrangler secret put GEMINI_API_KEY` ve `APP_TOKEN`; `local.properties`'e `PROXY_URL`/`PROXY_TOKEN` yaz, `GEMINI_API_KEY`'i boş bırak. Cloudflare'de rate limit kuralı ekle. Paylaşılan token da APK'dan çıkarılabilir; ciddi koruma için Play Integrity / Firebase App Check ekle. |
| Crashlytics + Analytics | `app/build.gradle.kts` | Firebase'de proje aç, `google-services.json`'ı `app/` içine koy; otomatik devreye girer. |

Henüz yok: İngilizce/çoklu dil (metinler Kotlin içinde Türkçe sabit, `strings.xml`'e taşınmalı).

## v1.1 – Yeni tasarım ve ek özellikler
**Tasarım:** Zümrüt–petrol gradyan "hero" kart, makro renk kodları, 24 dp yuvarlak kartlar, ortada vurgulu tarama butonlu alt çubuk, koyu mod (Sistem/Açık/Koyu seçilebilir), altın vurgulu Premium kartı. Tüm renkler `Theme.kt` ve `Components.kt` (`Brand`) içinde.

| Özellik | Nerede | Not |
|---|---|---|
| Onboarding (karşılama + hedef kurulumu) | `Onboarding.kt` | İlk açılışta bir kez |
| Makro hedefleri (%25 protein / %45 karb. / %30 yağ) | `Screens.kt` Home | Kalori hedefine göre otomatik |
| Hızlı ekle (son yediklerini tek dokunuşla tekrar kaydet) | Home | Hızlı alışkanlık, yüksek retention |
| Yazarak yemek ekle (AI metinden kalori) | `ScanScreens.kt` | Bir AI tarama hakkı harcar |
| Öğün gruplama (Kahvaltı/Öğle/Akşam/Atıştırmalık) | Home | Ara toplamlarla |
| Kilo takibi + trend grafiği | İlerleme sekmesi | Room v2 (migration dahil) |
| AI Koç haftalık analiz | Koç sekmesi | Ücretsiz günde 1, Premium sınırsız |
| Rapor paylaş (CSV, son 30 gün) | Koç sekmesi | Premium özelliği |
| Tema seçimi | Profil | Sistem / Açık / Koyu |

## v1.2 – Çoklu dil, AI düzeltme, hedef kilo, paylaşım kartı
| Özellik | Dosya | Not |
|---|---|---|
| **Türkçe + İngilizce** | `res/values/strings.xml` (EN, varsayılan), `res/values-tr/strings.xml`, `Locales.kt` | Sistem dilini izler; Profil'den veya ilk ekrandan elle değiştirilebilir. Android 13+ sistem "uygulama dili" ayarını da destekler. AI yanıtları (yemek adı, ipucu, koç) seçili dilde gelir. Yeni dil eklemek: yeni `values-xx/strings.xml` + `locales_config.xml` + `Locales.effective`. |
| **AI ile düzeltme** | `MainVM.correct`, `Gemini.refine`, `ScanScreens.kt` | "Pilav yoktu, et yarım porsiyon" yazınca analiz güncellenir. Kota harcamaz, tarama başına 3 hak. Öğeyi ✕ ile çıkarmak AI çağırmaz, anında hesaplanır. |
| **Hedef kilo + tahmini varış** | `Util.kt` (`forecast`), `Screens.kt` | Son 28 günün kayıtlarına doğrusal regresyon uygulanır (en az 3 kayıt, 7+ gün). Haftada 1 kg'dan hızlı değişimde uyarı gösterir. |
| **Haftalık paylaşım kartı** | `ShareCard.kt` | 1080×1350 PNG üretir, FileProvider ile paylaşır. Alt kısımda uygulama adı yer alır. |

**Not:** Öğün türleri artık veritabanında dilden bağımsız anahtarlarla saklanır (`breakfast/lunch/dinner/snack`); eski Türkçe kayıtlar otomatik eşlenir.
**Play Store:** Mağaza açıklaması, ekran görüntüleri ve anahtar kelimeler her dil için ayrı hazırlanmalı.

## v1.3 – 7 dil
**Desteklenen diller:** Türkçe, English, Español, Português (Brezilya), Deutsch, Français, العربية (Arapça, sağdan sola yerleşim).
- Sistem dilini izler; Profil'den ya da ilk ekrandan değiştirilebilir. Android 13+ "uygulama dili" ayarı da çalışır.
- AI yanıtları (yemek adı, ipucu, koç yorumu) seçili dilde gelir. Hata mesajları, bildirimler, widget ve CSV başlıkları da çevrilidir.
- Arapça: arayüz otomatik aynalanır; grafikler okunabilirlik için soldan sağa sabit tutulur. Arapça/Hint rakamlarıyla girilen kilo değerleri de doğru okunur.
- Yeni dil eklemek: `res/values-xx/strings.xml` (186 anahtar) + `res/xml/locales_config.xml` + `LocaleHelper.LANGS` içine kod/ad satırı + `Gemini.kt` içindeki `LANG_NAMES`.
- **Çeviri kalitesi:** Metinler yapay zekâ ile hazırlandı. Yayına çıkmadan önce her dil için anadili konuşan biriyle kontrol ettir (özellikle sağlık/uyarı metinleri ve mağaza açıklaması).
- Portekizce Brezilya ağırlıklı yazıldı (Portekiz için küçük farklar olabilir).

## v1.4 – Farklılaştıran özellikler
| Özellik | Dosya | Not |
|---|---|---|
| **Sağlık skoru (0–100)** | `Gemini.kt` (şema), `Data.kt` (Meal.healthScore, Room v3 migration), `ScanScreens.kt` | Her öğün için lif/protein dengesi/şeker/doymuş yağ/tuz/işlenmişlik değerlendirilir; günlük ortalama Bugün ekranında. |
| **AI Şef** | `Gemini.suggest`, `MainVM.askChef`, `Screens.kt` (ChefCard) | "Ne yesem?" (kalan kalori+makro), buzdolabı fotoğrafı, restoran menüsü fotoğrafı. Günde 2 ücretsiz istek, Premium sınırsız. Geçici fotoğraf istek sonrası silinir. |
| **Sesle ekleme** | `ScanScreens.kt` | Cihazın konuşma tanıma servisi (RecognizerIntent) → yazıdan analiz. |
| **Aralıklı oruç sayacı** | `Screens.kt` (FastingCard) | 12/14/16/18 saat planı, aşama metinleri, tamamlanan oruç sayacı. Herkes için uygun olmadığına dair uyarı içerir. |
| **Başarımlar** | `Screens.kt` (BadgesCard) | 9 rozet (ilk öğün, 10/100 öğün, 3/7/30 gün seri, 7 kilo kaydı, hedef kilo, ilk oruç). |
Tüm yeni metinler 7 dilde (224 anahtar).
