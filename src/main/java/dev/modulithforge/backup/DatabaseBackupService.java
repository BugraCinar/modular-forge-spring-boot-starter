package dev.modulithforge.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "app.modules.database-backup", name = "enabled", havingValue = "true")
public class DatabaseBackupService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseBackupService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    @Value("${app.database.backup.enabled:true}")
    private boolean backupEnabled;

    @Value("${app.database.backup.recipient-email:}")
    private String recipientEmail;

    @Value("${app.database.backup.directory:backups}")
    private String backupDirectory;

    @Value("${app.database.backup.mysqldump-path:mysqldump}")
    private String mysqldumpPath;

    @Value("${app.email.sender}")
    private String senderEmail;
    public void createAndEmailBackup() {
        if (!backupEnabled) {
            logger.info("Database backup is disabled");
            return;
        }

        if (recipientEmail == null || recipientEmail.isEmpty()) {
            logger.warn("No recipient email configured for database backup");
            return;
        }

        Path backupPath = null;
        try {
            createBackupDirectory();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFilename = String.format("modulithforge_backup_%s.sql", timestamp);
            backupPath = Paths.get(backupDirectory, backupFilename);
            DatabaseTarget target = parseDatabaseTarget(databaseUrl);
            boolean backupSuccess = createMySQLDump(target, backupPath);

            if (backupSuccess) {
                emailBackup(backupPath.toFile(), timestamp, target.databaseName());

                logger.info("Database backup completed successfully and emailed to: {}", recipientEmail);
            } else {
                logger.error("Failed to create database backup");
            }

        } catch (Exception e) {
            logger.error("Error during database backup process: {}", e.getMessage(), e);
        } finally {
            if (backupPath != null) {
                deleteBackupFile(backupPath);
            }
        }
    }
    private void createBackupDirectory() throws IOException {
        Path backupDir = Paths.get(backupDirectory);
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
            logger.info("Created backup directory: {}", backupDir.toAbsolutePath());
        }
    }
    private DatabaseTarget parseDatabaseTarget(String jdbcUrl) {
        if (jdbcUrl == null || (!jdbcUrl.startsWith("jdbc:mysql://") && !jdbcUrl.startsWith("jdbc:mariadb://"))) {
            throw new IllegalStateException("The database backup module supports only MySQL and MariaDB JDBC URLs");
        }

        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = uri.getHost();
        String path = uri.getPath();
        String databaseName = path == null || path.length() < 2 ? "" : path.substring(1);
        if (host == null || host.isBlank() || !databaseName.matches("[A-Za-z0-9_$-]{1,64}")
                || databaseName.startsWith("-")) {
            throw new IllegalStateException("The JDBC URL contains an unsupported database host or name");
        }
        int port = uri.getPort() == -1 ? 3306 : uri.getPort();
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("The JDBC URL contains an invalid database port");
        }
        return new DatabaseTarget(host, port, databaseName);
    }

    private boolean createMySQLDump(DatabaseTarget target, Path backupFile) {
        try {
            String backupFilePath = backupFile.toAbsolutePath().normalize().toString();
            ProcessBuilder processBuilder = new ProcessBuilder(
                mysqldumpPath,
                "--host=" + target.host(),
                "--port=" + target.port(),
                "--user=" + databaseUsername,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--result-file=" + backupFilePath,
                target.databaseName()
            );
            processBuilder.environment().put("MYSQL_PWD", databasePassword);
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

            logger.info("Creating database backup for database '{}' from {}:{} to file: {}",
                       target.databaseName(), target.host(), target.port(), backupFilePath);

            Process process = processBuilder.start();
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (finished && process.exitValue() == 0) {
                File backupOutput = new File(backupFilePath);
                if (backupOutput.exists() && backupOutput.length() > 0) {
                    logger.info("Database backup created successfully. Size: {} bytes", backupOutput.length());
                    return true;
                } else {
                    logger.error("Backup file is empty or doesn't exist");
                    return false;
                }
            } else {
                if (!finished) {
                    process.destroyForcibly();
                    process.waitFor(30, TimeUnit.SECONDS);
                }
                logger.error("mysqldump process failed with exit code: {}",
                           finished ? process.exitValue() : "timeout");
                return false;
            }

        } catch (Exception e) {
            logger.error("Error executing mysqldump: {}", e.getMessage(), e);
            return false;
        }
    }
    private void emailBackup(File backupFile, String timestamp, String databaseName) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(recipientEmail);
        helper.setSubject("ModulithForge Database Backup - " + timestamp);

        String emailBody = String.format(
            "<html><body>" +
            "<h2>ModulithForge Database Backup</h2>" +
            "<p>Please find attached the database backup for ModulithForge.</p>" +
            "<ul>" +
            "<li><strong>Backup Date:</strong> %s</li>" +
            "<li><strong>Database:</strong> %s</li>" +
            "<li><strong>File Size:</strong> %.2f MB</li>" +
            "</ul>" +
            "<p>This backup was automatically generated and the local copy has been deleted for security.</p>" +
            "<p><em>ModulithForge Automated Backup System</em></p>" +
            "</body></html>",
            timestamp,
            databaseName,
            backupFile.length() / (1024.0 * 1024.0)
        );

        helper.setText(emailBody, true);
        FileSystemResource fileResource = new FileSystemResource(backupFile);
        helper.addAttachment(backupFile.getName(), fileResource);

        mailSender.send(message);
        logger.info("Backup email sent successfully to: {}", recipientEmail);
    }
    private void deleteBackupFile(Path backupPath) {
        try {
            if (Files.exists(backupPath)) {
                Files.delete(backupPath);
                logger.info("Local backup file deleted: {}", backupPath);
            }
        } catch (IOException e) {
            logger.warn("Could not delete backup file: {}", e.getMessage());
        }
    }
    public String getBackupStatus() {
        if (!backupEnabled) {
            return "Database backup is disabled";
        }

        if (recipientEmail == null || recipientEmail.isEmpty()) {
            return "Database backup enabled but no recipient email configured";
        }

        return String.format("Database backup enabled. Daily backups sent to: %s", recipientEmail);
    }

    private record DatabaseTarget(String host, int port, String databaseName) {
    }
}
