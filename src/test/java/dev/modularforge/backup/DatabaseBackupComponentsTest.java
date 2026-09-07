package dev.modularforge.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupComponentsTest {

    @Mock DatabaseBackupService backupService;
    @Mock DatabaseBackupJobService backupJobService;

    private DatabaseBackupController controller;

    @BeforeEach
    void setUp() {
        controller = new DatabaseBackupController();
        ReflectionTestUtils.setField(controller, "databaseBackupService", backupService);
        ReflectionTestUtils.setField(controller, "databaseBackupJobService", backupJobService);
    }

    @Test
    void controllerQueuesReportsConflictsAndHandlesFailures() {
        when(backupJobService.requestBackup()).thenReturn(true, false).thenThrow(new IllegalStateException("executor down"));

        assertThat(controller.createBackup().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.createBackup().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(controller.createBackup().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void controllerReportsCurrentStatus() {
        when(backupService.getBackupStatus()).thenReturn("enabled");
        when(backupJobService.isBackupRunning()).thenReturn(true);

        var response = controller.getBackupStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "enabled").containsEntry("running", true);
    }

    @Test
    void jobRejectsConcurrentRequestsAndResetsAfterExecution() {
        List<Runnable> tasks = new ArrayList<>();
        Executor capturingExecutor = tasks::add;
        DatabaseBackupJobService job = new DatabaseBackupJobService(backupService, capturingExecutor);

        assertThat(job.requestBackup()).isTrue();
        assertThat(job.isBackupRunning()).isTrue();
        assertThat(job.requestBackup()).isFalse();

        tasks.getFirst().run();

        verify(backupService).createAndEmailBackup();
        assertThat(job.isBackupRunning()).isFalse();
    }

    @Test
    void jobAlwaysResetsWhenBackupOrExecutorFails() {
        doThrow(new IllegalStateException("backup failed")).when(backupService).createAndEmailBackup();
        DatabaseBackupJobService directJob = new DatabaseBackupJobService(backupService, Runnable::run);
        assertThatThrownBy(directJob::requestBackup).isInstanceOf(IllegalStateException.class);
        assertThat(directJob.isBackupRunning()).isFalse();

        DatabaseBackupJobService rejectedJob = new DatabaseBackupJobService(backupService,
                task -> { throw new IllegalStateException("rejected"); });
        assertThatThrownBy(rejectedJob::requestBackup).isInstanceOf(IllegalStateException.class);
        assertThat(rejectedJob.isBackupRunning()).isFalse();
    }

    @Test
    void scheduledServiceRequestsBackupWhetherAcceptedOrBusy() {
        DatabaseBackupScheduledService scheduled = new DatabaseBackupScheduledService();
        ReflectionTestUtils.setField(scheduled, "databaseBackupJobService", backupJobService);
        when(backupJobService.requestBackup()).thenReturn(true, false);

        scheduled.createDatabaseBackup();
        scheduled.createDatabaseBackup();

        verify(backupJobService, org.mockito.Mockito.times(2)).requestBackup();
    }
}
