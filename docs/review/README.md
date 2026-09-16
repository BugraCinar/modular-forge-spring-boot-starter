# İnceleme senaryoları

`SecurityReviewProbeTest.java`, 15 Eylül 2026 incelemesinde kullanılan yedi yerel yeniden üretim senaryosudur. **Assertion'lar mevcut güvensiz davranışı bekler.** Bunlar düzeltme sonrası güvenli davranış bekleyecek şekilde değiştirilmeli; mevcut halleri normal CI testlerine eklenmemelidir.

Sonuç: 7 test, 0 failure, 0 error. DB/mail/depolama mock kullanır. Gerçek HTTP sunucusu ve Spring transaction proxy'leri başlatılmaz.

| Senaryo | İlgili bulgu |
|---|---|
| User profil PUT işlemi doğrulanmış e-postayı şifresiz değiştiriyor | S1 |
| Role gönderilmeden gerçek auth controller/service CAPTCHA'yı atlıyor | S4 |
| Tekrarlanan 2FA setup etkin faktörü kapatıyor | S2 |
| Level 2 admin eş seviyedeki başka admin'i kapatabiliyor | S3 |
| Admin profil değişikliği kurtarma e-postasını ve resim sahipliği kontrolünü etkiliyor | S1, S5 |
| Logout-all sonrası aynı gerçek imzalı JWT, JwtAuthFilter tarafından kabul ediliyor | S6 |
| USER kimliği olan reset sayfası isteğinin ham token'ı audit repository'ye ulaşıyor | S8 |

Yerel yeniden çalıştırma: dosyayı geçici olarak `src/test/java/dev/modularforge/twofactor/SecurityReviewProbeTest.java` yoluna kopyalayıp proje kökünde `mvnw.cmd -B -ntp -Dtest=SecurityReviewProbeTest test` çalıştır. Ardından yalnızca kopyaladığın geçici dosyayı kaldır. Aynı isimde mevcut test varsa üzerine yazma.

Tam rapor: [Güvenlik ve veritabanı incelemesi](../SECURITY_DATABASE_REVIEW_TR.md).
