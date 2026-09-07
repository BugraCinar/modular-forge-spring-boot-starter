package dev.modularforge.shared.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionCoverageTest {

    @Test
    void everyDomainExceptionPreservesItsMessage() {
        assertThat(new BadRequestException("bad")).hasMessage("bad");
        assertThat(new ForbiddenException("forbidden")).hasMessage("forbidden");
        assertThat(new NotFoundException("missing")).hasMessage("missing");
        assertThat(new ResourceNotFoundException("resource missing")).hasMessage("resource missing");
        assertThat(new UnauthorizedException("unauthorized")).hasMessage("unauthorized");
    }

    @Test
    void everyDomainExceptionPreservesItsCause() {
        IllegalStateException cause = new IllegalStateException("cause");
        assertThat(new BadRequestException("bad", cause)).hasCause(cause);
        assertThat(new ForbiddenException("forbidden", cause)).hasCause(cause);
        assertThat(new NotFoundException("missing", cause)).hasCause(cause);
        assertThat(new ResourceNotFoundException("resource missing", cause)).hasCause(cause);
        assertThat(new UnauthorizedException("unauthorized", cause)).hasCause(cause);
    }
}
