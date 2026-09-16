# Uygulanan güvenlik ve veritabanı değişiklikleri

16 Eylül 2026. İlk inceleme: [bulgular](SECURITY_DATABASE_REVIEW_TR.md).

## Güvenlik düzeltmeleri

| Bulgu | Uygulanan değişiklik |
|---|---|
| S1 | Profil güncellemesiyle e-posta değiştirme engellendi. Ortak akış mevcut parola, yeni adrese tek kullanımlık doğrulama ve hesap sürümü kontrolü gerektirir. Eski adrese bildirim gönderilir; onayda oturumlar iptal edilir. |
| S2 | Etkin 2FA kurulumu yeni setup isteğiyle değiştirilemez. |
| S3 | Yönetici pasifleştirme/yeniden açma hiyerarşiye uyar; kendini, eş düzeyi ve üst düzeyi yönetme girişimleri reddedilir. |
| S4 | Rolü belirtilmemiş girişler de etkin CAPTCHA kontrolünden geçer. |
| S5 | Doğrudan profil resmi URL'si değiştirilemez. R2 silme/yenileme nesne anahtarındaki rol ve hesap kimliğini doğrular. |
| S6 | Logout-all ve refresh token tekrar kullanımı hesap sürümünü artırır; mevcut access token'lar da geçersizleşir. Refresh token'lar verildikleri sürüme bağlıdır. |
| S7 | İptal edilmiş refresh token geçmişi süresi dolana kadar korunur. |
| S8 | Audit kaydından query string kaldırıldı; token içerebilecek istisna mesajı saklanmaz. |
| S9 | Her yanıta özel CSP nonce, HTML script/style etiketlerine taşınır. Parola sıfırlama sayfası bu başlıkla birlikte test edilir. |
| S10 | Dump bağlantısında sertifika/hostname doğrulaması zorunlu; yedek e-postadan önce AES-256-GCM ile şifrelenir. Ayrı anahtar ve doğrulamalı geri yükleme aracı eklendi. |

Hesap ve 2FA kayıtlarında optimistic locking eklendi. Eski TOTP challenge'ları parola/oturum sürümü değişikliği, kilitlenme veya pasifleştirmeden sonra kabul edilmez. Kod tüketimi ve başarılı yönetici giriş kaydı aynı transaction içindedir. Paylaşılan parola/e-posta DTO'ları opsiyonel modüllerden çıkarıldı; nullable yönetici değişiklik kayıtları düzeltildi.

## Veritabanı mimarisi

İş servisleri sağlayıcıdan bağımsız repository sözleşmelerini kullanır. Her özellik kendi JPA ve MongoDB adaptörlerine sahiptir. MongoDB seçildiğinde hesap, token, audit ve TOTP verisi birlikte MongoDB'ye gider.

SQL tarafında dört motor için Flyway V1/V2 ve varsayılan `validate` bulunur. MongoDB tarafında replica set kontrolü, numeric kimlik üretimi, benzersiz/sorgu indeksleri ve transaction manager vardır. Ortak sözleşme testleri iki sağlayıcıda aynı iş kurallarını sınar. Ayrıntılar: [kurulum ve yükseltme](DATABASES.md).

## Kafka modülü

Veritabanından bağımsız, varsayılan olarak kapalı uygulama olayı modülü eklendi. İlk bağlı olay, kullanıcı/yönetici e-posta değişikliğinin doğrulanmasıdır. Mesaj yalnızca işlem commit edildikten sonra gönderilir; rollback ve transaction dışı yayınlar gönderilmez. Mesajda e-posta, parola veya token bulunmaz. Başarı/başarısızlık sayaçları, TLS varsayılanı, süre sınırları ve idempotent producer yapılandırması vardır.

Bu sürüm kalıcı outbox içermez; commit sonrası çökme veya broker kesintisinde olay kaybı mümkündür. Kafka hatası tamamlanmış hesap değişikliğini geri almaz. Ayrıntılı sözleşme, kurulum, tüketici ekleme ve kaldırma: [Kafka rehberi](modules/kafka.md).

## Dağıtım notları

- Mevcut SQL veritabanı için şema karşılaştırması, doğrulanmış V1 baseline ve V2 migration gerekir. Önce geri yüklemesi sınanmış yedek alın; UTC geçişini tarih kolonları için planlayın. Mevcut uygulama veritabanına bağlanılıp veri dönüştürülmedi.
- MongoDB için replica set/sharded cluster gerekir. Profil değiştirmek mevcut SQL verisini taşımaz.
- Şifreli dump modülü MySQL/MariaDB ile sınırlıdır. Diğer sağlayıcılarda uygun yedekleme çözümü gerekir.
- Eski refresh token'lar ve bekleyen e-posta/2FA challenge'ları yeni sürüm bağını taşımadığı için yeniden giriş/istek gerekir.

## Sonraki iyileştirmeler ve sınırlar

Ortak kullanıcı/yönetici kimlik alanını tek atomik unique kayıtla koruma, bütün giriş noktalarında tutarlı e-posta/username normalizasyonu, hesaba özel parola sıfırlama bütçesi, genel kimlik doğrulama hata mesajları, kalıcı bildirim kuyruğu, audit async sınırı ve JWT'yi bir kez parse etme ek iyileştirmelerdir. Bunlar tamamlanmış kabul edilmemelidir. Tablo/collection bazında unique indeksler vardır; iki hesap türü arasındaki ön kontrol ortak atomik unique kısıtı değildir.

Gerçek SMTP teslimatı, R2 hesabı, üretim TLS sertifikaları, yük testi ve mevcut veriyi motorlar arasında taşıma test kapsamı dışındadır.

## Doğrulama sonuçları

- Kafka dahil son `mvnw clean verify`: 746 test geçti; hata/başarısızlık/atlanan test yok. Satır (5380), dal (1642) ve metot (845) kapsamı %100.
- Gerçek MySQL 8.4.11: ortak sözleşmedeki 7 test geçti.
- Gerçek MongoDB 8.0.32, replica set: aynı 7 test ve MongoDB altyapı testleri geçti.
- PostgreSQL, MariaDB, H2 ve MongoDB Maven profilleri derlendi. PostgreSQL/MariaDB için canlı sunucu testi yapılmadı.
- Şifre çözme aracı: geri yükleme ve değiştirilmiş dosyanın reddi gerçek Java CLI ile sınandı.
- Kafka mesajının gerçek gömülü KRaft broker'ına ulaşması ve tüketilmesi geçti. Transaction commit/rollback, kapalı modül, hata sayaçları ve hassas veri içermeyen mesaj sözleşmesi test edildi.
- Kafka eklenmiş bağımlılık taraması: 152 bağımlılık; yüksek/kritik bulgu yok. `kafka-clients-4.2.1` için CVE-2026-41115 (CVSS 4.3) raporlandı ve bastırılmadı. [Apache açıklaması](https://kafka.apache.org/community/cve-list/) broker GROUP DESCRIBE/READ yetkilerinin gözden geçirilmesini ister; istemci kodu düzeltmesi önermez. Bu modül yalnızca producer'dır, runtime broker içermez.
- Güncel NVD API isteği hata verdi. Tekrar tarama, 48 saatlik geçerlilik penceresiyle yerel NVD verisini kullandı: son kontrol 2026-09-15T10:57:42Z, son değişiklik 2026-09-14T16:17:43Z. Bu sonuç güncel veri indirmesinin başarılı olduğu anlamına gelmez. CISA KEV resmî GitHub aynasından kontrol edildi; Sonatype OSS Index kimlik bilgisi olmadığı için çalışmadı.

Komutlar ve geçiş adımları [DATABASES.md](DATABASES.md) içindedir. Ayrıntılı yerel çıktılar `target/review` altında tutulur; `clean` bu üretilmiş çıktıları siler.
