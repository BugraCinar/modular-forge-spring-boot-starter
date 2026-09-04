package dev.modularforge.auth;

import java.util.Optional;
public interface SecondFactorGateway {

    Optional<Challenge> beginChallenge(Long accountId);

    record Challenge(String token, String message) {
    }
}
