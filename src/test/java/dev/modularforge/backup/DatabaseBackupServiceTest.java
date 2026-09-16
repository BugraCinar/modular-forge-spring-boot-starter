package dev.modularforge.backup;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @TempDir
    Path tempDirectory;

    private DatabaseBackupService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseBackupService();
        ReflectionTestUtils.setField(service, "backupCipher", new BackupCipher(java.util.Base64.getEncoder().encodeToString(new byte[32])));
        ReflectionTestUtils.setField(service, "sslCa", "");
        ReflectionTestUtils.setField(service, "mysqldumpPath", "mysqldump");
        ReflectionTestUtils.setField(service, "mariadbDumpPath", "mariadb-dump");
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "databaseUrl", "jdbc:mysql://db.example.com:3307/app_db?useSSL=true");
        ReflectionTestUtils.setField(service, "databaseUsername", "backup-user");
        ReflectionTestUtils.setField(service, "databasePassword", "secret");
        ReflectionTestUtils.setField(service, "backupEnabled", true);
        ReflectionTestUtils.setField(service, "recipientEmail", "ops@example.com");
        ReflectionTestUtils.setField(service, "backupDirectory", tempDirectory.resolve("backups").toString());
        ReflectionTestUtils.setField(service, "senderEmail", "noreply@example.com");
    }

    @Test
    void skipsDisabledAndUnaddressedBackups() {
        ReflectionTestUtils.setField(service, "backupEnabled", false);
        service.createAndEmailBackup();
        verify(mailSender, never()).createMimeMessage();
        assertThat(service.getBackupStatus()).isEqualTo("Database backup is disabled");

        ReflectionTestUtils.setField(service, "backupEnabled", true);
        ReflectionTestUtils.setField(service, "recipientEmail", null);
        service.createAndEmailBackup();
        assertThat(service.getBackupStatus()).contains("no recipient email");

        ReflectionTestUtils.setField(service, "recipientEmail", "");
        service.createAndEmailBackup();
        assertThat(service.getBackupStatus()).contains("no recipient email");
    }

    @Test
    void createsEmailsAndDeletesSuccessfulDump() throws Exception {
        ReflectionTestUtils.setField(service, "dumpExecutor", successfulDump());
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.createAndEmailBackup();

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSubject()).startsWith("Modular Forge Database Backup");
        assertThat(messageCaptor.getValue().getAllRecipients()[0].toString()).isEqualTo("ops@example.com");
        assertThat(Files.list(tempDirectory.resolve("backups")).toList()).isEmpty();
        assertThat(service.getBackupStatus()).contains("ops@example.com");
    }

    @Test
    void doesNotEmailWhenDumpFailsOrProducesNoData() throws Exception {
        ReflectionTestUtils.setField(service, "dumpExecutor", (BiFunction<Object, Path, Boolean>) (target, path) -> false);
        service.createAndEmailBackup();
        verify(mailSender, never()).createMimeMessage();

        ReflectionTestUtils.setField(service, "mysqldumpPath", tempDirectory.resolve("missing-command").toString());
        Object target = ReflectionTestUtils.invokeMethod(service, "parseDatabaseTarget", "jdbc:mysql://localhost/app");
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", target,
                tempDirectory.resolve("missing.sql"))).isFalse();
    }

    @Test
    void alwaysDeletesDumpWhenEmailDeliveryFails() throws Exception {
        ReflectionTestUtils.setField(service, "dumpExecutor", successfulDump());
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new IllegalStateException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));

        service.createAndEmailBackup();

        assertThat(Files.list(tempDirectory.resolve("backups")).toList()).isEmpty();
    }

    @Test
    void validatesJdbcTargetsAndSupportsDefaultAndCustomPorts() {
        Object mysql = ReflectionTestUtils.invokeMethod(service, "parseDatabaseTarget", "jdbc:mysql://localhost/app");
        Object maria = ReflectionTestUtils.invokeMethod(service, "parseDatabaseTarget", "jdbc:mariadb://db.example.com:3308/app-1");
        assertThat(mysql.toString()).contains("host=localhost", "port=3306", "databaseName=app");
        assertThat(maria.toString()).contains("port=3308", "databaseName=app-1");

        for (String invalid : new String[] {
                null, "jdbc:postgresql://localhost/app", "jdbc:mysql:///app", "jdbc:mysql://localhost", "jdbc:mysql:opaque",
                "jdbc:mysql://localhost/invalid.name", "jdbc:mysql://localhost/-invalid",
                "jdbc:mysql://localhost:0/app", "jdbc:mysql://localhost:70000/app"
        }) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "parseDatabaseTarget", invalid))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void deleteHelperIgnoresMissingPathsAndDeletionFailures() throws Exception {
        Path missing = tempDirectory.resolve("missing.sql");
        ReflectionTestUtils.invokeMethod(service, "deleteBackupFile", missing);

        Path nonEmptyDirectory = tempDirectory.resolve("cannot-delete-as-file");
        Files.createDirectories(nonEmptyDirectory);
        Files.writeString(nonEmptyDirectory.resolve("child"), "data");
        ReflectionTestUtils.invokeMethod(service, "deleteBackupFile", nonEmptyDirectory);
        assertThat(nonEmptyDirectory).exists();
    }

    @Test
    void dumpRunnerCoversSuccessfulEmptyFailedAndTimedOutProcesses() throws Exception {
        Object target = ReflectionTestUtils.invokeMethod(service, "parseDatabaseTarget", "jdbc:mysql://localhost/app");
        Path output = tempDirectory.resolve("dump.sql");
        Process process = mock(Process.class);
        when(process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        Files.writeString(output, "backup");
        ReflectionTestUtils.setField(service, "processStarter",
                (DatabaseBackupService.ProcessStarter) builder -> process);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", target, output)).isTrue();

        Files.delete(output);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", target, output)).isFalse();

        Files.createFile(output);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", target, output)).isFalse();
        Files.delete(output);

        when(process.exitValue()).thenReturn(9);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", target, output)).isFalse();

        Process timeout = mock(Process.class);
        when(timeout.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)).thenReturn(false);
        when(timeout.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(timeout.destroyForcibly()).thenReturn(timeout);
        ReflectionTestUtils.setField(service, "processStarter",
                (DatabaseBackupService.ProcessStarter) builder -> timeout);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", target, output)).isFalse();
        verify(timeout).destroyForcibly();
    }

    @Test
    void backupHandlesFailureBeforeAPathExistsAndAnExistingDirectory() throws Exception {
        ReflectionTestUtils.setField(service, "backupDirectory", (Object) null);
        service.createAndEmailBackup();

        Path existing = tempDirectory.resolve("existing");
        Files.createDirectories(existing);
        ReflectionTestUtils.setField(service, "backupDirectory", existing.toString());
        ReflectionTestUtils.invokeMethod(service, "createBackupDirectory");
        assertThat(existing).isDirectory();
    }

    private BiFunction<Object, Path, Boolean> successfulDump() {
        return (target, path) -> {
            try {
                Files.writeString(path, "database backup", StandardCharsets.UTF_8);
                return true;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        };
    }

    @Test void backupRejectsOtherProvidersAndKeepsTlsVerificationInDumpCommand() throws Exception {
        ReflectionTestUtils.setField(service, "provider", "mongodb");
        assertThatThrownBy(service::validateProvider).isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.setField(service, "provider", "sql"); service.validateProvider();
        ReflectionTestUtils.setField(service, "sslCa", "/test/ca.pem");
        ReflectionTestUtils.setField(service, "processStarter", (DatabaseBackupService.ProcessStarter) builder -> {
            assertThat(builder.command()).contains("mariadb-dump", "--ssl-verify-server-cert", "--ssl-ca=/test/ca.pem");
            assertThat(builder.command()).noneMatch(arg -> arg.contains("secret"));
            throw new java.io.IOException("test does not launch a process");
        });
        Object maria = ReflectionTestUtils.invokeMethod(service, "parseDatabaseTarget", "jdbc:mariadb://localhost/app");
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", maria, tempDirectory.resolve("dump.sql"))).isFalse();
    }


    @Test void nullCaStillRequiresCertificateAndHostnameValidation() {
        ReflectionTestUtils.setField(service, "sslCa", (Object) null);
        ReflectionTestUtils.setField(service, "processStarter", (DatabaseBackupService.ProcessStarter) builder -> {
            assertThat(builder.command()).contains("--ssl-mode=VERIFY_IDENTITY");
            assertThat(builder.command()).noneMatch(value -> value.startsWith("--ssl-ca="));
            throw new java.io.IOException("test does not launch a process");
        });
        Object target = ReflectionTestUtils.invokeMethod(service, "parseDatabaseTarget", "jdbc:mysql://localhost/app");
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "createMySQLDump", target, tempDirectory.resolve("dump.sql"))).isFalse();
    }
}
