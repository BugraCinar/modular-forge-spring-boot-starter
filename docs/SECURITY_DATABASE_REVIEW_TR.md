# Güvenlik ve veritabanı incelemesi

Tarih: 15 Eylül 2026

## Sonuç

Projenin güvenlik temeli iyi: Argon2id, pepper, HMAC ile token saklama, JWT algoritma/issuer doğrulaması, hesap durumu ve authVersion kontrolü, refresh rotation, kısıtlı CORS ve cookie-CSRF koruması mevcut. Ancak aşağıdaki iş akışı açıkları giderilmeden üretime çıkılmasını önermiyorum.

MySQL yapılandırması ve JDBC sürücüsü mevcut. MongoDB desteği mevcut değil. MongoDB'yi MySQL yerine seçilebilir yapmak, yalnızca bağlantı adresi eklemekten çok daha kapsamlı bir değişiklik gerektiriyor.

Bu çalışma inceleme ve yerel doğrulamadır. Üretim kodu değiştirilmedi; gerçek hesaplara, SMTP'ye, R2'ye veya uzak veritabanlarına işlem yapılmadı.

## Doğrulama ve sınırlar

- `mvnw.cmd -B -ntp clean verify`: **697 test, 0 hata, 0 başarısızlık**. JaCoCo line/branch/method eşikleri geçti.
- Geçici `SecurityReviewProbeTest`: **7 senaryo çalıştırıldı ve mevcut sorunlu davranışlar doğrulandı**. Bunların geçmesi güvenli davranış anlamına gelmez; mevcut açıkların yeniden üretildiği anlamına gelir. Kaynak [review/SecurityReviewProbeTest.java](review/SecurityReviewProbeTest.java) altında saklandı ve normal test kaynaklarından çıkarıldı.
- İnceleme testleri gerçek controller/service/JWT kodunu kullanır; veritabanı, mail ve depolama bağımlılıkları mock'tur. HTTP sunucusu, gerçek veritabanı eşzamanlılığı veya tarayıcı uçtan uca testi değildir.
- Repo CI'sindeki NVD feed güncellemesi tamamlandı; ardından `-Psecurity-audit dependency-check:check -DautoUpdate=false` başarıyla çalıştı. **131 bağımlılık kaydında raporlanan zafiyet yok. NVD verisinin son değişiklik tarihi 14 Eylül 2026 16:17 UTC, son kontrolü 15 Eylül 2026 10:57 UTC.** Sonatype OSS Index kimlik bilgileri olmadığı için devre dışıydı. Bu sonuç uygulamanın iş akışı açıklarını veya henüz veritabanına girmemiş zafiyetleri kapsamaz.
- İlk güncelleme denemesi disk alanı hatası nedeniyle durduruldu. Kullanıcı alan açtıktan sonra güncelleme ve tarama yeniden başarıyla tamamlandı. İnceleme testlerinin son çalışması da 7/7 başarılıdır.
- Gerçek MySQL/MongoDB testleri çalıştırılmadı; yerel PATH üzerinde Docker/MySQL/MongoDB sunucu araçları bulunamadı. Mevcut entegrasyon testleri H2 kullanıyor.
- Ayrıntılı çalışma logları `target/review/`, bağımlılık HTML/JSON raporu `target/dependency-check-report.*` altında. `clean` bu çıktıları siler.

## Öncelikli bulgular

Java kaynak referansları `src/main/java/dev/modularforge/` dizinine göredir; template referansları `src/main/resources/` altındadır. Öncelikler bu projenin akışlarına göre verilmiştir; CVSS puanı değildir.

### S1 — Yüksek: profil güncellemesiyle kurtarma e-postası değiştirilebiliyor

**Kaynak:** `profile/UserProfileService.java:53`, `admin/AdminProfileService.java:50`; ilgili PUT yolları `/api/v1/profile` ve `/api/v1/admin/profile`.

Genel profil DTO'sundaki `email`, mevcut şifre sorulmadan ve yeni adres doğrulanmadan hesaba yazılıyor. Kullanıcıda `emailVerified=true` değeri de korunuyor. Oysa ayrı `requestEmailChange` akışı şifre ve doğrulama talep ediyor; PUT yolu bunu atlıyor.

**Koşul/etki:** Geçerli access token ele geçiren kişi e-postayı kendi adresine çevirip şifre sıfırlama üzerinden hesabı kalıcı olarak ele geçirebilir. Yönetici e-postasında da aynı sorun var; etkin 2FA ayrıca değerlendirilmelidir.

**Düzeltme:** Genel profil güncellemesinden e-postayı çıkar. Kullanıcı ve yönetici için yeniden kimlik doğrulama, pending email, tek kullanımlık doğrulama ve eski adrese bildirim içeren ortak bir iş akışı kullan. İki inceleme senaryosunda doğrulandı.

### S2 — Yüksek: 2FA setup mevcut ikinci faktörü doğrulamadan kapatıyor

**Kaynak:** `twofactor/TwoFactorAuthService.java:85`, özellikle `credential.setEnabled(false)`; `TwoFactorAuthController.java:45`.

`POST /api/v1/admin/2fa/setup`, var olan aktif credential'ı yeni secret ile değiştiriyor ve 2FA'yı hemen kapatıyor. Yalnızca admin access token gerekli; mevcut TOTP veya şifre istenmiyor.

**Koşul/etki:** 2FA modülü açıkken ele geçirilen admin oturumuyla ikinci faktör devre dışı bırakılabilir veya saldırganın faktörü kurulabilir. S1 ile birleşmesi riski artırır. İnceleme testi eski secret'ın değiştiğini ve enabled değerinin false olduğunu doğruladı.

**Düzeltme:** Aktif credential varken sıradan setup çağrısını reddet. Faktör değişiminde mevcut faktörü yeniden doğrula; yeni secret'ı ayrı pending kayıt olarak tut ve doğrulanana kadar eski faktörü koru. [OWASP faktör değiştirme rehberi](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html) mevcut oturuma tek başına güvenilmemesini öneriyor.

### S3 — Yüksek: alternatif admin profil yolları yetki kuralını aşıyor

**Kaynak:** `admin/AdminProfileController.java:66`, `AdminProfileService.java:126` ve `:149`; karşılaştırma için `AdminManagementService.java:64`.

Ana yönetim akışı level 2 moderatörün başka bir yöneticiyi yönetmesini engelliyor. Profil altındaki `/{adminId}/deactivate` ve `/{adminId}/reactivate` yolları level 2 dahil açılıyor; servis yalnızca `requestingLevel > targetLevel` koşulunu reddediyor. Eşit seviye kabul ediliyor.

**Etki:** Level 2 admin başka bir level 2 admin'i devre dışı bırakabilir veya askıya alınmış eş seviyedeki hesabı etkinleştirebilir. Level 1 için de eş seviye yönetimi açılıyor. Devre dışı bırakma inceleme testinde doğrulandı.

**Düzeltme:** Tek bir admin yetki politikası kullan; bütün alternatif HTTP yolları aynı actor/target/action kontrolünden geçsin. Aktifleştirme/devre dışı bırakma için rol-seviye test matrisi ekle.

### S4 — Orta: role gönderilmeyince yönetici CAPTCHA kontrolü atlanıyor

**Kaynak:** `auth/AuthController.java:97`, `AuthService.java:150`, `auth/dto/LoginRequest.java:29`.

Controller CAPTCHA'yı yalnızca istemcinin `role=admin` gönderdiği durumda denetliyor. Role null/boş olabilir; servis bu durumda önce kullanıcıyı, sonra admin'i arıyor.

**Yeniden üretim:** CAPTCHA etkin, doğru admin kullanıcı adı/şifresi, role ve CAPTCHA alanları yok. Gerçek controller/service ile 200 admin yanıtı alındı ve CAPTCHA servisi çağrılmadı. Bu açık şifreyi veya 2FA'yı kendiliğinden atlamaz; otomasyona karşı ek korumayı atlar.

**Düzeltme:** Kontrolü sunucuda çözümlenen hesap türüne bağla veya admin için ayrı, zorunlu doğrulama akışı kullan.

### S5 — Orta: resim sahipliği kullanıcı tarafından değiştirilebilir URL'ye dayanıyor

**Kaynak:** `admin/AdminProfileService.java:66`, `:158`; `storage/r2/AdminImageController.java:101`; `ImageUploadService.java:207`.

Admin profil PUT isteği herhangi bir `profilePicture` URL'sini kaydediyor. Silme yetkisi bu kayıtla istemcinin URL'sinin eşitliğine bakıyor. Depolama servisi origin ve key biçimini kontrol ediyor fakat nesnenin gerçek sahibini kontrol etmiyor.

**Koşul/etki:** R2 ve admin modülleri açıkken admin, URL'sini bildiği başka hesabın aynı storage origin'inde bulunan profil resmini kendi profil alanına yazıp silme yolundan sildirebilir. İnceleme testi başka hesabın URL'si yazıldıktan sonra sahiplik kontrolünün true döndüğünü doğruladı; gerçek R2 silmesi yapılmadı.

**Düzeltme:** Sahipliği sunucunun oluşturduğu object key ile owner ID/role kaydına bağla; serbest URL alanı silme yetkisi kazandırmasın. Değiştirme sonrası eski resim temizliği de aynı kurala tabi olsun.

### S6 — Orta: logout-all mevcut access token'ları iptal etmiyor

**Kaynak:** `auth/token/RefreshTokenController.java:202`; `security/JwtAuthFilter.java:93`.

Logout-all yalnızca refresh kayıtlarını iptal ediyor. User/Admin `authVersion` artırılmadığından mevcut access JWT geçerli kalıyor. Varsayılan pencere 15 dakika; yapılandırma 24 saate kadar izin veriyor.

**Kanıt:** Gerçek imzalı JWT ile logout-all çağrıldı; aynı JWT daha sonra gerçek JwtAuthFilter tarafından tekrar kabul edildi.

**Düzeltme:** Tüm cihazlardan çıkışta authVersion artırımı ve refresh iptalini tek transaction içinde yap. Tek cihazdan anlık çıkış gerekiyorsa session ID veya ayrı revocation tasarımı ekle.

### S7 — Orta: refresh geçmişi erken temizlendiği için tekrar kullanım tespiti kayboluyor

**Kaynak:** `auth/token/RefreshTokenRepository.java:55`, `RefreshTokenService.java:65`, `RefreshTokenCleanupScheduledService.java:15`.

Gece temizliği `isRevoked=true OR expiryDate<now` ile bütün iptal edilmiş token'ları siler. Rotation eski token'ı revoked yaptığı için henüz doğal süresi dolmamış geçmiş de silinir. Silinmiş eski token tekrar geldiğinde “bulunamadı” yanıtı verilir; tekrar kullanım için aktif token'ları iptal eden dal çalışmaz.

**Etki:** Eski token tekrar geçerli olmaz; kaybolan koruma, tekrar kullanımı algılayıp saldırganın elinde kalmış yeni token'ları iptal etmektir. Bu bulgu kod akışıyla doğrulandı.

**Düzeltme:** Revoked hash kayıtlarını asıl expiry sonuna kadar tut. Token aileleri/session ilişkisi tanımlayarak replay durumunda ilgili ailenin refresh ve access yetkisini iptal et.

### S8 — Orta: audit kaydı ham query string içindeki token'ı saklıyor

**Kaynak:** `audit/UserActivityLoggingInterceptor.java:67`, `UserActivityLogger.java:47`.

Audit interceptor kimliği doğrulanmış USER isteklerinde query string'i ayıklamadan JSON'a yazıyor. Örneğin bearer kimliği olan `/api/v1/auth/reset-password?token=...` isteği reset token'ını audit tablosuna taşır.

**Kanıt/sınır:** İnceleme testi interceptor ve logger üzerinden repository'ye giden details içinde ham token'ı doğruladı. Normal anonim e-posta linki ziyaretinde bu USER koşulu oluşmaz; bütün ziyaretlerde sızıntı var denemez.

**Düzeltme:** Query parametrelerini izin listesiyle kaydet; token, code, secret ve credential değerlerini hiçbir log katmanına gönderme. Exception metinleri için de merkezi maskeleme uygula.

### S9 — Orta, işlevsel: CSP parola sıfırlama sayfasının JavaScript'ini engelliyor

**Kaynak:** `security/SecurityConfig.java:99`, `resources/templates/reset-password-page.html:44`.

Politika `default-src 'self'` diyor; sayfada nonce/hash bulunmayan inline script ve style var. Uyumlu tarayıcı inline script'i çalıştırmayınca formun fetch ile JSON gönderme kodu devreye giremez. Bu sonuç statik header/template incelemesidir; tarayıcı testi yapılmadı. [CSP standardı](https://www.w3.org/TR/CSP3/#directive-script-src) inline kod için ayrı izin gerektirir.

**Düzeltme:** Script/style için nonce veya hash kullan ya da bunları izin verilen ayrı dosyalara taşı. Ayrı dosya seçilirse mevcut kapalı static resource mappings ayarını da düzenle. Şifre sıfırlama ve Swagger için tarayıcı testi ekle.

### S10 — Orta, koşullu: backup bağlantısı JDBC TLS politikasını uygulamıyor

**Kaynak:** `backup/DatabaseBackupService.java:125` ve `:197`.

JDBC URL'sinden yalnızca host/port/database çıkarılıp mysqldump'a aktarılıyor; sertifika/hostname doğrulama seçenekleri aktarılmıyor. Bu yolun güvenliği mysqldump'ın dış konfigürasyonuna bağlı. Ayrıca SQL dump uygulama seviyesinde şifrelenmeden e-posta eki oluyor; SMTP TLS, arşiv dosyasını uçtan uca şifrelemez.

**Düzeltme:** Backup için bağımsız ve zorunlu TLS/CA politikası uygula. MySQL istemcileri için [resmi TLS seçenekleri](https://dev.mysql.com/doc/refman/8.4/en/using-encrypted-connections.html) kullanılabilir; MariaDB istemcisinin uyumlu seçeneklerini ayrı adaptörde ele al. Yedeği şifreli depoya, sınırlı erişim ve retention ile yaz; restore testi ve başarısızlık bildirimi ekle. Modül varsayılan kapalıdır.

## Veritabanı uygunluğu

| Veritabanı | Mevcut durum | İnceleme sonucu |
|---|---|---|
| MySQL | Maven profili/sürücü, Spring profili, TLS varsayılanı ve mysqldump yolu var | Mevcut mimariye uygun; üretim migration ve gerçek DB testleri eksik |
| PostgreSQL | Maven ve Spring profilleri var | Sürücü desteği var; gerçek sorgu/JSON uyumluluğu kanıtlanmış değil; backup adaptörü yok |
| MariaDB | Maven ve Spring profilleri var | Gerçek DB ve dump istemcisi uyumluluğu ayrıca test edilmeli |
| H2 | Mevcut test altyapısı | MySQL/PostgreSQL davranışının kanıtı olarak kullanılamaz |
| MongoDB | Driver, profil, document/repository adaptörü yok | Şu an desteklenmiyor; JPA yerine URI değiştirmek yeterli değil |

### MySQL için tamamlanması gerekenler

1. **Migration:** Flyway/Liquibase ve başlangıç şeması yok. `prod` validate kullanıyor; boş üretim DB'sini hazırlayamıyor. Varsayılan profil yalnızca mysql ve varsayılan DDL davranışı update. Üretimde `mysql,prod` profili, sürümlenmiş migration ve sınırlı yetkili runtime DB hesabı kullan.
2. **Gerçek test:** `.github/workflows/ci.yml:40` matrisinde `-DskipTests package` var. Bu yalnızca paketleme kontrolü. MySQL container'ında migration, register/login, email uniqueness, audit insert, refresh rotation ve eşzamanlı password reset testleri çalışmalı.
3. **Eşzamanlılık:** User/Admin üzerinde JPA `@Version` yok. `authVersion` bir JWT iptal sayacı; optimistic lock değildir. Login attempt ve parola/authVersion güncellemeleri eski kayıtla birbirini ezebilir. Atomik update, uygun kilit veya optimistic locking tasarla; gerçek DB'de yarış testiyle doğrula. Bu yarışlar bu çalışmada çalıştırılmadı.
4. **Kimlik tekilliği:** User/admin tablolarındaki ayrı unique kısıtları, iki tablo arasındaki aynı e-posta/kullanıcı adını atomik engellemez. Ortak identity tablosu veya atomik isim rezervasyonu değerlendir. E-posta/kullanıcı adı normalleştirmesi ve collation kuralları bütün yazma yollarında tutarlı olsun.
5. **JSON taşınabilirliği:** Audit entity'leri `String + columnDefinition="JSON"` kullanıyor. Özellikle PostgreSQL için Hibernate JDBC JSON eşlemesi ve gerçek insert/read testi gerekir. Profil bulunmasını tam taşınabilirlik olarak kabul etme.
6. **Performans:** Token filtreleme bütün sonucu List olarak çekiyor, her kayıt için kullanıcı adını ayrıca sorguluyor. Sayfalama ve toplu kullanıcı yükleme uygula. Gerçek sorgu planlarına göre composite index seç; mevcut unique indexlerle örtüşen indexleri gözden geçir.
7. **Zaman ve bağlantı:** LocalDateTime yerine mümkün olan yerlerde UTC Instant/Clock kullan; JVM/DB zaman dilimlerini sabitle. Havuz boyutlarını instance sayısı ve DB connection bütçesine göre belirle.

### MongoDB + MySQL için önerilen iki seçenek

**A. Birlikte kullanım:** Bu proje için daha küçük değişiklik, hesaplar/roller/token'ları MySQL'de tutup ihtiyaç oluşursa audit veya belge özelliklerini MongoDB'ye ayırmak. İki DB'ye yazmayı tek bir yerel transaction gibi varsayma; outbox ve tekrar denemede mükerrer işlem yapmama tasarımı gerekir.

**B. Birbirinin yerine seçilebilir sağlayıcı:** Kullanıcı/admin, token ve audit servislerinin doğrudan JpaRepository bağımlılıklarını port arayüzlerinin arkasına al. JPA/MySQL ve MongoDB adaptörleri aynı sözleşmeyi uygulasın. Her feature kendi persistence koduna sahip olmaya devam etsin.

Gerekli işler:

- MongoDB driver/config ve JPA'dan ayrılmış `@Document` modelleri; Mongo modunda JDBC/JPA auto-configuration ve repository taramasının doğru kapatılması.
- Long ID/IDENTITY varsayımlarına karşı ortak ID stratejisi; DTO/JWT referanslarının uyumu.
- E-posta/username unique indexleri; normalleştirme ve create/update davranışlarının aynı olması.
- Refresh tüketiminin koşullu atomik update ile yalnız bir çağrıya izin vermesi; replay geçmişinin korunması.
- Çok belgeli atomiklik gereken yerlerde MongoTransactionManager ve transaction destekleyen replica set/sharded ortam. [Spring Data MongoDB belgesi](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html), transaction manager tanımlanmadan transaction desteğinin etkin olmadığını açıklar. Tek belgeli işlemlerin atomikliği ayrıca kullanılabilir.
- Expiry ve log retention için tarih alanları/TTL indexleri. TTL temizliğini anlık güvenlik kontrolü sayma; expiry uygulama içinde de denetlensin.
- JOIN/lazy relation yerine belge veya referans tasarımı; JPQL yerine Mongo sorguları/aggregation; ortak pagination ve sort izin listesi.
- Seçilen DB'ye uygun health/readiness ve backup adaptörü.
- Aynı davranış testlerini MySQL ve gerçek Mongo replica set üzerinde çalıştır; zorunlu modüller ve kapalı modüller de matrise girsin.

Önerim: önce S1–S6'yı düzeltip MySQL'i migration ve gerçek entegrasyon testleriyle sağlamlaştırmak. MongoDB'yi eklerken A/B kararını kullanım ihtiyacına göre vermek; yalnız sürücüyü ekleyerek “MongoDB destekli” ilan etmemek.

## Diğer iyileştirmeler

- Modül sınırlarında kalan bağımlılıkları azalt: `profile/UserProfileService.java:11` ve `UserProfileController.java:6`, opsiyonel admin modülünün `ChangePasswordRequest` DTO'sunu kullanıyor. Admin paketi fiziksel kaldırılınca profil derlemesi bozulur. Sözleşmeyi auth/shared veya profil sahipliğine taşı ve mimari kurallarla kapsa.
- `AuthService` aktif olmayan/doğrulanmamış/kilitli hesaplar için parola doğrulamadan farklı mesajlar veriyor. Kullanıcı keşfini azaltmak için dış yanıtları tutarlı yap.
- Forgot-password yalnız global IP limitine dayanıyor. Normalize hesap + IP bazında ayrı limit, bildirim bütçesi ve kuyruk koruması ekle.
- `UserActivityLogger` içindeki annotated metoda aynı nesne içinden yapılan çağrılar varsayılan proxy modunda async sınırını geçmez. Ayrı writer bean veya olay işleyici kullan; HttpServletRequest'i async işlere taşımadan önce gereken alanları immutable snapshot'a al. [Spring async dokümanı](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).
- Auth işlemleri transaction içinde broad catch kullanıyor; kontrollü hata dönüşlerinin transaction rollback ve yan etkileri nasıl etkilediğini test et. E-posta için commit sonrası olay/outbox tercih et.
- 2FA challenge'ını authVersion/oturum nesline bağla; parola sıfırlama, logout-all ve askıya alma sırasında eski challenge'ın kullanımını reddet. Şu an bu yaşam döngüsü ayrıca tasarlanmalı.
- JWT her claim için yeniden parse ediliyor. Filtrede bir kez doğrula, claim'leri bir kez çıkar; rol/seviye için mevcut DB kontrolünü koru.
- `AdminManagementService.updateAdmin` nullable eski ad/soyad değerlerini `Map.of` içine veriyor; null değer 500'e neden olabilir. Null kabul eden change kaydı kullan.
- Yüzde 100 coverage hedefi güvenlik senaryosu kapsamı anlamına gelmiyor. Bu incelemedeki probe'ları düzeltme sonrası güvenli davranış bekleyen regression testlerine dönüştür; yarış, yetki matrisi ve gerçek tarayıcı testlerini ekle.

## Önerilen sıra

1. E-posta değiştirme, 2FA setup, alternatif admin yetkileri, CAPTCHA, resim sahipliği ve logout-all düzeltmeleri.
2. Replay geçmişi ve audit redaction; CSP/reset sayfası; backup güvenliği.
3. Migration, MySQL entegrasyon testleri, eşzamanlılık/unique kuralları.
4. İhtiyaca göre MongoDB audit adaptörü veya tam sağlayıcı ayrımı ve iki DB için ortak test sözleşmesi.
