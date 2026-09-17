# Prompts

The messages the author sent to the Claude Code session that built this project, in order and in their
original language (Turkish), each with a one-line English summary. Only the author's messages are included:
no assistant replies and no tool output. Answers the author gave through the assistant's multiple-choice
questions are not messages; the resulting decisions are recorded in `docs/decisions.md`.

Content that is not about the code was cut and replaced with `[removed: not about the code]`: other companies and
projects, people, the hiring process, local paths, device identifiers and key fragments. Technical content is
unchanged, including advice that later turned out to be wrong; the corrections are in later messages and in
the verification log.

---

## 1. 2026-09-16 15:58 UTC

**Summary:** Hand-off brief: the case text, verified stack, module plan, lessons from an earlier location-tracking app, device test protocol, helper-file and git rules, schedule.

````text
# [removed: not about the code] Android Case — uygulama session'ı

Türkçe konuş. Kod, commit mesajları, README ve dokümanlar İngilizce. Bu prompt ayrı bir session'dan
devrediliyor; "ÖLÇÜLDÜ" etiketli her şey orada build/komutla doğrulandı — yeniden tartışma, ama
şüphelendiğin bir şey olursa sandbox'ta ölçerek kontrol et.

## 0. Rolün ve kurallar

- **[removed: not about the code] AI kullanımı SERBEST ve bekleniyor.** Kodu bu session'da yazabilirsin.
- [removed: not about the code]
  - Kritik kararları yazmadan önce kullanıcıyla kısaca netleştir.
  - [removed: not about the code]
- [removed: not about the code]
- Kullanıcının kendi uygulaması **[removed: not about the code]** ([removed: not about the code], KMP)
  bu case'in neredeyse birebir karşılığını üretimde barındırıyor. **Kodunu değil dersini taşı** (§5).
  [removed: not about the code] kopyalamak (KMP, CMP, 59 modül, convention plugin'ler) over-engineering olur.

## 1. Case (verbatim)

**Amaç:** Kullanıcının konumunu izleyen bir uygulama geliştirmek.
- Kullanıcının konumu izlenmeli ve **her 100 metrelik konum değişikliğinde** haritaya bir marker eklenmeli.
- Konum izleme **ön planda ve arka planda mümkün olduğunca uzun süre** devam etmeli.
- Marker'lar harita üzerinde yer almalı; **marker'a tıklandığında ilgili konuma ait adres** görüntülenmeli.
- Kullanıcı konum takibini **durdurabilir veya başlatabilir.**
- **Rotayı sıfırlama** seçeneği olmalı; rota sıfırlanmadıkça **uygulama yeniden açıldığında mevcut rota görüntülenmeli.**
- İstenen harita sağlayıcısı kullanılabilir.
- Git kullanımına özen gösterilmeli, proje **GitHub reposu** olarak iletilmeli.
- Tasarım adaya bırakılmıştır.
- Case sürecinde AI'dan destek alınabilir; **destek alınırsa tüm yardımcı dosyalar da repoya eklenmeli.**

**Teslim: Cuma 18 Eylül, 18:00.** ([removed: not about the code])

## 2. [removed: not about the code]

## 3. Verilmiş kararlar

| Karar | Gerekçe |
|---|---|
| **Google Maps** (`maps-compose`) | [removed: not about the code]. SDK kimliksiz iner → reviewer anahtarsız da build eder. Mapbox **reddedildi**: SDK indirmek için Gradle download token gerekiyor, reviewer'ın makinesinde build 401 ile patlar |
| **Compose** | [removed: not about the code] |
| **Koin** | [removed: not about the code] |
| **MVI** — tek `StateFlow<State>` + tek `onIntent(Intent)` | [removed: not about the code] |
| **Hafif modüler** | Clean Architecture'ı somut gösterir, 2 günde süre yemez |
| **Room** rota noktaları için, **DataStore** takip oturumu durumu için | Noktalar liste + sorgu; oturum birkaç anahtar |

Önerilen modül yapısı (kullanıcıyla netleştir):
```
:app               Application (startKoin), MainActivity, API key → manifestPlaceholders
:core              SAF JVM — RoutePoint, RouteRepository, TrackingSessionStore, AddressResolver arayüzleri,
                   ve DistanceGate: saf Kotlin haversine + "son kaydedilen noktaya göre ≥100 m mi" — UNIT TEST'Lİ
:data              Room, DataStore, FusedLocation kaynağı, Geocoder impl, repository impl'ler,
                   TrackingService (foreground service) + manifest girişi, Koin dataModule
:feature:tracking  Compose MapScreen, TrackingViewModel (MVI), izin akışı UI, Koin trackingModule
```
`DistanceGate`'in `:core`'da saf fonksiyon olması kasıtlı: case'in **tek iş kuralı** Android'e dokunmadan JVM'de test edilebilir.

## 4. ÖLÇÜLDÜ — doğrulanmış stack

Referans sandbox (5 modül değil 4, yukarıdaki yapı):
[removed: not about the code]
Sonuç: **75 actionable task, 75 executed, 0 FROM-CACHE, BUILD SUCCESSFUL.**

```toml
[versions]
agp = "9.4.0"            # Gradle 9.6.0
kotlin = "2.4.20"
ksp = "2.3.12"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
room = "2.8.5"
datastore = "1.2.1"
playLocation = "21.4.0"
playMaps = "20.0.0"
mapsCompose = "8.6.0"
koin = "4.2.2"           # BOM
coroutines = "1.11.0"
```
Plugin'ler: `com.android.application`, `com.android.library` (ikisi `agp`), `org.jetbrains.kotlin.plugin.compose`
ve `org.jetbrains.kotlin.jvm` (ikisi `kotlin`), `com.google.devtools.ksp` (`ksp`).

**En kritik bulgu — kontrollü deneyle ölçüldü:**
- AGP 9 **gömülü Kotlin** kullanıyor; `org.jetbrains.kotlin.android` plugin'i **eklenmez**. Varsayılan derleyici 2.2.10.
- **`org.jetbrains.kotlin.plugin.compose` 2.4.20 uygulanınca derleyici 2.4.20'ye çıkıyor.**
- Aynı proje `kotlin = "2.2.10"` ile **BUILD FAILED**: `maps-compose-8.6.0` ve `maps-ktx-6.4.1` →
  *"Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0,
  expected version is 2.2.0."* 2.4.20 ile yeşil. **Kotlin sürümünü düşürme.**
- Çözülen kotlin-stdlib: 2.4.20. Build classpath'te KGP 2.4.20.

Diğer ölçülenler:
- `settings.gradle.kts` → `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` şart (`projects.core` sözdizimi için).
- **Compose kullanan library modülü İKİSİNİ de ister:** `alias(libs.plugins.kotlin.compose)` **ve** `buildFeatures { compose = true }`.
  (Doğrulandı: `TrackingScreen(TrackingViewModel, Composer, int, int)` — Compose derleyicisi parametreyi enjekte etti.)
- Room 2.8.5 + KSP 2.3.12 **library modülünde** çalıştı (`RouteDb_Impl.kt` üretildi).
- `:core` → `org.jetbrains.kotlin.jvm` 2.4.20, `jvmTarget` 17; classpath'te "android" satırı **0**; bytecode major 61.
- Java 17 her modülde yalnızca `compileOptions` ile (gömülü Kotlin jvmTarget'ı oradan türetiyor).
- **Maps API key:** `local.properties` içindeki `MAPS_API_KEY`, `app/build.gradle.kts`'te `Properties` ile okunup
  `manifestPlaceholders["MAPS_API_KEY"]`'e veriliyor → merged manifest'te `android:value="probe-key-123"` görüldü.
  Manifest: `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}" />`
- Koin 4.2.2: `module {}`, `single {} bind`, `viewModelOf(::X)`, `koinViewModel()` derlendi.
- Yeni kopya sandbox'ı derlemek `local.properties` (`sdk.dir`) istiyor.

## 5. [removed: not about the code] taşınacak dersler — KODU DEĞİL, KARARLARI

Kaynaklar (okuyabilirsin): [removed: not about the code]
Aşağıdakilerin çoğu **Galaxy S23'te ölçüldü** — tek atışlık AI'ın yanlış yazacağı yerler bunlar:

1. **Neden foreground service, neden WorkManager DEĞİL.** [removed: not about the code] kayıt önce `PeriodicWorkRequest`'ti; Android
   periyodik işi **15 dakika tabanına** kıstığı için bir saatlik sürüşte ~4 nokta çıkıyordu. [removed: not about the code]
2. **Sıra: İZİN → TAKİP → FOREGROUND.** targetSdk 34+'ta `FOREGROUND_SERVICE_TYPE_LOCATION` bildiren
   `startForeground`, konum izni yoksa **fırlatıyor.** İşletim sistemi izni **süreç ölüyken** iptal edebiliyor →
   `START_STICKY` yeniden başlatıyor → tekrar çöküyor. Ölçülen: **2 FATAL, yeniden başlatma 4 sn → 16 sn → ~30 dk.**
3. **Fused API'nin izin hatası ASENKRON.** `requestLocationUpdates` izin yokken **senkron `SecurityException`
   fırlatmıyor**, normal dönüyor (`Task` tabanlı). Yani `try/catch` ya da dönüş değeriyle izin çıkarımı yapılamaz.
   [removed: not about the code] sıra düzeltildikten sonra bile çökme sürdü, sadece adı `ForegroundServiceStartNotAllowedException`
   oldu. **Çözüm: `ContextCompat.checkSelfPermission` ile platforma doğrudan sor.**
   Genel ders: *"Doğrudan sorabileceğin bir olguyu çıkarımla elde etme."*
4. **Arka planda yeniden başlatılan FGS konum tipini hiç alamıyor** (izin olsa bile): *"Foreground service started
   from background can not have location/camera/microphone access."* `startForeground`'u yakala, başarısızsa
   oturumu temizce bitir. *"Uygulama sürekli ölüyor"* → *"kayıt sessizce sona erdi"*: kullanıcı ikincisinden kurtulabilir.
5. **İzin kontrolünde COARSE da sayılır** — platform ikisinden birini kabul ediyor; yalnızca FINE'a bakmak gereksiz yere kaydı bitirir.
6. **`START_STICKY` yeniden başlatmada `intent` NULL gelir.** Devam için gereken her şey kalıcı depodan
   okunmalı, Intent extra'larından asla.
7. **Platformun mesafe filtresi kural değildir.** `setMinUpdateDistanceMeters` bir pil ipucudur. Kural
   uygulamanın kendi kapısıdır ve **son kaydedilen noktaya (çapa)** göre ölçülür, son GPS fix'ine göre değil.
   Fix'ten fix'e ölçülürse GPS sapması gerçek hareket olmadan marker üretir; yavaş yürüyüş ise eşiğin altındaki
   küçük adımlarla hiç tetiklemez. Çapa kalıcı depoda tutulur ki süreç ölümünden sonra da doğru ölçülsün.
8. **`PRIORITY_HIGH_ACCURACY` gerekçesi:** balanced güç modunun doğruluğu ~100 m — eşiğin kendisine eşit, yani
   gürültü sinyale eşit olur. Interval (10 sn / en hızlı 5 sn) bir **pil tabanıdır**, takvim değil.
9. **`onDestroy`'da önce location callback'i kaldır.** Sızan callback, bitmiş bir kayıt için GPS'i açık tutar —
   görünür sebebi olmayan pil tüketimi.
10. **Force-stop nüansı — [removed: not about the code]:** `START_STICKY` sistemin öldürdüğü servisi yeniden
    başlatır ama **kullanıcının force-stop ettiğini başlatmaz.** Doğrulanabilir olan: veri kurtulur ve yeniden
    açılınca rota görünür. Takip kendiliğinden devam **etmez.** README'de dürüstçe yaz.
11. **Ters geocoding** ([removed: not about the code]): `Geocoder.isPresent()` kontrolü; API 33+'ta
    `GeocodeListener` + `suspendCancellableCoroutine`, altında IO dispatcher'da blocking çağrı; `withTimeoutOrNull`;
    `CancellationException`'ı yeniden fırlat; çevrimdışı / rate-limit → `null` (uyarı değil).
12. **Adres ne zaman çözülür — offline-first cevap:** nokta kaydedilirken best-effort çöz ve **kaydet**;
    marker'a tıklandığında adres yoksa tekrar dene ve sonucu kaydet. Kararı README'de yaz.
13. **EKLEME:** [removed: not about the code] 20 dakikalık "zaman backstop"u var (hareketsizken zorla nokta). **Bu case'e koyma** —
    spec "her 100 metrede" diyor; zamanla nokta üretmek spec'i ihlal eder.

**İzin merdiveni:** `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (runtime), `POST_NOTIFICATIONS` (13+,
FGS bildirimi için), manifest'te `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` (14+),
`<service android:foregroundServiceType="location" android:exported="false"/>`. **Önemli ve sık yanlış anlaşılan:**
ön plandayken başlatılmış `location` tipli bir foreground service, uygulama arka plana geçince de konum almaya
devam eder — bunun için `ACCESS_BACKGROUND_LOCATION` **şart değildir** (o, FGS olmadan arka plandan konuma erişim
içindir). Arka planda yeniden başlatma senaryosuyla etkileşimini **cihazda doğrula**; tahminle yazma.

## 6. Cihaz doğrulama protokolü (teslimden önce hepsi yapılacak)

- **Hareket simülasyonu:** yürümeden 100 m testi için emülatörde Extended Controls → Location → Routes (GPX) ya da
  `adb emu geo fix <lng> <lat>` ardışık çağrıları. Fiziksel cihaz: [removed: not about the code]
- Takip sırasında **uçak modu** → noktalar yine kaydediliyor mu (GPS ağ istemez), adres boş kalıp sonra çözülüyor mu.
- **`adb shell am force-stop <paket>`** takip ortasında → yeniden aç → rota görünüyor mu. (Takibin devam etmemesi beklenen.)
- **Sistem öldürmesi:** uygulama arka plandayken `adb shell am kill <paket>` → `START_STICKY` ile servis dönüyor mu.
- Takip sürerken **Ayarlar'dan konum iznini iptal et** → çökme döngüsü YOK, oturum temizce bitiyor.
- **Ekran döndürme** → mükerrer marker yok, takip yeniden başlamıyor.
- Takip açıkken **son uygulamalardan kaydırarak kapat** → FGS yaşıyor mu.
- **Rotayı sıfırla** → DB + çapa temizleniyor; takip sürerken sıfırlanırsa davranış ne — karar ver ve belgele.
- Sonuçları okurken logu oku, exit code'a güvenme; "yeşil" bir build'in işi gerçekten çalıştırdığını doğrula.

## 7. AI yardımcı dosyaları — teslimin parçası, sonradan eklenecek bir şey değil

Case şartı: AI desteği alındıysa **tüm yardımcı dosyalar repoya.** [removed: not about the code]
- `CLAUDE.md` (kök): mimari, modül sınırları, komutlar, doğrulanmış stack notları.
- `docs/decisions.md`: her karar + **elenen alternatif** + **ölçülmüş kanıt** (Kotlin 2.4.20 deneyi, maps-compose
  metadata hatası, S23 tuzakları, Mapbox reddi). Bu dosya *"AI kod yazmayı hızlandırır, doğrulamayı değil"*
  cümlesinin somut hâli.
- Kullanılan prompt'lar (bu prompt dahil) — kullanıcıyla netleştir.
- **Hafif tut.** [removed: not about the code] 59 SKILL.md'yi taklit etme.
- **Dışarıda bırak:** `local.properties`, API key, `.claude/settings.local.json` gibi yerel yollar/izinler içeren dosyalar.

## 8. Git

- Küçük, anlamlı Conventional Commits; zamana yayılmış.
- **API key ve `local.properties` asla commit'lenmez.** Teslimden önce `git ls-files` + key'in kendisi için grep ile doğrula.
- `.gitignore`: `local.properties`, `.idea/`, `build/`, `.DS_Store`, `.claude/settings.local.json`.
- Commit attribution (`Co-Authored-By`) — [removed: not about the code];
  **kullanıcıya sor.** ([removed: not about the code])
- [removed: not about the code]
  Public/private + davet kararını kullanıcıya sor.
- Anahtarsız klonlayan reviewer gri harita görmesin: key boşsa uygulama içinde net bir mesaj göster.

## 9. README

Kurulum (`MAPS_API_KEY`), modül grafiği, 100 m kuralı (çapa = son kaydedilen nokta), arka plan davranışı **ve
dürüst sınırları** (force-stop, OEM pil öldürücüleri, Doze — [removed: not about the code]), kalıcılık modeli, adres çözme stratejisi, test protokolü (GPX), kararlar ve elenen alternatifler,
**AI kullanımı bölümü** (neyi AI yaptı, neyi cihazda / elle doğruladın).

## 10. Takvim

Başlangıç **Çarşamba 18:45**, teslim **Cuma 18:00**. [removed: not about the code]
- **Çar akşamı:** proje + doğrulanmış stack + modüller + harita render + izin akışı iskeleti
- **Per:** TrackingService (§5 sırası) + DistanceGate (+ unit testler) + Room + DataStore oturumu + başlat/durdur/sıfırla + ters geocoding
- **Cum sabah:** uç durumlar, README, `docs/decisions.md`, yardımcı dosyalar
- **Cum 14:00–16:00:** §6 protokolünün tamamı + tampon
- **Hedef teslim Cuma 16:00**, son sınır 18:00

## 11. [removed: not about the code]

## 12. İlk adımlar

1. Kullanıcıyla §3 modül yapısını ve §8'deki iki soruyu (attribution, repo görünürlüğü) netleştir.
2. Android Studio'da yeni proje ("Empty Activity" = Compose). §4 sürümlerini uygula; ilk build'i logdan oku.
3. `DistanceGate`'i önce yaz ve testle — case'in tek iş kuralı, JVM'de dakikalar içinde doğrulanır.
4. `TrackingService`'i §5 sırasıyla yaz ve **§6'nın izin iptali ile force-stop maddelerini ilk günden** dene.

## 13. Doğrulanmadı

Uygulama **hiç çalıştırılmadı** — FGS, konum akışı, geocoding, Google Maps render'ı runtime'da ölçülmedi ·
Secrets Gradle Plugin denenmedi (sürüm sorgusu boş döndü; yerine `Properties` okuma kullanıldı) ·
`local.properties` doğrudan okunduğu için `MAPS_API_KEY` değişikliğinin configuration cache'i geçersiz kılıp
kılmadığı ölçülmedi · `ACCESS_BACKGROUND_LOCATION` ile arka planda yeniden başlatma etkileşimi ölçülmedi
(§5 izin merdiveni) · [removed: not about the code] S23 ölçümleri [removed: not about the code]; bu projede cihazda tekrarlanmalı.
````

---

## 2. 2026-09-16 16:24 UTC

**Summary:** Re-asks for a tool permission that was denied by mistake.

````text
izni yanlış verdim bidaha sor
````

---

## 3. 2026-09-16 16:46 UTC

**Summary:** Review 1: camera never centers on a new route, gate tests cannot catch a wrong anchor, record must be atomic, check mock-location accuracy, write docs, record the demo.

````text
REVIEW BULGULARI (ayrı bir review session'ından, bağımsız ölçümle):

Doğrulandı: assembleDebug 79/79 executed, :core 16/16 test taze XML ile yeşil. Commit'ler ve kararlar iyi.

Yarının sırası:
1. P1 BUG — TrackingScreen'deki LaunchedEffect(state.isLoading) yalnızca bir kez çalışıyor. Temiz kurulumda
   rota boşken çalışıp biter; sonra noktalar gelse de isLoading değişmediği için kamera ASLA rotaya ortalanmaz.
   Effect'i ilk noktanın varlığına / son noktanın id'sine bağla. Ayrıca isMyLocationEnabled ile kullanıcının
   konumunu göster (izin varken).
2. P1 — DistanceGateTest'teki "slow walk" ve "GPS jitter" testleri her çağrıda sabit `origin`'i anchor veriyor;
   anchor'ı doğru seçmek çağıranın işi olduğu için bu testler, servisin yanlış anchor seçmesini YAKALAYAMAZ.
   :core'a saf bir RecordFixUseCase(repository, gate) çıkar: son noktayı oku → değerlendir → Record ise yaz.
   Sahte repository ile gerçek bir fix dizisi ver, anchor'ın yalnızca Record'da ilerlediğini test et.
3. P2 — "anchor oku → değerlendir → yaz" ATOMİK olmalı. LocationResult.locations birden fazla fix'i toplu
   getirebilir; aksi hâlde aralarında <100 m olan iki marker oluşur. Room @Transaction ya da tek yazıcı.
   Servisi yazarken ilk karar bu.
4. P2 — accuracyMeters == null reddediliyor. İlk emülatör testinde `adb emu geo fix` / GPX sahte konumlarının
   doğruluk değeri taşıdığını doğrula; taşımıyorsa test sırasında hiçbir şey kaydedilmez. 50 m eşiği
   ölçülmedi, akıl yürütüldü — README'de takas olarak yaz.
5. P2 — README / CLAUDE.md / docs/decisions.md hiç yok. Bu akşamki ölçülmüş kararları (config cache doğrulaması,
   gate mutasyon testi, 50 m gerekçesi, Kotlin 2.4.20) ŞİMDİ decisions.md'ye yaz.
6. P1 — Reviewer'ın Maps key'i olmayacak, haritayı gri görecek. README'ye çalışan uygulamanın EKRAN KAYDI
   ve ekran görüntüleri zorunlu: başlat → hareket → marker → dokun → adres → kapat-aç → rota duruyor.
7. P3 — manifest'te android:icon yok; GitHub remote yok.

NOT: "tam 100.0 m sınırı test edilmemiş" diye bir boşluk DEĞİL — haversine tam 100.0 değil 99.99999999969069
döndürüyor, 99.5/100.5 testleri doğru tercih. Buna dokunma.

Uygulama henüz hiç cihazda çalıştırılmadı — servis bitince ilk iş §6 protokolünü emülatörde başlat.
````

---

## 4. 2026-09-16 17:09 UTC

**Summary:** Review 2: RouteDao.last() is untested (DESC→ASC passes all tests), a Recorded point may already be deleted so addresses must be written with UPDATE; plan for the service day.

````text
İKİNCİ REVIEW (bağımsız ölçümle). Genel: bu tur çok iyi — bulgular doğru kapatılmış, decisions.md
[removed: not about the code]. Doğrulandı: 88/88 task, 25/25 test taze; kilit mutasyonu 5/5 koşuda deterministik
kırıldı; anchor=null mutasyonu 6 test kırdı; kamera düzeltmesi ve sıfırlama analizi doğru.

BU AKŞAM — toplam ~5 dk, sadece doküman:
1. P1 — RouteDao.last() hiç test edilmemiş. Ölçüldü: "ORDER BY id DESC LIMIT 1" → "ASC" yapıldı (çapa =
   ilk nokta), :data derlendi, 25 testin 25'i GEÇTİ. Use case testleri fake kullandığı için gerçek DAO
   görünmez. Gerçek etkisi: başlangıçtan 100 m sonra her 5 sn'de bir marker. Doğrulama günlüğündeki M4
   ("anchor on the first point → caught") yalnızca use case/fake katmanında geçerli; o satıra bunu not düş
   ve decisions.md'ye boşluğu dürüstçe ekle.
2. P2 — D14'e kısıt ekle: Recorded(point) sonucu, eşzamanlı bir sıfırlamanın sildiği bir satırı
   gösterebilir (oku → yaz → sıfırla sırası). Adres yazımı YALNIZCA "UPDATE route_points SET address=? WHERE
   id=?" olmalı. @Upsert/@Insert kullanılırsa sıfırlanmış nokta geri dirilir.

YARIN — KURAL: :core'a yeni iş yok. Sıra:
1. İlk iş emülatörde: `adb emu geo fix` / GPX sahte konumları accuracy taşıyor mu (D12 bekleyen kontrol).
   Taşımıyorsa test stratejisi değişir.
2. TrackingService + izin akışı. CLAUDE.md'deki sıra (izin → startForeground → konum güncellemeleri),
   prompt'taki ölçülmüş kaynaktan (izin → takip → foreground) farklı ve gerekçesi yazılmamış. Sıran
   savunulabilir, hatta daha basit olabilir — ama D17 olarak gerekçesiyle belgele. Review'un en derin
   kazılacağı yer burası.
3. Başlat/durdur/sıfırla + DataStore oturumu. 4. Geocoding (yukarıdaki UPDATE kısıtıyla).
5. Akşam §6 protokolü emülatörde; emülatör açıkken RouteDao için tek bir androidTest (in-memory Room:
   3 nokta ekle, last() 3.'yü dönsün, deleteAll boşaltsın) — sonra DESC→ASC mutasyonunu tekrar et, kırılsın.

KÜÇÜK: CLAUDE.md henüz var olmayan DataStore/fused/geocoder/servis'i anlatıyor — teslimde kodla eşleşmeli.
GitHub push'una kullanıcı evet diyecek; gerekçe "commit'ler gün gün birikir" değil (commit'ler zaten kendi
tarihini taşır) — gerçek sebepler: makine dışı yedek + Cuma günü temiz klondan build testi.
````

---

## 5. 2026-09-16 17:42 UTC

**Summary:** Review 3: a restarted service may be refused location foreground access; justify the start order from a user-initiated start and end the session cleanly on refusal.

````text
ÜÇÜNCÜ REVIEW. Bu akşamki iş doğru ve tam: D10 boşluğu + M4 kapsamı, D14 kısıtı, CLAUDE.md eşleşmesi,
private push (0/0 senkron), gizli bilgi taraması temiz ([removed: not about the code]: 0). "stored route matches" düzeltmen isabetli.

YARIN D17 İÇİN — [removed: not about the code] şu soruyla oku:
Son mesajındaki gerekçe ("yeniden başlatılan servis konumu ancak location tipli FGS olduktan sonra
alabiliyor, bu yüzden güncellemeler startForeground'dan sonra") ölçülmüş kaynakla çelişiyor. [removed: not about the code]
S23'te (Android 16) şunu ölçmüş: arka planda başlatılan bir FGS location tipini HİÇ alamıyor, izin ne olursa
olsun — "Foreground service started from background can not have location/camera/microphone access" ve
"Service.startForeground() not allowed due to mAllowStartForeground false". Yani START_STICKY yeniden
başlatmasında "location FGS olduktan sonra konum iste" senaryosu o cihazda gerçekleşmiyor.

Sonuçları:
1. Sıran (izin → startForeground → güncellemeler) hâlâ savunulabilir — ama gerekçesini kullanıcı başlatması
   (uygulama ön planda) üzerinden kur; yeniden başlatma üzerinden değil.
2. startForeground'ı yakala; reddedilirse oturumu temizce bitir. Rota Room'da kalır, takip kalmaz.
3. README "dürüst sınırlar": "mümkün olduğunca uzun" = çalışırken FGS ile süreci ayakta tutmak; sistem
   süreci öldürürse takip devam ETMEZ, rota korunur. Eski Android sürümlerinde davranış farklı olabilir —
   cihazda doğrulanmadıysa "Device check pending" etiketle.
4. [removed: not about the code]

Yarın sırası aynı kalıyor: emülatörde accuracy kontrolü → servis + izinler (D17) → başlat/durdur/sıfırla +
DataStore → geocoding (UPDATE) → akşam §6 + RouteDao androidTest + DESC→ASC tekrarı.
````

---

## 6. 2026-09-16 18:19 UTC

**Summary:** Review 4: am kill does not kill a process with a foreground service; use startService rather than startForegroundService; plan two skills (verify-claim, device-check) and sanitized prompts.

````text
DÖRDÜNCÜ REVIEW. Üçüncü review'a cevabın doğru: kaynağı satır numarasıyla doğruladın, gereksiz değişiklik
yapmadın, D17 planın doğru.

[removed: not about the code]

ŞÜPHE (a) — haklısın, §6'daki `adb shell am kill` önerisi hatalıydı: yalnızca "öldürülmesi güvenli"
süreçleri öldürür, FGS'li süreç o grupta değil. Yol: "Google APIs" imajlı emülatör (Play services var,
root'a izin verir; "Google Play" imajları vermez) → adb root → adb shell kill -9 <pid>.

ŞÜPHE (b) — [removed: not about the code] somut cevabı var: [removed: not about the code] `context.startService(intent)`,
startForegroundService() DEĞİL. "did not then call startForeground" sözleşmesi yalnızca
startForegroundService() ile gelir. Uygulama ön plandayken startService() + onStartCommand içinde
startForeground ile yükseltme → izin yoksa stopSelf() hiçbir sözleşmeyi ihlal etmez ([removed: not about the code] bu
çökmenin kaydı yok). startForegroundService() ise çıkmaz: zaman aşımından önce startForeground zorunlu,
ama targetSdk 34'te location tipli startForeground izinsiz fırlatır. Ek (Android dokümanı, ölçülmedi):
kullanıcı çalışma zamanı iznini iptal edince sistem süreci öldürür → aynı süreçte yarış pratikte yok.
D17'ye bu kararı gerekçesiyle yaz. `adb shell pm revoke <paket> android.permission.ACCESS_FINE_LOCATION`
ile izin iptali Ayarlar'a girmeden test edilebilir.

AI YARDIMCI DOSYALARI — plan:
1. Modül başına skill YAZMA — bu session kullanmadı, sonradan eklemek uydurma olur.
2. İKİ skill, gerçekten tekrar eden prosedürler için, .claude/skills/ altında (frontmatter'lı):
   - verify-claim: test-results sil (find -prune -exec rm; zsh glob'u eşleşmezse rm'i iptal eder, 0 kaldığını
     doğrula) → --rerun-tasks --no-build-cache → sonucu logdan oku, exit code'dan değil → N executed, 0
     FROM-CACHE → JUnit XML'i parser'la oku, mtime taze mi → mutasyon: grep tam 1 eşleşme → uygula → derlendi
     mi → çalıştır → kopyadan geri yükle → md5 eşit → git status temiz. Sıfır, dedektörün ateşleyebildiği
     gösterilmeden kanıt değil; bir probe ancak orijinal kodda geçiyorsa sayılır (99.99999999969069 dersi).
   - device-check: YARIN CİHAZ İŞİNDEN ÖNCE yaz, sonra kullan. Google APIs imajı · adb emu geo fix / GPX +
     accuracy var mı · sistem öldürmesi adb root + kill -9 (am kill DEĞİL) · am force-stop (STICKY dönmez) ·
     pm revoke · döndürme · son uygulamalardan kaydırma · logcat'te FATAL / ForegroundService istisnaları ·
     sonuçları decisions.md doğrulama günlüğüne.
3. Cuma: docs/ai/ altına prompt'ların ARINDIRILMIŞ hâli + README "AI kullanımı" bölümü.

[removed: not about the code]
````

---

## 7. 2026-09-16 18:51 UTC

**Summary:** Review 5: run verify-claim steps in one shell call; instrumented test results may land outside build/test-results.

````text
BEŞİNCİ REVIEW. verify-claim skill'i çok iyi — her kuralı günlükteki gerçek bir olaya dayanıyor.
Doğrulandı: skill'i tek komutta uçtan uca çalıştırdım (0 → 4/4 executed, 0 FROM-CACHE, 3 taze, 25/25,
stale 0, exit 0). D8 doğru ve doğru kapsamlanmış. Bağımsız arındırma taraması: 29 terim, ağaç + tüm
geçmiş, hepsi 0; pozitif kontroller ateşledi; .claude/ yalnızca iki skill dosyası. Anahtar kelime
listesini repoya koymaman isabetli.

İKİ KÜÇÜK DÜZELTME (yarın skill'i kullanmadan önce):
1. Skill tek shell oturumu varsayıyor. Claude Code'da her Bash çağrısı yeni shell; $START ve $LOG
   kalmıyor. Ölçüldü: junit_summary.py "" → ValueError, exit 1 (yüksek sesle, yanlış sonuç yok).
   SKILL.md'ye ekle: "1–4. adımları tek bir Bash çağrısında çalıştır."
2. Yarınki RouteDao androidTest'i skill'e görünmeyebilir: silme adımı ve script glob'u yalnızca
   build/test-results'a bakıyor; instrumented test sonuçları AGP'de başka klasöre yazılıyor olabilir.
   ÖLÇÜLMEDİ (referans projede diskte androidTest sonucu yoktu). Test ilk koştuğunda XML'in nereye
   düştüğüne bak, skill'in silme adımını ve glob'unu o yolu kapsayacak şekilde genişlet, günlüğe yaz —
   DESC→ASC mutasyon tekrarı ancak ondan sonra geçerli sayılır.
````

---

## 8. 2026-09-17 08:30 UTC

**Summary:** The Maps API key is ready and restricted; record the demo with a debug build; explain in the README how reviewers add their own key.

````text
Maps API key hazır ve local.properties'te: yalnızca Maps SDK for Android'e ve com.erolgizlice.routetracker +
debug SHA-1'e kısıtlı, bütçe alarmı kurulu. Sonuç: cihaz testlerinde harita çalışır; Cuma'daki ekran kaydı
DEBUG build ile yapılmalı (release/başka makine = gri harita). README'de reviewer'ın kendi key'ini nasıl
ekleyeceğini yaz — bizim key paylaşılmıyor.
````

---

## 9. 2026-09-17 09:25 UTC

**Summary:** Check the emulator image yourself; from now on commit but do not push.

````text
imajı sen kontrol et. indiyse testlere başlayabilirsin. bir de bundan sonra commit ettikten sonra push atma çünkü atılan kodları incelemek istiyorum.
````

---

## 10. 2026-09-17 11:24 UTC

**Summary:** Review 7: D17 contradicts itself about restarts; use this project's measurements in the README; drop fixes queued after Stop; plan the demo around address lookup timing.

````text
YEDİNCİ REVIEW. Çok iyi tur — çekirdek gereksinimlerin hepsi cihazda ölçülmüş, dört geçersiz deneme dürüstçe
tekrarlanmış. Bağımsız doğrulandı: :data test APK'sı yalnızca emülatörde (S23'e dokunulmadan) orijinalde 6/6,
DESC→ASC ile tam olarak lastReturnsTheMostRecentlyRecordedPoint ve
routeIsOrderedByRecordingEvenWhenTheClockWentBackwards kırıldı, geri yüklemede 6/6. Arındırma 22 terim 0
(cihaz seri numarası dahil). D14 UPDATE ve D17 startService kodda doğru. Push edilmemiş 3 commit onaylı —
kullanıcı push edecek.

DÜZELTMELER:
1. D17 kendi içinde çelişiyor: "location tracking never restarts without the user asking" ↔ tablo, sistem
   öldürmesinden sonra servisin kendiliğinden döndüğünü gösteriyor. Ayrımı net yaz: sistem öldürmesinde aynı
   oturum devam eder; force-stop/yeniden başlatmada (servis sağ kalmadığında) oturum sonraki açılışta
   kapatılır ve takip kendiliğinden başlamaz.
2. README "dürüst sınırlar" BU PROJENİN ölçümünü kullanmalı: sistem öldürmesinde takip DEVAM ETTİ (4/4,
   S23 Android 16); reddetme yolu yazıldı ama bu projede tetiklenmedi. "Takip kalmaz" ifadesi [removed: not about the code];
   burada tekrar etmedi — kullanma. Doze, OEM pil yönetimi ve API 26–32 "Device check pending".
3. Düşük öncelik: record() oturum aktifliğine bakmıyor; Stop'tan sonra kuyrukta kalan bir fix kaydedilebilir
   (pencere ms, en fazla 1 nokta). Tek satır kontrol ekle ya da decisions'a bir cümle yaz.
4. [removed: not about the code]
5. Cuma ekran kaydı için: D21'deki "kayıt anında adres alamayan noktalar" nedeniyle bazı marker'lar önce
   "Looking up address…" gösterebilir — kayıt senaryosunu buna göre planla.
````

---

## 11. 2026-09-17 11:35 UTC

**Summary:** Review 8: document that Stop/Start joins the route with a straight line; the dropped-fix flag narrows but does not close the window.

````text
SEKİZİNCİ REVIEW. İki düzeltme doğru: acceptingFixes @Volatile, endSession'da ilk satır, start'ta
güncellemelerden önce; D17'nin üç durumu net ve çelişki çözülmüş. Arındırma temiz. Co-Authored-By 18/18
commit'te tutarlı — README'deki AI bölümü buna atıf yapsın. Kullanıcı 2 commit'i push edecek.

İKİ NOT:
1. README "dürüst sınırlar"a ekle: Stop/Start aynı rotayı sürdürür; durdurulmuşken kat edilen yol düz bir
   çizgiyle birleşir (günlükteki 144 m satırı bunu gösteriyor). Segmentlere ayırmak nokta başına oturum
   kimliği + şema migration'ı ister, kapsam dışı. Ekran kaydında bu sahneyi ya açıklayarak göster ya da
   kayıt sırasında durdurup yer değiştirme.
2. [removed: not about the code]
   acceptingFixes pencereyi daraltır ama recordFix'in içindeki bir fix
   yine tamamlanır; tamamen kapatmak kontrolü kilidin içine almayı gerektirirdi, ms'lik tek nokta için
   değmedi.
````

---

## 12. 2026-09-17 11:55 UTC

**Summary:** Delivery brief: app icon, keyless screenshot, emulator demo recording, README, docs/ai, fresh-clone build check.

````text
Teslim fazı. Hedef: Cuma 18 Eylül 16:00'dan önce push edilmiş, teslime hazır repo. Yeni özellik yok; sadece aşağıdaki 7 iş, bu sırayla. Her adımın sonunda commit at (Conventional Commits). Push etme, push'u ben yapacağım.

GENEL KURALLAR
- Her adb komutu `-s emulator-5554` ile. Telefon bağlı kalabilir. `./gradlew installDebug` bağlı TÜM cihazlara kurar, kullanma. Kurulum: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`.
- Fiziksel cihazdan hiçbir görsel veya log repoya girmez. Medya sadece emülatörden, `adb exec-out screencap -p` ve `adb shell screenrecord` ile alınır. macOS ekran görüntüsü kullanma.
- API key ekrana hiç basılmaz. Kontrol sadece uzunluk ve sha256 ile yapılır.
- README'deki her sayı ya decisions.md'deki bir ölçümden gelir (D numarasıyla referans ver) ya da bugün yeniden ölçülür. Etiketi "Device check pending" olan bir şeyi README'de "çalışıyor" diye yazma. "production-ready" gibi ifadeler yok.

1) UYGULAMA İKONU (timebox 20 dk)
- minSdk 26 olduğu için adaptive icon yeterli: `mipmap-anydpi-v26/ic_launcher.xml` ve `ic_launcher_round.xml`. Vector foreground (basit bir rota/pin glifi), düz renk background, API 33 themed icon için `<monochrome>`.
- Manifest'e `android:icon` ve `android:roundIcon` ekle.
- Emülatör launcher'ında ikonun göründüğünü screencap ile kontrol et. Bu görselin repoya girmesi gerekmiyor.

2) D5: KEY'SİZ EKRAN GÖRÜNTÜSÜ
- `local.properties` yedeğini REPO DIŞINA al (`mktemp -d`). Repo içinde `.bak` bırakma: gitignore'da değil, commit'e girer.
- Orijinalin sha256'sını kaydet. MAPS_API_KEY satırını geçici olarak kaldır, `assembleDebug` al, emülatöre kur.
- Missing-key kartını `docs/media/missing-key.png` olarak kaydet.
- Aynı build'de "takip ve kayıt yine çalışır" iddiasını da ölç: Start ver, aralarında >100 m olan iki geo fix gönder, kaydın oluştuğunu göster.
- local.properties'i yedekten geri koy, sha256'nın eşit olduğunu doğrula. Key'li build'i tekrar kur, haritanın geldiğini gör.
- D5'in etiketini Measured yap, verification log'a ekle.

3) EKRAN KAYDI (emülatör, uydurma rota)
- Debug build şart: key bu makinenin debug SHA-1'ine kısıtlı.
- Rota kamuya açık olsun ve benimle kişisel bağı olmasın: İstiklal Caddesi, Taksim → Galatasaray, yaklaşık 700 m. Koordinatları haritadan al.
- `adb -s emulator-5554 emu geo fix <BOYLAM> <ENLEM>`: önce boylam gelir. Sırayı ters verirsen nokta başka yere düşer.
- Location request 10 s aralıklı, en kısa 5 s. Fix hızını buna göre ayarla. Daha önce ölçtüğün 480 m yürüyüş script'ini temel al. Marker aralığı videoda 100–150 m görünmeli.
- screenrecord 180 s ile sınırlı. İki klip çek, toplam ≤ 20 MB olsun (`--bit-rate` ile ayarla, `ls -lh` ile ölç). Kayıttan önce POST_NOTIFICATIONS iznini ver.
  - Klip 1 (≤ 120 s): temiz kurulum → izinler (precise) → Start → yürüyüş, marker'lar belirir → HOME, yürüyüş sürer, bildirim gölgesinde görünür → uygulamaya dön, arkadayken eklenen marker'lar orada.
  - Klip 2 (≤ 90 s): marker'a dokun → adres (D21: önce adresin çözülmesini bekle, sonra dokun) → Stop → recents'tan kaydırıp kapat → tekrar aç, rota duruyor → Reset → rota temiz.
  - Stop'tan sonra konumu oynatma (D22: sonraki Start noktaları düz çizgiyle bağlar). Oynatman gerekirse README'de açıkla.
- Klipleri `docs/media/` altına koy. README'den link ver, 2–3 kareyi PNG olarak inline koy. Metne "recorded on an emulator with a scripted route" yaz.
- Klipleri COMMIT ETMEDEN önce bana haber ver. Baştan sona izleyeceğim: ekranda veya bildirim gölgesinde hesap adı, e-posta ya da gerçek konum olmamalı.

4) README.md (İngilizce)
 a. Ne yaptığı (1 paragraf), ekran görüntüleri, video linkleri.
 b. Gereksinim → implementasyon tablosu: her gereksinimin sınıfı ve D numarası (100 m marker, ön plan/arka plan takibi, marker'a dokununca adres, start/stop, reset, yeniden açınca rotanın görünmesi).
 c. Kurulum:
    - Android Studio sürümünü bu makineden ölç (`/Applications/Android Studio.app/Contents/product-info.json`), tahmin etme.
    - JDK notunu 6. adımdaki clean clone'da doğruladıktan sonra yaz (gradle-daemon-jvm.properties, toolchain 25).
    - Kendi Maps key'ini oluşturma: Maps SDK for Android'e ve paket adı + kendi debug SHA-1'ine kısıtla. SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`. Ardından `local.properties` içine `MAPS_API_KEY=...`.
    - Key yoksa: build geçer, takip ve kayıt çalışır, haritanın yerinde açıklama kartı görünür (missing-key.png).
 d. Build ve test komutları. Test sayılarını bugün verify-claim skill'iyle taze ölç, sonra yaz.
 e. Mimari: modül grafiği, MVI + Koin, Room + DataStore, foreground service. Ayrıntılar için decisions.md'ye link.
 f. 100 m kuralı: çapa son kaydedilen nokta; accuracy > 50 m olan veya accuracy'si olmayan fix reddedilir; haversine; tek yazıcı ve mutex.
 g. Arka plan davranışı ölçüm tablosu (senaryo / sonuç / cihaz / D numarası): başlatma, HOME, 4 sistem kill'i (1–16 s içinde yeniden başladı, takip sürdü), izin geri alma (oturum biter, bildirim gelir), force-stop (yeniden başlamaz; açılışta bilgi verilir, rota durur), bildirimden Stop, rotasyon, recents'tan kapatma. Cihaz adları: "Samsung Galaxy S23, Android 16" ve "API 33 emulator". Seri numarası yazılmaz.
 h. Bilinen sınırlar, dürüst biçimde: force-stop ve reboot sonrası takip kendiliğinden başlamaz; Doze, OEM pil yönetimi, uzun süreli arka plan ve API 26–32 cihazda test edilmedi; stop/start arası düz çizgi (D22); kötü accuracy'li fix'ler bilerek atılır, kapalı alanda boşluk olabilir; adres Geocoder'a ve ağa bağlı (D21); oturum biterken kuyruktaki fix'ler atılır (D17); ACCESS_BACKGROUND_LOCATION neden istenmiyor.
 i. AI kullanımı: araç ve model (Claude Code, Claude Opus 5). İş akışı: prompt → plan → implementasyon → verify-claim / device-check skill'leri → commit → her milestone'da ikinci bir Claude Code session'ı iddiaları bağımsız olarak yeniden ölçtü → düzeltme. İnsanda kalanlar: kararlar, cihaz başındaki fiziksel testler, her commit'in incelenmesi. Tüm commit'lerde Co-Authored-By satırı var. Yardımcı dosyalar: CLAUDE.md, .claude/skills/*, docs/decisions.md, docs/ai/.

5) docs/ai/
- `docs/ai/README.md`: iş akışını ve her yardımcı dosyanın ne işe yaradığını tek sayfada anlat.
- `docs/ai/prompts.md`: bu session'a gönderdiğim mesajlar; kronolojik, orijinal dilinde (Türkçe), her birinin başında 1 satırlık İngilizce özet. Kaynak bu session'ın transcript'i ([removed: not about the code]). Sadece benim mesajlarım, tool çıktıları yok.
- Sanitizasyon: teknik içerik aynen kalır. Şunlar çıkarılır ve yerlerine `[removed: not about the code]` yazılır: başka şirket ve proje adları, kişi adları, işe alım süreci ve görüşme notları, adayla ilgili değerlendirmeler, [removed: not about the code], koçluk etiketleri ve "30 saniyelik cevap" blokları, cihaz seri numarası, yerel dosya yolları, API key parçaları.
- Bir kesinti teknik anlamı değiştirecekse kesme, önce bana sor.

6) CLEAN CLONE
- Commit'lerden sonra: `git clone <bu repo yolu> "$(mktemp -d)/rt"`. Bu, push öncesi yerel commit'leri test eder.
- Clone'a sadece `sdk.dir` içeren bir local.properties koy, key koyma.
- `./gradlew assembleDebug :core:test --no-build-cache` çalıştır. Log'dan BUILD SUCCESSFUL'u ve test sayısını oku. Üretilen BuildConfig'te `HAS_MAPS_API_KEY = false` olduğunu kontrol et.
- Build sonrası clone'da `git status --porcelain` boş olmalı (gitignore'da eksik yok demek).
- Ben push ettikten sonra: `git rev-parse HEAD` ile `git ls-remote origin main` eşit mi, kontrol et. GitHub'da video linklerinin oynayıp oynamadığına bak.

RAPOR: commit listesi; README'deki her sayının kaynağı (D numarası veya bugünkü ölçüm); clean clone log özeti; medya dosya boyutları.
````
