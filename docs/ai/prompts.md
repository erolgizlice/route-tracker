# Prompts

The messages the author sent to the Claude Code session that built this project, in order and in their
original language (Turkish), each with a one-line English summary. Only the author's messages are included:
no assistant replies and no tool output. Answers the author gave through the assistant's multiple-choice
questions are not messages; the resulting decisions are recorded in `docs/decisions.md`. Commands the
author ran in their own terminal, and the tool's task notifications, are not messages either. The
messages here run up to the one that produced the last commit; the review that followed it is, in the
nature of things, not in the repository it reviews.

Content that is not about the code was cut, and every cut is marked `[removed: …]`, mostly `[removed: not about the code]`: other companies and
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
   S23 Android 16); reddetme yolu yazıldı ama bu projede tetiklenmedi. "Takip kalmaz" ifadesi [removed: not about the code] ve burada tekrar etmedi — kullanma. Doze, OEM pil yönetimi ve API 26–32 "Device check pending".
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
2. [removed: not about the code] acceptingFixes pencereyi daraltır ama recordFix'in içindeki bir fix
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
- Bu mesajdaki "7) TARAMA" bloğu docs/ai'ye hiçbir biçimde girmez.
- Bir kesinti teknik anlamı değiştirecekse kesme, önce bana sor.

6) CLEAN CLONE
- Commit'lerden sonra: `git clone <bu repo yolu> "$(mktemp -d)/rt"`. Bu, push öncesi yerel commit'leri test eder.
- Clone'a sadece `sdk.dir` içeren bir local.properties koy, key koyma.
- `./gradlew assembleDebug :core:test --no-build-cache` çalıştır. Log'dan BUILD SUCCESSFUL'u ve test sayısını oku. Üretilen BuildConfig'te `HAS_MAPS_API_KEY = false` olduğunu kontrol et.
- Build sonrası clone'da `git status --porcelain` boş olmalı (gitignore'da eksik yok demek).
- Ben push ettikten sonra: `git rev-parse HEAD` ile `git ls-remote origin main` eşit mi, kontrol et. GitHub'da video linklerinin oynayıp oynamadığına bak.

7) [removed: a scan for sensitive terms; the term list is kept out of the repository]

RAPOR: commit listesi; README'deki her sayının kaynağı (D numarası veya bugünkü ölçüm); tarama sonuçları (üç kapsam ve pozitif kontrol); clean clone log özeti; medya dosya boyutları.
````

---

## 13. 2026-09-17 13:02 UTC

**Summary:** Review 9 (delivery): the README's account of the AI workflow overclaims, one prompt cut is unmarked, move the new log rows to the end, crop or retake the notification screenshot; commit but do not push.

````text
DOKUZUNCU REVIEW (teslim). Bağımsız doğrulandı: yerel temiz clone (a79b692, key yok) 101/101 executed, 0 FROM-CACHE, :core 25/25, HAS_MAPS_API_KEY=false, merged manifest key "", build sonrası git status boş. RouteDao ve RouteDaoTest 572a39a'dan beri değişmedi, 6/6 geçerli. README'deki D12/D13/D17/D21 sayıları decisions.md ile birebir. Tarama üç kapsamda temiz, pozitif kontrol ateşledi. prompts.md transcript'teki 12 mesajın tamamı. MP4'lerde konum metadata'sı yok. 24/24 commit Co-Authored-By.

DÜZELTMELER:
1. P1: README "AI usage" iki yerde fazlasını iddia ediyor.
   (a) "reviewed every commit before pushing": 16 Eylül 18:21 ve 18:52'de, 17 Eylül 09:15'te push'u session yaptı. Kural 09:25'te geldi. docs/ai/README bunu doğru yazıyor ("From 2026-09-17"); README'yi onunla eşle.
   (b) Mesajların kaynağı yazılmamış. 1. mesaj (brief), review mesajları ve 12. mesaj (teslim brief'i) review session'ında hazırlandı; yazar neyin gönderileceğine karar verdi ve karar sorularını cevapladı. Bu cümleyi README AI bölümüne ve docs/ai/README Workflow 1. ve 5. maddelere ekle. prompts.md bunu zaten belli ediyor; README söylemezse okuyan kişi çelişki bulur. Karar listesine Co-Authored-By ve repo görünürlüğü sorularını da ekle (transcript'te 10 soru var).
2. P2: prompts.md mesaj 12 "7 iş" diyor ama 6 madde var. 7. madde işaretsiz kesilmiş, RAPOR satırı da sessizce değiştirilmiş. Dosyanın başı her kesintinin işaretli olduğunu söylüyor. Şunu ekle: "7) [removed: a scan for sensitive terms; the term list is kept out of the repository]". RAPOR satırını orijinaline döndür (içinde hassas bir şey yok).
3. P3: Verification log'daki 7 yeni satır, daha eski iki satırın ("Recording across stop and restart" ve "Mutation M5") üstüne eklenmiş, günlük artık kronolojik değil. Sona taşı.
4. P3: notification.png'de emülatörün "Serial console enabled, Performance is impacted" sistem bildirimi var ve göz önce ona gidiyor. Bildirim kapatılabiliyorsa kapatıp yeniden çek, kapatılamıyorsa `sips` ile uygulamanın bildirim kartını içeren kısma kırp. Kliplerde görünmesi sorun değil.

Koda dokunma. Düzeltmeleri commit et, push etme. docs/media, kullanıcı klipleri izleyene kadar commit'lenmez.
````

---

## 14. 2026-09-17 13:18 UTC

**Summary:** Shorten the demo clips with ffmpeg, back the originals up outside the repository, check the result with ffprobe, and do not commit them until they have been watched.

````text
KLİPLERİ KISALT: sadece medya ve doküman, koda dokunma.

Ölçüm (review session'ı, MP4 zaman tablosundan): screenrecord değişken kare hızıyla kaydediyor, ekran değişmeyince kare yazmıyor. clip1'in 106.7 s'sinin 72 s'si, clip2'nin 73.0 s'sinin 45 s'si 1 s'den uzun sabit karelerde geçiyor: konum güncellemesi bekleniyor. Hareketli kısımların medyan kare aralığı 18–35 ms, zaten akıcı. Sorun emülatör değil, bekleme.

Yöntem: her karenin ekranda kalma süresini 1.0 s ile sınırla; hareketli kısımlar gerçek hızda kalır. ffmpeg'i kullanıcı kuracak. `which ffmpeg` boşsa dur, bana söyle.

1. Orijinal klipleri repo dışına yedekle (`mktemp -d`).
2. Her klip için:
   ffmpeg -i IN.mp4 -vf "setpts='if(eq(N,0),0,PREV_OUTPTS+min(PTS-PREV_INPTS,1.0/TB))'" -fps_mode vfr -c:v libx264 -crf 23 -pix_fmt yuv420p -movflags +faststart -an OUT.mp4
3. ffprobe ile doğrula:
   - kare sayısı orijinalle aynı (`-count_frames`; clip1 432, clip2 405)
   - en uzun kare aralığı ≤ 1.0 s
   - yeni süre (beklenen yaklaşık clip1 53 s, clip2 40 s) ve dosya boyutu
4. Okunması gereken anlar (izin diyaloğu, adres kartı, reset onayı) 1 s'de okunamıyorsa sınırı 1.5 s yapıp tekrarla. İki sürümü de bana söyle, ben izleyip seçeceğim.
5. README Demo bölümü: süreleri güncelle ve şunu ekle: "Waits between location updates are shortened: no frame stays on screen longer than 1 s; everything else plays at real speed. The waits come from the location request, at most every 5 s in the foreground and 10 s in the background (D19)."
6. Verification log'a bir satır ekle: komut, sınır değeri, önceki ve sonraki süreler, kare sayıları.
7. Klipleri henüz commit etme; kullanıcı yeni hallerini izleyecek.
````

---

## 15. 2026-09-17 16:25 UTC

**Summary:** The videos are still bad: set up a fresh emulator and record them again.

````text
videolar hala çok kötü. sıfırdan emülatör kurup tekrardan video çek
````

---

## 16. 2026-09-17 17:52 UTC

**Summary:** Final round: measure startup and the map first, then custom markers and startup work, every change its own commit with before-and-after numbers, and stop at a gate to report.

````text
SON TUR: iki iyileştirme (custom marker, açılış ve harita hızı), ardından teslim. Fazlar sırayla; her faz bir kapıyla biter. Ölçmeden iddia yok.

FAZ 0: GÜVENLİK AĞI
- `git switch -c polish/markers-startup`. main teslim edilebilir hâlde kalır.
- Cuma 11:00'de Kapı 2 geçilmemişse branch'i bırak; main'deki mevcut kliplerle Faz 3'ün teslim adımlarına geç.

FAZ 1: ÖLÇ (kod yok)
Açılış (API 36 emülatör ve S23; her komutta -s ile pinle. S23'ten medya veya log repoya girmez, sadece sayılar):
1. Cold start: `am force-stop`, ardından `adb shell am start -S -W -n com.erolgizlice.routetracker/.MainActivity`, 5 kez. TotalTime medyanını al. Ayrıca temiz kurulumdan sonraki ilk açılışı 3 kez ölç (her seferinde uninstall + install).
2. Release'e yakın build: `benchmark` build type ekle (initWith release, isMinifyEnabled ve isShrinkResources true, signingConfig debug; Maps key debug SHA-1'e kısıtlı olduğu için çalışır, debuggable false). Aynı ölçümleri tekrarla. Debug ile benchmark sayılarını ayrı tut.
3. Harita: GoogleMap'in `onMapLoaded` anına ve rotanın ekrana geldiği ana geçici log koy (süreç başlangıcından geçen ms). Rota geldiğinde `reportFullyDrawn()` çağır. Ölç.
4. Kök nedenleri sayılarla sırala:
   - süreç başlangıcı ve Koin
   - ilk kare (main thread'de I/O var mı?)
   - Maps SDK init
   - tile indirme
   - kameranın önce İstanbul'a sonra rotaya gitmesi (tile'lar iki kez yükleniyor mu?)
Marker'lar:
5. Mevcut durum: her nokta için varsayılan `Marker`, `key(point.id)` ile. Stres ölçümü (timebox 30 dk, commit'lenmez): debug-only bir yolla 1000 sahte nokta yükle. `dumpsys gfxinfo <pkg> reset`, ardından haritada 10 s kaydırma ve zoom yap, sonra janky frame yüzdesini ve p90/p99 kare süresini oku.

FAZ 2: UYGULA (her değişiklik ayrı commit; her birinin önce/sonra sayısı olsun)
Açılış ve harita, Faz 1'deki kök nedenlere göre:
a. `MainActivity`'de Maps SDK'yı erken başlat: `MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, null)`. Application'a KOYMA: START_STICKY restart süreci Activity'siz açar, orada harita yok (D8). İlk kareyi geciktiriyorsa ölçümle göster ve geri al.
b. Rota varsa kamera doğrudan son noktada başlasın; İstanbul'dan rotaya zıplama olmasın. Doğrulama: ilk karelerde İstanbul görünümü yok.
c. `onMapLoaded` gelene kadar harita alanında uygulama arka plan renginde bir placeholder göster, sonra kısa bir fade ile kaldır. Bu algı iyileştirmesi, hız değil; öyle belgele. Key'siz build'de (D5) missing-key kartı yine görünmeli ve placeholder sonsuza kadar kalmamalı; ölç.
d. `profileinstaller` bağımlılığı var mı, `:app:dependencies` ile kontrol et. Özel Baseline Profile üretimi (macrobenchmark modülü) kapsam dışı; README sınırlarına yaz.
Custom marker'lar:
e. Dört görünüm: başlangıç, ara nokta, son nokta, seçili. Her biri Canvas/DrawScope ile BİR kez çizilen bitmap'ten tek bir `BitmapDescriptor`; bütün marker'lar paylaşır. `MarkerComposable` KULLANMA (her marker için Compose render eder). Numaralı marker YOK (her numara ayrı bitmap demek).
f. TUZAK: `BitmapDescriptorFactory` Maps SDK başlamadan çağrılırsa "IBitmapDescriptorFactory is not initialized" fırlatır. Descriptor'ları harita başladıktan sonra oluştur; key'siz build'de çökmediğini doğrula.
g. Seçili marker: adres kartı açık olan marker vurgulansın; mevcut seçim state'inden türet (MVI, tek state). contentDescription'lar korunur.
h. Anchor ve tıklama: demoda son marker mavi konum noktasıyla çakıştığı için ilk dokunuşta açılmıyordu. Yeni görünümle son marker'a 5 kez dokun, kaç kez açıldığını raporla.
i. Stil seçimi saf bir fonksiyon olsun (index, nokta sayısı, seçili id → stil). JVM unit testi yaz, bir mutasyonla testin kırılabildiğini kanıtla.
j. 1000 noktalı stres ölçümünü custom marker'larla tekrarla. Varsayılandan kötü değilse bunu "performans kazancı" diye değil "ek maliyetsiz görsel ve seçili durum" diye belgele. Performans iddiası sadece ölçüm destekliyorsa yazılır.
k. decisions.md'ye D23 (marker'lar) ve D24 (açılış ve harita): elenen alternatifler (MarkerComposable, numaralı marker, Application'da init) ve Measured kanıtlarla. verify-claim ile tüm testler taze sonuçla.

KAPI 2: DUR. Bana şunları raporla ve review gelmeden kayda geçme:
- commit listesi
- önce/sonra tablosu (debug ve benchmark; emülatör ve S23)
- stres sonuçları (varsayılan ve custom)
- dokunma testi sonucu
- emülatörden yeni marker'ların bir ekran görüntüsü (repoya girmez)

FAZ 3: KAYIT VE TESLİM (review sonrası "devam" deyince)
1. İki klibi yeni build ile yeniden çek (device-check "Recording a demo" kuralları, API 36 AVD, host recorder).
2. Klip 1'de arka plan paneli yine uzun ve donuk kalırsa (önceki ölçüm: panelde sadece sayaç değişiyordu, "4 markers" 20 s duruyordu, sonda 7 s donuk kare vardı): önce karelerle sınırları doğrula, sonra panel kısmını 4 kat hızlandır ve sondaki donuk kısmı kes. Örnek (sınırları yeni klibe göre ayarla):
   ffmpeg -i IN.mp4 -filter_complex "[0:v]split=3[a][b][c];[a]trim=0:54,setpts=PTS-STARTPTS[a1];[b]trim=54:96,setpts=(PTS-STARTPTS)/4,fps=30[b1];[c]trim=96:100,setpts=PTS-STARTPTS[c1];[a1][b1][c1]concat=n=3:v=1:a=0,format=yuv420p[v]" -map "[v]" -c:v libx264 -preset slow -crf 22 -movflags +faststart -an OUT.mp4
   Doğrula: sabit 30 fps, en uzun kare aralığı 33 ms, hızlı bölümde sayacın arttığı görünüyor. README'de hangi aralığın kaç kat hızlı oynadığını açıkça yaz. Verification log'a komut ve süreleri ekle. Hızlandırma yapılsın mı kararını kullanıcı izledikten sonra verir; iki sürümü de hazır tut.
3. README:
   - kareleri yeni kliplerden al; route.png alt metnindeki marker sayısı görsele uysun
   - "Tested devices" satırına API 36 emülatörünü ekle
   - açılış sayılarını ekle (debug ve benchmark ayrı ayrı; hangi cihazda)
   - Baseline Profile üretilmediğini sınırlara yaz
4. prompts.md, SON İŞ OLARAK:
   - 13:02 UTC'den sonraki tüm mesajlarımı ekle (bu mesaj dahil). Terminalde çalıştırdığım `brew install ffmpeg` ve task bildirimleri mesaj değil, dahil etme.
   - Mesaj 10'u orijinaline getir: "[removed: not about the code] ve burada tekrar etmedi".
   - docs/ai/README'deki review mesajı listesini güncelle.
   - Her mesajın transcript'teki orijinalinden yalnızca [removed] işaretlerinde ayrıldığını programatik olarak kontrol et.
5. Hassas terim taraması (üç kapsam + pozitif kontrol); temiz clone'da key'siz build ve :core testleri.
6. Bana haber ver ve DUR. Kullanıcı klipleri izleyip onaylayınca: docs/media commit'i, branch'i main'e fast-forward merge et. Push'u kullanıcı yapar.
````

---

## 17. 2026-09-17 19:20 UTC

**Summary:** Delivery phase: two small fixes, a written and recorded stress test, re-recorded clips, README sections, prompts.md, the sensitive-term scan and a fresh-clone check, with the privacy rules for anything recorded on the phone.

````text
FAZ 3: KAYIT, STRES TESTİ BELGESİ VE TESLİM. Kod donduruldu; aşağıdakiler dışında koda dokunma.

ÖNCE İKİ KÜÇÜK DÜZELTME (yapılırsa testler ve ölçümler tekrar):
1. D24'e bir cümle: rota okunurken ekranda harita da placeholder da yok, çünkü ikisi de aynı koşulun içinde; süre kısa ve arka plan rengi aynı.
2. `ReportDrawnWhen { isMapLoaded }` çevrimdışıyken hiç tetiklenmiyor. `isMapLoaded || placeholderTimedOut` yap; çevrimiçi ölçümleri değiştirmediğini bir koşuyla göster, D24'e yaz. Hızlı değilse atla ve atladığını söyle.

STRES TESTİ BELGESİ (kullanıcının isteği: hem yazılı hem videolu, repoda)
3. `docs/stress/README.md`:
   - Yöntem: 1000 noktanın veritabanına nasıl konduğu (kullandığın tam komut, uygulamada kod yok), neden 20 m aralık, hangi build, hangi cihaz, `dumpsys gfxinfo <pkg> reset` ve 10 s kaydırma/zoom protokolü.
   - Sonuçlar: varsayılan marker ve dört paylaşılan bitmap için S23 ve API 36 emülatör; toplam kare, janky yüzdesi, p50/p90/p99. `dumpsys gfxinfo` çıktısının ilgili bölümünü olduğu gibi ver (cihaz seri numarası ve dosya yolları çıkarılmış olarak).
   - 1000 noktalı soğuk açılış sayıları.
   - Dürüst yorum: bu bir performans iddiası değil; ölçüm, dört bitmap'in ölçülebilir bir maliyet getirmediğini gösteriyor. Emülatör sayıları kendi GPU emülasyonuyla sınırlı, karar veren sayılar telefondan.
4. `docs/media/clip3-stress.mp4` (emülatör, ≤ 20 s): 1000 marker ekranda, akıcı kaydırma ve zoom. README'de linkin yanına tek cümle: emülatörde çekildi, emülatörün GPU emülasyonu hem varsayılan hem custom marker'ları aynı şekilde sınırlıyor, karşılaştırmalı sayılar docs/stress'te. Telefondan görüntü YOK.

KAYIT
5. Klip 1 ve klip 2'yi yeni build ile yeniden çek (device-check "Recording a demo" kuralları, API 36 AVD, host recorder, geo fix saniyede bir).
6. Klip 1'de arka plan paneli yine uzun kalırsa: sınırları karelerle doğrula, panel kısmını 4× hızlandır, sondaki donuk kısmı kes. Hem gerçek hızlı hem hızlandırılmış sürümü hazır tut; kullanıcı izleyip seçecek. Hangi aralığın kaç kat oynadığı README'de yazacak.

README
7. Kareleri yeni kliplerden çıkar; route.png alt metnindeki marker sayısı görselle uysun.
8. "Tested devices" satırına API 36 emülatörünü ekle.
9. Yeni bölüm: marker'ların dört görünümü (başlangıç, ara nokta, son nokta, seçili) ve nedenleri, D23'e link.
10. Açılış sayıları: debug ve benchmark ayrı, hangi cihaz olduğu yazılı; ilk kare ile fully drawn ayrımı ve fully drawn'ın neden büyüdüğü. Review session'ının bağımsız ölçümünü de ekleyebilirsin: emülatör, debug, 7 noktalı rota, ikişer tur beşer soğuk açılış, `am start -W` TotalTime medyanı 2152 ms → 1626 ms.
11. "Build and test" bölümüne `benchmark` build type'ının ne işe yaradığı ve `./gradlew assembleBenchmark` komutu.
12. Sınırlara: Baseline Profile üretilmedi; kütüphanelerin kendi profilleri kuruluyor.

SON İŞLER
13. prompts.md: 13:02 UTC'den sonraki bütün mesajlarım (bu dahil). `brew install ffmpeg` ve task bildirimleri mesaj değil. Mesaj 10'u orijinaline getir ("[removed: not about the code] ve burada tekrar etmedi"). docs/ai/README'deki mesaj listesini güncelle. Her mesajın orijinalinden yalnızca [removed] işaretlerinde ayrıldığını programatik doğrula.
14. Hassas terim taraması (üç kapsam + pozitif kontrol). verify-claim ile tüm testler taze. Temiz clone'da key'siz build ve :core testleri.
15. DUR ve bana raporla. Kullanıcı klipleri izleyip onaylayınca: docs/media ve docs/stress commit'i, branch'i main'e fast-forward merge. Push'u kullanıcı yapar.

ZAMAN: 13:00'e kadar stres videosu bitmezse onu bırak, yazılı sonuçlarla devam et ve bana söyle.


Faz 3 prompt'undaki 4. maddenin yerine geçer:

STRES KLİBİ S23'TE. Uygulamanın dışına hiç çıkma; sistem arayüzü kadraja girmeyecek.

1. Hazırlık:
   - S=[removed: the test phone's serial number]; her komutta -s ile pinle.
   - `adb -s $S shell pm clear com.erolgizlice.routetracker` — D17 testlerinden kalan GERÇEK koordinatlar ve adresler bu veritabanında; önce silinecek.
   - `adb -s $S shell pm revoke com.erolgizlice.routetracker android.permission.ACCESS_FINE_LOCATION` ve COARSE. Böylece mavi konum noktası çizilmez, ekranda gerçek konum olmaz. Bunu kayıttan önce ekran görüntüsüyle doğrula.
   - Rahatsız Etmeyin'i aç; kayıt sırasında bildirim düşmesin.
2. 1000 noktayı tohumla (uygulamada kod yok, doğrudan veritabanına; kullandığın tam komutu docs/stress'e yazacaksın). Koordinatlar uydurma rota üzerinde olsun.
3. Kaydet: `adb -s $S shell screenrecord --bit-rate 8000000 --time-limit 60 /sdcard/stress.mp4`, uygulamayı aç, 1000 marker görünürken 10-15 s kaydır ve zoom yap, `pkill -INT screenrecord` ile bitir, çek.
   Sabit kare hızına çevir: `ffmpeg -i stress.mp4 -vf fps=30 -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -movflags +faststart -an docs/media/clip3-stress.mp4`. ≤ 20 s, ≤ 5 MB.
4. Aynı koşuda `dumpsys gfxinfo` sayılarını da al; docs/stress/README.md'ye hem varsayılan hem custom marker sonuçlarını, S23 ve emülatör için ayrı ayrı yaz. Çıktıdan cihaz seri numarasını ve dosya yollarını temizle.
5. Gizlilik kapısı: klibin 1 fps kare kontak sayfasını çıkar ve tara. Gerçek adres, hesap adı, kişisel uygulama, durum çubuğunda tanımlayıcı bir şey olmamalı. Kullanıcı ve review session'ı görmeden commit yok.
6. Geri alma, kayıt biter bitmez: konum izinlerini geri ver (`pm grant` ikisi için), DND'yi kapat. Telefonda Haritalar'ın gerçek konumu gösterdiğini doğrula.
7. README: klip 3'ün S23'te, konum izni kapalıyken, tohumlanmış 1000 noktalı bir rota ile çekildiğini yaz. Emülatör sayılarının kendi GPU emülasyonuyla sınırlı olduğunu da.


KLİP 1 VE 2'Yİ S23'TE ÇEK. Cut line 11:00: aşağıdaki 2. madde çalışmazsa ya da gizlilik kapısı takılırsa emülatör klipleriyle devam et.

1. `pm clear` ile eski gerçek noktaları sil. DND aç. Ekran zaman aşımını uzat (eski değerini not al).
2. Sahte konum sağlayıcısı, her adımı ölçerek:
   adb -s $S shell appops set 2000 android:mock_location allow
   adb -s $S shell cmd location providers add-test-provider fused --requiresSatellite --supportsSpeed --supportsBearing
   adb -s $S shell cmd location providers set-test-provider-enabled fused true
   adb -s $S shell cmd location providers set-test-provider-location fused --location 41.0369,28.9850 --accuracy 8
   ÖLÇ: uygulama bu fix'i alıyor mu, marker düşüyor mu? "fused" işe yaramazsa "gps" sağlayıcısıyla dene ve fused istemcisine ulaşıp ulaşmadığını ölç. İkisi de olmuyorsa DUR, emülatörle devam et ve bana söyle.
   Rota: saniyede bir fix, ~11 m/s, aynı 707 m'lik uydurma rota. Doğruluk 8 m, yani 50 m kapısının üstünde.
3. SİSTEM ARAYÜZÜ YASAK: ana ekran, son uygulamalar ve bildirim gölgesi kadraja girmeyecek. Bu telefonda ikinci kullanıcı yok, steril ana ekran yok.
   - Arka plan sahnesi: Saat uygulamasına geç, orada kal, sonra uygulamaya dön. Uygulamaya dönünce arkada eklenen marker'lar orada olacak.
   - Bildirim sahnesini S23'te çekme; emülatör klibindeki hâliyle kalsın ya da hiç gösterme.
   - Klip 2'deki "son uygulamalardan kaydırma" sahnesi de S23'te çekilmez; o sahne emülatörde kalır.
4. Kayıt ve dönüştürme Blok 1'deki gibi (screenrecord + ffmpeg fps=30).
5. Gizlilik kapısı: her iki klibin 1 fps kontak sayfasını çıkar ve tara. Kullanıcı ve review session'ı onaylamadan commit yok.
6. Geri alma, hemen kayıttan sonra, tek tek doğrulayarak:
   adb -s $S shell cmd location providers remove-test-provider fused
   adb -s $S shell appops set 2000 android:mock_location default
   DND kapat, ekran zaman aşımını eski değerine al. Telefonda Haritalar gerçek konumu gösteriyor mu, doğrula.
7. README ve decisions: hangi klibin hangi cihazda çekildiği, S23 kliplerinde konumun test sağlayıcısıyla verildiği, hangi sahnelerin neden emülatörde kaldığı açıkça yazılacak.
````

---

## 18. 2026-09-17 20:48 UTC

**Summary:** Review 11: the repository's own rule still forbids media from a physical device while one clip comes from one; do not commit the clip that is not chosen; write down what the marker count shows during launch.

````text
ON BİRİNCİ REVIEW. Bağımsız doğrulandı: 32 JVM testi taze XML ile 0 hata (19/19 task); a8f4f97'nin temiz klonu key'siz 107/107 executed, HAS_MAPS_API_KEY=false, 32 test, git status boş; büyük/küçük harf ve Türkçe diyakritik katlayan taramada 71 metin dosyası + tüm commit mesajları + tüm diff'lerde 25 terimin hiçbiri yok, pozitif kontroller ateşledi; prompts.md'deki 17 mesajın hepsi transcript'ten yalnızca [removed] işaretlerinde ayrılıyor; telefonda sahte sağlayıcı yok, appop default, uygulama verisi silinmiş, DND kapalı. Dört klip de 30 fps CFR, konum metadata'sı yok; kareleri tek tek inceledim, gizlilik temiz. Seçili marker'ın mavi pin olması, eski klipteki "hangi marker seçildi" eksiğini kapatmış.

DÜZELTMELER:
1. P1: Repo kendi kuralıyla çelişiyor. Klip 3 fiziksel telefondan, ama CLAUDE.md ("Never commit screenshots, recordings or logs from a physical device"), .claude/skills/device-check/SKILL.md ve docs/ai/README.md ("All media in docs/media/ comes from an emulator on a made-up route") hâlâ tersini söylüyor. Kuralı fiilen uyguladığın hâliyle yaz: telefondan medya ancak (a) konum izni kaldırılmış, (b) uygulama verisi `pm clear` ile silinmiş, (c) rota uydurma ve tohumlanmış, (d) her kare kontak sayfasında taranmış, (e) cihaz sonradan geri alınmış ve doğrulanmışsa alınır; aksi hâlde emülatör. Üç dosyada da aynı kural görünsün. docs/ai/README'deki "not included" maddesini de düzelt.
2. P2: clip1 ile clip1-tracking-fast arasındaki seçimi kullanıcı yapacak. README şu an yalnızca gerçek zamanlı olanı gösteriyor. Seçilmeyen dosyayı commit etme; hızlandırılmış olan seçilirse README'de hangi aralığın kaç kat oynadığı açıkça yazılsın.
3. P3: Klip 2'de uygulama yeniden açılırken kontrol çubuğu yaklaşık 2 s boyunca "Not tracking, 0 markers" gösteriyor, sonra 7'ye dönüyor. Harita alanı artık düz olduğu için eskisinden daha görünür. Koda dokunma; D24'e bir cümleyle yaz (rota okunana kadar sayaç henüz gerçek değeri değil).

Kod donduruldu. Bunları commit et, push etme.
````

---

## 19. 2026-09-17 21:01 UTC

**Summary:** Polish the UI and then record once: measure what is drawn behind the navigation bar, let the map run to the bottom edge behind a translucent card, and verify contrast, tap targets, rotation and the keyless path.

````text
UI ROTUŞLARI, SONRA TEK SEFERDE YENİDEN KAYIT. İş mantığına dokunma; sadece TrackingScreen, MainActivity ve gerekiyorsa tema.

ÖNCE ÖLÇ (kod yazmadan):
1. Emülatörde harita zaten navigation bar alanının arkasına çiziliyor (review session'ı ölçtü: inset bölgesi y=2274-2340, oradaki pikseller harita renkleri). S23'te üç tuşlu navigation bar var ve edge-to-edge otomatik bir kontrast perdesi koyuyor; klip 3'teki açık şerit bu. İki cihazda da ekran görüntüsüyle doğrula.

DEĞİŞİKLİKLER:
2. `window.isNavigationBarContrastEnforced = false` (ya da enableEdgeToEdge'e şeffaf SystemBarStyle). Ölç: S23'te navigation bar alanında harita pikselleri görünüyor mu, emülatörde bir şey bozuldu mu.
3. Kontrol kartı navigation bar'ın arkasına uzansın: alt safeDrawing padding'ini dış Column'dan kaldır, onun yerine kartın KENDİ içine navigation bar inset'i kadar alt iç boşluk ver. Böylece kart arkaya uzanır ama butonlar jest çubuğunun/tuşların üstünde kalır. Yatay ve üst inset'ler aynı kalsın.
4. Kart arka planı yarı saydam olsun (surface rengi, alfa ~0.85) ve altına şeffaftan yüzey rengine ince bir gradyan koy. GERÇEK BLUR YAPMA: harita ayrı bir yüzeyde çiziliyor, Compose blur'u onu örnekleyemez; tek alternatif haritanın tamamını bulanıklaştırmak olurdu. Bunu D25'te elenen alternatif olarak yaz.
5. Google logosu ve "Map data" atıfı: sol altta kalır, taşınamaz (SDK logoyu alt kenara sabitliyor; contentPadding onu yukarı iterken kameranın odak alanını da daraltıyor, ayrıca Maps Platform şartları atıfın görünür kalmasını istiyor). Kart yarı saydam olduktan sonra bile logonun kartın ALTINDA KALMADIĞINI ekran görüntüsüyle doğrula; haritanın alt contentPadding'i kart yüksekliğinden hesaplanmaya devam etsin.

DOĞRULAMA (hepsi ölçüm):
6. Metin kontrastı: kartın üstündeki yazı, haritanın hem açık hem koyu bölgelerinde okunur mu; kontrast oranını hesapla (en az 4.5:1 hedefle) ve sayıyı yaz.
7. Dokunma hedefleri: Start/Stop/Reset jest çubuğunun üstünde mi, üç tuşlu cihazda tuşlarla çakışıyor mu; her butona beşer kez dokun.
8. Ekran döndürme, key'siz build (missing-key kartı hâlâ görünüyor mu), ve placeholder yolu bozulmamış olsun.
9. Testler taze (verify-claim), temiz clone key'siz build.
10. decisions.md'ye D25: edge-to-edge kart, yarı saydamlık, elenen alternatifler (gerçek blur, logoyu taşımak) ve ölçülmüş kanıtlar. README'ye tek cümle.

SONRA KAYIT (UI donduktan sonra, tek seferde):
11. Klip 1, klip 2 ve klip 3'ü yeniden çek (device-check kuralları; klip 3 için telefonda yine önce `pm clear`, konum izni kaldırılmış, rota tohumlanmış, sonra cihaz geri alınmış). README karelerini yeni kliplerden çıkar.
12. Eski klipleri yenisi onaylanana kadar silme.

Commit et, push etme. Bitince rapor ver.
````

---

## 20. 2026-09-17 21:27 UTC

**Summary:** The scrim's numbers come from a pattern that already works: transparent bars, the app's own gradient as tall as the navigation bar inset plus 32 dp with three stops, decorative, constants in one place.

````text
EK: perdenin sayıları hazır bir desenden gelsin, uydurma.

1. MainActivity:
   enableEdgeToEdge(
       statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
       navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
   )
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
   minSdk 26 olduğu için Q kontrolü şart.

2. Perde: yükseklik = WindowInsets.navigationBars alt inset'i + 32 dp; dikey gradyan, üç durak: renk alpha 0f → alpha 0.4f → alpha 0.8f (yani alpha, alpha*0.5, 0 sırasının tersi, alt kenarda en opak). Renk MaterialTheme.colorScheme.background. Perde dekoratif: clearAndSetSemantics {} ile erişilebilirlik ağacından çıkar. Bu üç sayı (0.8 alfa, 32 dp tolerans, üç durak) yazarın kendi uygulamasında kullandığı ve yerinde oturduğu görülen oranlar; sabitleri tek bir yerde topla ki kart ile perde birbirinden kaymasın.

3. Kart yarı saydam olduğunda perde kartın ARKASINDA, haritanın ÜSTÜNDE olacak. Sıralama: harita → perde → kart. Perde kartı karartmamalı.

4. Google logosu: Maps SDK'da Mapbox'taki gibi bir logo slot'u yok; tek kaldıraç contentPadding ve kod zaten kart yüksekliğinden hesaplıyor. Perde ve kart geldikten sonra logonun hâlâ görünür olduğunu ekran görüntüsüyle doğrula; kapanıyorsa contentPadding'i perdenin yüksekliğini de kapsayacak şekilde büyüt.

5. Ekranı dikey kilitleme; döndürme testi var.
````

---

## 21. 2026-09-18 06:38 UTC

**Summary:** Review 12: the media rule now reads the same in three files; make a release build type work with signing properties that stay outside the repository, and plan around a release build not being debuggable.

````text
ON İKİNCİ REVIEW. Bağımsız doğrulandı: 32 JVM testi taze XML ile 0 hata; tarama üç kapsamda 0, pozitif kontrol ateşledi; medya kuralı artık CLAUDE.md, device-check skill'i ve docs/ai/README'de aynı, önceki P1 kapandı. Kendi cihaz kontrollerim: emülatörde harita alt kenara iniyor, kart yarı saydam, logo kartın üstünde; S23'te üç tuşlu navigation bar haritanın üstünde çiziliyor ve sistemin beyaz kontrast şeridi gitmiş. D25'teki before/after ölçümü tuttu.

ŞİMDİ VİDEO ÇEKME. Kayıt release build ile yapılacak ve önce imzalama ile API key kısıtlaması hazırlanacak. Sıra:

1. `release` build type'ı çalışır hâle getir:
   - Release signing config SADECE Gradle property'leri varsa oluşsun (RT_RELEASE_STORE_FILE, RT_RELEASE_STORE_PASSWORD, RT_RELEASE_KEY_ALIAS, RT_RELEASE_KEY_PASSWORD; hepsi ~/.gradle/gradle.properties'te, repoda değil). Property yoksa `assembleRelease` yine çalışmalı, sadece imzasız çıkmalı: reviewer'ın klonu bozulmayacak.
   - release: isMinifyEnabled = true, isShrinkResources = true. benchmark zaten initWith(release) ile aynı R8 kurallarını kullanıyor, yani risk düşük; yine de release APK'sını kurup Koin ve Room'un çalıştığını ölç.
   - `.gitignore`'a `*.jks` ve `*.keystore` ekle. Keystore ve parolalar repoya asla girmez; parolaları ekrana da basma.
2. Kullanıcı keystore'u oluşturup SHA-1'i Cloud Console'daki key'e ekleyecek (parolaları o giriyor). Sen bekle; "eklendi" dediğinde release APK'sını kur ve haritanın geldiğini ölç. Gri gelirse `adb logcat | grep -i "Authorization failure"` satırındaki paket ve SHA-1'i konsoldakiyle karşılaştır.
3. TUZAK, kayıttan önce planla: release build debuggable değil, yani `run-as` çalışmaz. Klip 3'ün 1000 noktası `run-as` ile tohumlanıyordu ve "debug ile tohumla, sonra benchmark'ı install -r et" numarası da gerçek release imzasıyla çalışmaz (imza farklı olduğu için install -r reddeder). O yüzden: klip 1 ve klip 2 release build ile, klip 3 benchmark build ile kalsın; README'de hangi klibin hangi build'den geldiği yazsın. Daha iyi bir yol bulursan önce bana söyle.
4. Kayıt öncesi kontrol listesi: API 36 AVD ayakta mı, emülatörde uygulama verisi temiz mi, POST_NOTIFICATIONS verildi mi.
5. Kayıt izni gelene kadar sadece hazırlık ve doğrulama yap; klip çekme. Hazır olduğunda bana raporla, kaydı ondan sonra başlatacağız.

decisions.md'ye D26: release imzalama ve key kısıtlaması, elenen alternatifler (release'i debug key ile imzalamak, klip 3'ü release ile çekmek) ve ölçülmüş kanıtlar.
````

---

## 22. 2026-09-18 06:50 UTC

**Summary:** The release build type keeps minification and resource shrinking on, benchmark differs from it only by its certificate, and R8 is to be checked on a device rather than assumed.

````text
EK: release build type'ı minify ve shrinkResources AÇIK olacak, benchmark ile bire bir aynı yapılandırma; tek fark imzalayan sertifika. benchmark zaten initWith(release) olduğu için ikisi kendiliğinden eşleşsin, ayrı ayrı ayar yazma. README'de "benchmark = release + debug imzası" cümlesi doğru kalmalı, çünkü açılış ve stres sayıları benchmark'tan geliyor.

Release APK'sı kurulduktan sonra R8'in bir şey kırmadığını cihazda ölç, sadece haritanın gelmesine bakma:
- takip başlat, marker düşüyor mu
- marker'a dokun, adres kartı açılıyor mu
- Stop, uygulamayı kapat-aç, rota duruyor mu
- Reset çalışıyor mu
- logcat'te ClassNotFound / NoSuchMethod / Koin hatası var mı
Sonucu D26'ya yaz. Bir şey kırılırsa çözüm minify'ı kapatmak değil, eksik proguard kuralını eklemek ve kuralı gerekçesiyle yazmak.
````

---

## 23. 2026-09-18 07:23 UTC

**Summary:** The SHA-1 is registered: measure the map and start recording, on the phone if possible.

````text
SHA-1 eklendi, haritayı ölç ve kayda başla. mümkünse kayıtları s23 cihazıyla yap
````

---

## 24. 2026-09-18 07:39 UTC

**Summary:** Try the phone again.

````text
s23'te bidaha dene
````

---

## 25. 2026-09-18 07:44 UTC

**Summary:** The phone had no internet; with it connected the map appeared, so start again.

````text
s23'te internet bağlı değilmiş. bağladım ve harita gözüktü. baştan bidaha dene
````

---

## 26. 2026-09-18 08:05 UTC

**Summary:** Plan change: every clip is recorded on the phone, with the app's own mock source in a separate demo build type, and none of those hooks in release or debug.

````text
PLAN DEĞİŞİKLİĞİ: bütün kayıtlar S23'te alınacak, emülatörde kayıt yok. Sahte konum, fused'ın desteklediği tek yoldan gelecek: uygulamanın kendisi setMockMode + setMockLocation çağıracak. Platform test sağlayıcısını tekrar deneme, onu zaten ölçtün ve Play services yok sayıyor.

ADIM 1, ÖLÇÜM (timebox 20 dk, 12:00'ye kadar): S23'te FusedLocationProviderClient.setMockMode(true) çalışıyor mu? Geçici bir hook ile dene: `adb shell appops set com.erolgizlice.routetracker android:mock_location allow`, sonra setMockMode + setMockLocation ile bir fix gönder ve uygulamanın onu KAYDETTİĞİNİ göster (marker düştü mü). SecurityException geliyorsa ya da fix ulaşmıyorsa DUR ve bana söyle; planı kullanıcı değiştirecek.

ADIM 2, ÇALIŞIYORSA: `demo` build type, initWith(release), aynı imza ve R8; sahte konum kaynağı ve 1000 noktalık tohumlama YALNIZCA `app/src/demo/` kaynak kümesinde. Release ve debug varyantlarında bu kod bulunmayacak; bunu `assembleRelease` çıktısında dex'i tarayarak doğrula. Üretim kodunda tek satır değişiklik olmayacak.

ADIM 3, KAYITLAR (hepsi S23, demo build):
- Klip 1 ve klip 2: 707 m'lik uydurma rota, saniyede bir fix, doğruluk 8 m.
- Klip 3: 1000 nokta tohumlanmış, konum izni kaldırılmış.
- Gizlilik kapısı her klip için: pm clear ile başla, uydurma rota, ana ekran/son uygulamalar/bildirim gölgesi KADRAJA GİRMESİN (telefonda steril ikinci kullanıcı yok; arka plan sahnesi için Saat gibi kişisel veri taşımayan bir uygulamaya geç), her kare kontak sayfasında taransın, cihaz sonunda geri alınsın ve geri alma doğrulansın (appop default, izinler, DND, ekran zaman aşımı).
- Klip 1'in hızlandırılmış sürümünü de üret.

ADIM 4: D26'ya demo varyantı, neden gerektiği, elenen alternatifler (platform test sağlayıcısı: ölçüldü, fused yok sayıyor; release'e mock kodu koymak: üretim yolunu kirletir) ve dex doğrulaması. README'de hangi klibin hangi varyantla çekildiği tek cümleyle.

Klipleri commit etme; kullanıcı ve ben izleyeceğiz.
````

---

## 27. 2026-09-18 08:12 UTC

**Summary:** Try again.

````text
Try again
````

---

## 28. 2026-09-18 09:01 UTC

**Summary:** Review 13: correct what is actually demo-only, say why the demo receiver is exported, link the shortened clip as clip 1, then finish the prompts, commit the media and re-run the checks.

````text
ON ÜÇÜNCÜ REVIEW. Bağımsız doğrulandı: temiz klonda RT_RELEASE_STORE_PASSWORD boşken assembleDebug + assembleRelease BUILD SUCCESSFUL, 235/235 task, çıktı app-release-unsigned.apk, 32 test, git status boş. Dört APK'nın dex taraması senin sonucunu veriyor (release ve benchmark 0/0/0, demo 1/1/2, debug 0/0/1). Terim taraması üç kapsamda 0; keystore parolasının metni repoda hiçbir yerde yok. Üç klibin de kareleri okundu: ana ekran yok, son uygulamalarda yalnızca Hesap Makinesi ve uygulama, hesap adı yok, konum noktası yok. Telefon geri alınmış: appop default, aktif mock sağlayıcı yok, veri silinmiş.

DÜZELTMELER:
1. P2: app/build.gradle.kts'teki yorum ve D26, play-services-location'ın "yalnızca demo classpath'inde" olduğunu ve üretim build'inin bu API'lere erişemeyeceğini söylüyor. Yanlış: kütüphane :data üzerinden her varyantta var, fused konum oradan geliyor. Demo'ya özel olan alıcı ve doğrudan derleme bağımlılığı. İki yeri de düzelt; D26'nın kendi dex kanıtı (debug'daki setMockMode isabeti kütüphaneden) zaten bunu söylüyor.
2. P3: src/demo/AndroidManifest.xml'deki alıcı exported ve izinsiz. adb broadcast için gerekli olduğu ve yalnızca demo varyantında bulunduğu manifest yorumunda açıkça yazsın.
3. README'de klip 1 olarak HIZLANDIRILMIŞ sürümü linkle (arka plan sahnesi 35 s boyunca sabit bir hesap makinesi; takibin sürdüğü ancak dönüşte görülüyor). Gerçek zamanlı sürüm repoda kalsın ve README tek cümleyle neyin kaç kat hızlandırıldığını söylesin.

SONRA, SIRAYLA:
4. prompts.md: 20:48 UTC'den sonraki bütün mesajlarım (bu dahil). Her mesajın orijinalinden yalnızca [removed] işaretlerinde ayrıldığını programatik doğrula. docs/ai/README'deki mesaj listesini güncelle.
5. Medyayı commit et (kullanıcı klipleri onayladı sayılır: review session'ı bütün kareleri okudu ve temiz buldu; yine de kullanıcı kendi gözüyle görecek).
6. verify-claim ile testler taze, tarama tekrar, temiz klonda key'siz build.
7. Bana raporla ve DUR. Merge ve push kullanıcıda.
````

---

## 29. 2026-09-18 09:05 UTC

**Summary:** Record the stress clip again, starting from a cold start, and take the startup numbers and the frame statistics from that same run.

````text
EK (önceki review'daki düzeltmelerden SONRA, ancak 13:30'a kadar yetişiyorsa):

Klip 3'ü soğuk açılışla yeniden çek. Şu an yalnızca gezinme var; asıl gösterilmesi gereken şey bin marker'la uygulamanın açılışı, çünkü onun kanıtı şimdilik sadece docs/stress'teki tabloda.

1. Telefonda: demo build kurulu, konum izni kaldırılmış, DND açık, uygulama verisi temiz. Bin noktayı tohumla, uygulamayı force-stop et.
2. Kaydı başlat, uygulamayı aç, harita ve bin marker ekrana gelsin, sonra 8-10 s kaydır ve zoom yap. Toplam 15-20 s.
3. AYNI KOŞUDA demo build'in soğuk açılışını ölç (am start -W TotalTime ve Fully drawn), 3 koşunun medyanı. docs/stress'teki tabloya "demo build, telefon" satırı olarak ekle; videodaki açılışın hangi sayıya karşılık geldiği belli olsun.
4. dumpsys gfxinfo'yu yine aynı koşudan al, docs/stress'teki sayıları güncelle.
5. Gizlilik kapısı aynı: kareleri kontak sayfasında oku, ana ekran/son uygulamalar/bildirim gölgesi kadraja girmesin, cihazı sonunda geri al ve geri almayı doğrula.
6. README'de klip 3'ün açıklamasına "soğuk açılışla başlıyor" cümlesini ekle.

Yetişmiyorsa mevcut klip kalsın, bana söyle.
````

---

## 30. 2026-09-18 09:22 UTC

**Summary:** Review 14: three files still say the media comes from an emulator, the README still claims every clip plays in real time, and the rule should say how a cropped screenshot is scanned.

````text
ON DÖRDÜNCÜ REVIEW. Bağımsız doğrulandı: 32 test taze XML ile 0 hata; HEAD'in klonu medya dahil build oluyor; imzalama property'si boşken assembleRelease imzasız APK üretiyor; dex taraması release ve benchmark'ta demo kancası yok diyor; terim taraması üç kapsamda 0, kontrol ateşledi; prompts.md'deki 28 mesajın hepsi transcript'ten yalnızca [removed] işaretlerinde ayrılıyor (sırada gönderilen dördü dahil); yeni klip 3 sabit 30 fps, konum metadata'sı yok, kareleri temiz.

DÜZELTMELER:
1. P1: docs/media içindeki HER ŞEY artık telefondan. CLAUDE.md:85, docs/ai/README.md:78 ve .claude/skills/device-check/SKILL.md:29 hâlâ "klip 3 telefondan çekilen tek dosya, gerisi emülatörden" diyor. Üçünü de gerçeğe getir: bütün medya telefondan, sayılan koşullar altında (uydurma tohumlanmış rota, konum izni durumu, kare kare tarama, cihazın geri alınması ve doğrulanması). Emülatör artık yalnızca instrumented testler için kullanılıyor; öyle yaz.
2. P2: README'nin Demo paragrafı "All three play in real time" diyor, ama klip 1'in bir bölümü 4× oynuyor. Cümleyi düzelt: sabit 30 fps hepsinde geçerli; klip 2 ve 3 baştan sona gerçek zamanlı; klip 1 yalnızca belirtilen aralıkta hızlandırılmış.
3. Küçük: notification.png kırpılmış bir ekran görüntüsü. Kural metninde "commit edilen kırpılmış hâli taranır" cümlesi varsa yeterli, yoksa bir cümle ekle.

Sonra: verify-claim ile testler taze, tarama tekrar, ve bana raporla. Merge ve push kullanıcıda; sen push etme.
````

---

## 31. 2026-09-18 09:25 UTC

**Summary:** Review 15: the rule's first condition is still the permission, when the real one is that no real location reaches the screen; then close prompts.md and run the checks again.

````text
ON BEŞİNCİ REVIEW. İki düzeltme doğru, biri yarım kaldı.

1. P2: CLAUDE.md ve .claude/skills/device-check/SKILL.md'deki kuralın BİRİNCİ koşulu hâlâ "konum izni kaldırılmış olacak" diyor, sonra "docs/media'daki her şey bu koşullarla çekildi" deniyor. Klip 1 ve klip 2 izin VERİLMİŞKEN çekildi; gerçek konumu ekran dışında tutan şey, uygulamanın yalnızca demo build'inin sahte fix'lerini almasıydı. İzin yalnızca klip 3'te kaldırıldı.
   Koşulu gerçek değişmezle değiştir: ekrana gerçek konum ulaşmaz; bu ya izni kaldırarak (klip 3) ya da uygulamanın aldığı her fix'in demo kaynağından gelmesiyle (klip 1-2) sağlanır ve kaydedilen koordinatlara bakılarak doğrulanır. docs/ai/README.md'deki ifade zaten doğru, üçünü aynı cümleye getir.

2. SON İŞ: prompts.md'ye 09:01 UTC'den sonraki mesajlarımı ekle (ON DÖRDÜNCÜ REVIEW ve bu mesaj). Dosyanın girişine tek cümle: yayımlanan mesajlar son commit'i üreten mesaja kadar olanlardır, ondan sonraki review alışverişi doğal olarak burada değil. Her mesajın orijinalinden yalnızca [removed] işaretlerinde ayrıldığını programatik doğrula ve docs/ai/README'deki mesaj numaralarını güncelle.

3. Sonra verify-claim ile testleri taze koştur, taramayı tekrarla, bana raporla ve DUR. Merge ve push kullanıcıda.
````

---

## 32. 2026-09-18 09:53 UTC

**Summary:** Architecture: pull the screen's state transitions out into a pure reducer with JVM tests and a mutation, or say honestly in the documents that there is none; behaviour unchanged, and D21 is to record where the address policy lives and what leaving it there costs.

````text
MİMARİ: SAF REDUCER + DOKÜMAN DÜZELTMESİ. Kesme noktası 14:15. Görsel hiçbir şey değişmeyecek, kayıtlar yeniden çekilmeyecek. Davranış birebir korunacak.

Tespit: sözleşme MVI (tek state, tek onIntent, ayrı effect kanalı) ama reducer yok. Geçişler when bloğunun içinde screen.update { copy(...) } olarak yan etkilerle iç içe. Ayrıca izin merdiveninin kararı (Denied/Blocked, D18'deki Android 16 inceliği) ViewModel'de ve JVM testi yok.

1. ScreenState'in SAF geçişlerini ayır:
   internal fun ScreenState.reduce(intent: TrackingIntent, location: LocationSnapshot): ScreenState
   LocationSnapshot(hasPrecise, hasAny, isEnabled) ViewModel'de okunur ve parametre olarak verilir; reducer saf kalır, Android'e dokunmaz, :feature:tracking'in test kaynak kümesinden çağrılabilir.
   Yan etkiler (trackingController.start/stop, routeRepository.reset, resolveAddress, sendEffect) ViewModel'de kalır ve reducer'dan SONRA çalışır. Sealed bir Command tipi zorunlu değil; sadelik önce gelir.
   DAVRANIŞ DEĞİŞMEYECEK. D18 mantığı aynen taşınacak: rationale'ın false olması tek başına "blocked" demek değil, yalnızca yükseltme zaten istenmişse blocked. Testler bir fark ortaya çıkarırsa eski davranış kazanır.

2. Testler (:feature:tracking, JVM): Start'ta precise varsa issue temizlenir; precise yokken izin akışı; approximate verilince PreciseLocationDenied; yükseltme zaten istenmişken canAskAgain=false ise PreciseLocationBlocked; konum kapalıyken LocationDisabled; ScreenResumed çözülmüş issue'yu temizler; Reset onayı ve iptali; marker seçimi ve SelectionDismissed. Sonra mutasyon: blocked/denied ayrımını ters çevir, TAM olarak ilgili testlerin kırıldığını göster, geri yükle, md5 eşit olsun.

3. Dokümanlar, testler yeşil olduktan SONRA:
   - D7: tek state, tek intent kapısı, effect kanalı VE saf reducer. Elenen alternatif: geçişleri ViewModel'e serpmek. Evidence artık Measured (testler + mutasyon).
   - README'deki MVI paragrafına reducer cümlesi; CLAUDE.md'deki "MVI TrackingViewModel" ifadesini mekanizmayla eşle; CLAUDE.md invariant'larına: state geçişleri reducer'da, yan etkiler ViewModel'de.
   - prompts.md'ye bu mesajı ekle (32. mesaj), docs/ai/README'deki numaraları güncelle, her mesajın orijinalden yalnızca [removed] işaretlerinde ayrıldığını programatik doğrula.
   - docs/ai/README'de 120 karakteri aşan satırı da sar.

4. 14:15'e kadar testler yeşil değilse DUR, bana söyle ve hiçbir şeyi zorlamadan branch'i geri al:
   git reset --hard eee740e
   O durumda yalnızca dokümanı düzelteceğiz: "tek state + tek intent kapısı + effect kanalı; ayrı bir reducer yok, geçişler ViewModel'de" diye dürüstçe yazacağız.

Testler taze (verify-claim), tarama tekrar. Commit et, push etme.

EK MADDE (kod değişikliği değil, yalnızca D21'e üç cümle):
AddressLookup'ın politikası (kayıtlı adres varsa onu döndür, yoksa çöz, yalnızca UPDATE ile yaz, çözülemezse null) :data'daki RoomAddressLookup'ta duruyor. Bunu D21'de açıkça bir karar olarak yaz: bu aslında bir use case, :core'a EnsureAddress(repository, resolver) olarak alınabilirdi ve o zaman JVM'de test edilirdi; :data'da bırakıldı çünkü tek bağımlılığı DAO ve geocoder, ve UPDATE-only kısıtı (D14) zaten DAO'nun yanında duruyor. Elenen alternatifi ve bedelini (JVM testi yerine cihaz ölçümü) yaz. Kodu DEĞİŞTİRME.
````

---

## 33. 2026-09-18 10:20 UTC

**Summary:** Review 16: the reducer round is right, but the README's Build and test section still quotes the old test counts; measure them again today before writing them.

````text
ON ALTINCI REVIEW. Reducer turu doğru. Bağımsız doğrulandı: 50 JVM testi taze XML ile 0 hata (:core 25, :feature:tracking 25); D18 terimini düşüren mutasyon TAM olarak bir testi kırdı ve geri yüklemede md5 aynı; 12 intent'in hepsinde davranış eski kodla eşdeğer (PermissionResult'ta önceki issue anlamı, StartClicked'in yan etki kapısı ve IssueActionClicked'in redüksiyon sonrası okuması dahil); prompts.md'deki 32 mesaj transcript'le birebir; tarama 0.

TEK DÜZELTME: README "Build and test" bölümü eski sayılarda.
- Satır 95: "32 JVM tests" → 50 JVM testi (:core 25, :feature:tracking 25) ve komutun ikisini de kapsadığından emin ol.
- Satır 103: "On 2026-09-17 all 38 tests passed ... (32 JVM, 6 on an API 36 emulator)" → bugünün ölçümü: 56 test (50 JVM + 6 instrumented) ve instrumented olanların telefonda koştuğu.
Sayıları verify-claim ile bugün yeniden ölçüp öyle yaz; decisions.md'deki tarihli eski satırlara DOKUNMA, onlar günlük.

Sonra bana raporla ve DUR. Merge ve push kullanıcıda.
````

---

## 34. 2026-09-18 10:34 UTC

**Summary:** The videos in the README do not play, because GitHub will not play a repository video inline: add a short GIF preview for each clip, keep the mp4s as the full clips, and record which seconds each GIF comes from.

````text
README'DEKİ VİDEOLAR OYNAMIYOR. Sebep: GitHub depo içindeki .mp4'leri Markdown'da satır içi oynatmıyor; link dosya sayfasına gidiyor ve o sayfa bu boyuttaki videoları göstermiyor ("we can't show files that are this big").

ÇÖZÜM: her klip için kısa bir GIF önizleme ekle, mp4'ler tam klip olarak kalsın.

1. Üç GIF üret, docs/media/ altına:
   - clip1: marker'ların biriktiği bir pencere seç (tek marker'lı sakin kısmı değil; 3-4 marker düşmeli), 12 s
   - clip2: marker'a dokunma ve adres kartının açılması, 8 s
   - clip3: bin marker'la kaydırma ve zoom, 8 s
   Komut kalıbı (ölçüldü: 12 s / 300px / 10 fps ≈ 0,8 MB):
   ffmpeg -ss <BAŞLA> -t <SÜRE> -i IN.mp4 -vf "fps=10,scale=300:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=3" -loop 0 OUT.gif
   Her biri ≤ 1 MB olsun; büyükse fps'i 8'e, genişliği 280'e çek.

2. README'nin Demo bölümü: her klip için önce GIF'i <img> ile satır içi göster, hemen altında mp4 linkini "full clip" olarak bırak ve tek cümleyle söyle: GitHub depo videolarını satır içi oynatmaz, link dosya sayfasını açar.

3. GIF'lerin hangi saniye aralığından alındığını ve boyutlarını doğrulama günlüğüne yaz. GIF'lerin kendisini kare kare kontrol et (gizlilik kuralı GIF için de geçerli).

4. Testleri koşturmaya gerek yok, kod değişmiyor. Taramayı yine yap. prompts.md'ye bu mesajı ekle.

Commit et, push etme. Kullanıcı push edecek.
````
