package com.pulse.tracker.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pulse.tracker.data.local.entity.TrackingStateEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Testable session state machine detecting continuous movement per Section 10.2:
 * <ul>
 *   <li>First positive step delta starts a candidate movement session.</li>
 *   <li>Confirm the session after movement continues for at least 10 seconds. Candidate start time is included in duration.</li>
 *   <li>When no new steps arrive for 30 seconds, end the session and include a maximum 30-second tail after the last movement event.</li>
 *   <li>Uses monotonic time (SystemClock.elapsedRealtime()) so manual clock/time zone changes do not corrupt session duration.</li>
 *   <li>Restores persisted state on service restarts and caps downtime gaps (never counts hours of inactivity as walking).</li>
 *   <li>Splits active session durations that cross local midnight into the correct daily rows.</li>
 * </ul>
 */
public class ActivitySessionCalculator {

    /**
     * Threshold in milliseconds required to confirm candidate movement as an active session (10s).
     */
    public static final long CONFIRMATION_THRESHOLD_MS = 10_000L;

    /**
     * Threshold in milliseconds of inactivity before a session ends (30s).
     */
    public static final long INACTIVITY_TIMEOUT_MS = 30_000L;

    /**
     * Maximum tail duration in milliseconds added after the last movement event (30s).
     */
    public static final long MAX_TAIL_DURATION_MS = 30_000L;

    private boolean sessionActive = false;
    private boolean candidateActive = false;
    private long candidateStartMonotonicMs = 0L;
    private long lastMovementMonotonicMs = 0L;
    private long lastCreditedMonotonicMs = 0L;
    private long accumulatedSessionSeconds = 0L;

    /**
     * Immutable result of a session state machine transition.
     */
    public static class SessionUpdate {
        private final long activeSecondsDelta;
        @NonNull
        private final Map<String, Long> dateSplits;
        private final boolean sessionActive;
        private final boolean candidateActive;
        private final long candidateStartMonotonicMs;
        private final long lastMovementMonotonicMs;
        private final boolean sessionJustConfirmed;
        private final boolean sessionJustEnded;

        public SessionUpdate(long activeSecondsDelta,
                             @NonNull Map<String, Long> dateSplits,
                             boolean sessionActive,
                             boolean candidateActive,
                             long candidateStartMonotonicMs,
                             long lastMovementMonotonicMs,
                             boolean sessionJustConfirmed,
                             boolean sessionJustEnded) {
            this.activeSecondsDelta = Math.max(0, activeSecondsDelta);
            this.dateSplits = Collections.unmodifiableMap(Objects.requireNonNull(dateSplits));
            this.sessionActive = sessionActive;
            this.candidateActive = candidateActive;
            this.candidateStartMonotonicMs = candidateStartMonotonicMs;
            this.lastMovementMonotonicMs = lastMovementMonotonicMs;
            this.sessionJustConfirmed = sessionJustConfirmed;
            this.sessionJustEnded = sessionJustEnded;
        }

        public static SessionUpdate empty(boolean sessionActive, boolean candidateActive,
                                          long candidateStartMs, long lastMovementMs) {
            return new SessionUpdate(0L, Collections.emptyMap(), sessionActive, candidateActive,
                    candidateStartMs, lastMovementMs, false, false);
        }

        public long getActiveSecondsDelta() {
            return activeSecondsDelta;
        }

        @NonNull
        public Map<String, Long> getDateSplits() {
            return dateSplits;
        }

        public boolean isSessionActive() {
            return sessionActive;
        }

        public boolean isCandidateActive() {
            return candidateActive;
        }

        public long getCandidateStartMonotonicMs() {
            return candidateStartMonotonicMs;
        }

        public long getLastMovementMonotonicMs() {
            return lastMovementMonotonicMs;
        }

        public boolean isSessionJustConfirmed() {
            return sessionJustConfirmed;
        }

        public boolean isSessionJustEnded() {
            return sessionJustEnded;
        }

        @NonNull
        @Override
        public String toString() {
            return "SessionUpdate{" +
                    "activeSecondsDelta=" + activeSecondsDelta +
                    ", dateSplits=" + dateSplits +
                    ", sessionActive=" + sessionActive +
                    ", candidateActive=" + candidateActive +
                    ", candidateStartMonotonicMs=" + candidateStartMonotonicMs +
                    ", lastMovementMonotonicMs=" + lastMovementMonotonicMs +
                    ", sessionJustConfirmed=" + sessionJustConfirmed +
                    ", sessionJustEnded=" + sessionJustEnded +
                    '}';
        }
    }

    public ActivitySessionCalculator() {
    }

    public synchronized boolean isSessionActive() {
        return sessionActive;
    }

    public synchronized boolean isCandidateActive() {
        return candidateActive;
    }

    public synchronized long getCandidateStartMonotonicMs() {
        return candidateStartMonotonicMs;
    }

    public synchronized long getLastMovementMonotonicMs() {
        return lastMovementMonotonicMs;
    }

    public synchronized long getAccumulatedSessionSeconds() {
        return accumulatedSessionSeconds;
    }

    /**
     * Resets the in-memory session tracking state.
     */
    public synchronized void resetSession() {
        sessionActive = false;
        candidateActive = false;
        candidateStartMonotonicMs = 0L;
        lastMovementMonotonicMs = 0L;
        lastCreditedMonotonicMs = 0L;
        accumulatedSessionSeconds = 0L;
    }

    /**
     * Processes an incoming positive movement event.
     *
     * @param eventMonotonicMs Monotonic timestamp of the movement event (milliseconds).
     * @param eventWallTimeMs  Wall-clock timestamp corresponding to the movement event (milliseconds).
     * @param zoneId           Time zone for date attribution.
     * @return SessionUpdate containing active seconds delta, date attribution, and session status flags.
     */
    @NonNull
    public synchronized SessionUpdate onMovement(long eventMonotonicMs, long eventWallTimeMs, @Nullable ZoneId zoneId) {
        if (eventMonotonicMs <= 0) {
            return SessionUpdate.empty(sessionActive, candidateActive, candidateStartMonotonicMs, lastMovementMonotonicMs);
        }
        ZoneId effectiveZoneId = (zoneId != null) ? zoneId : ZoneId.systemDefault();

        // 1. Check if an existing candidate or session timed out prior to this event
        if ((candidateActive || sessionActive) && lastMovementMonotonicMs > 0) {
            long idleMs = eventMonotonicMs - lastMovementMonotonicMs;
            if (idleMs >= INACTIVITY_TIMEOUT_MS) {
                // Finalize previous timed-out session or discard unconfirmed candidate
                SessionUpdate timeoutUpdate = handleTimeoutInternal(eventMonotonicMs, eventWallTimeMs, effectiveZoneId);

                // Start fresh candidate at this event
                startCandidate(eventMonotonicMs);

                // Return timeout delta (e.g. tail seconds) combined with new candidate state
                return new SessionUpdate(
                        timeoutUpdate.getActiveSecondsDelta(),
                        timeoutUpdate.getDateSplits(),
                        false,
                        true,
                        candidateStartMonotonicMs,
                        lastMovementMonotonicMs,
                        false,
                        timeoutUpdate.isSessionJustEnded()
                );
            }
        }

        // 2. Process movement
        if (!candidateActive && !sessionActive) {
            // First step starts candidate session
            startCandidate(eventMonotonicMs);
            return SessionUpdate.empty(false, true, candidateStartMonotonicMs, lastMovementMonotonicMs);
        }

        if (candidateActive && !sessionActive) {
            // Update movement monotonic timestamp
            lastMovementMonotonicMs = eventMonotonicMs;
            long durationFromStart = eventMonotonicMs - candidateStartMonotonicMs;

            if (durationFromStart >= CONFIRMATION_THRESHOLD_MS) {
                // Movement continued for >= 10s: CONFIRM SESSION!
                // Include candidate start time in session duration
                sessionActive = true;
                candidateActive = false;

                long uncreditedMs = eventMonotonicMs - lastCreditedMonotonicMs;
                long deltaSeconds = uncreditedMs / 1000L;
                long creditedMs = deltaSeconds * 1000L;

                long startMono = lastCreditedMonotonicMs;
                long endMono = lastCreditedMonotonicMs + creditedMs;
                lastCreditedMonotonicMs = endMono;
                accumulatedSessionSeconds += deltaSeconds;

                long startWall = eventWallTimeMs - (eventMonotonicMs - startMono);
                long endWall = eventWallTimeMs - (eventMonotonicMs - endMono);
                Map<String, Long> splits = splitDurationByDate(startWall, endWall, effectiveZoneId);

                return new SessionUpdate(
                        deltaSeconds,
                        splits,
                        true,
                        false,
                        candidateStartMonotonicMs,
                        lastMovementMonotonicMs,
                        true,
                        false
                );
            } else {
                // Still candidate (< 10s)
                return SessionUpdate.empty(false, true, candidateStartMonotonicMs, lastMovementMonotonicMs);
            }
        }

        // Session is already active and confirmed
        lastMovementMonotonicMs = eventMonotonicMs;
        long uncreditedMs = eventMonotonicMs - lastCreditedMonotonicMs;

        if (uncreditedMs >= 1000L) {
            long deltaSeconds = uncreditedMs / 1000L;
            long creditedMs = deltaSeconds * 1000L;

            long startMono = lastCreditedMonotonicMs;
            long endMono = lastCreditedMonotonicMs + creditedMs;
            lastCreditedMonotonicMs = endMono;
            accumulatedSessionSeconds += deltaSeconds;

            long startWall = eventWallTimeMs - (eventMonotonicMs - startMono);
            long endWall = eventWallTimeMs - (eventMonotonicMs - endMono);
            Map<String, Long> splits = splitDurationByDate(startWall, endWall, effectiveZoneId);

            return new SessionUpdate(
                    deltaSeconds,
                    splits,
                    true,
                    false,
                    candidateStartMonotonicMs,
                    lastMovementMonotonicMs,
                    false,
                    false
            );
        } else {
            return SessionUpdate.empty(true, false, candidateStartMonotonicMs, lastMovementMonotonicMs);
        }
    }

    /**
     * Checks if the 30-second inactivity timeout has elapsed.
     * When movement pauses for at least 30s, ends the session and credits a maximum 30s tail.
     *
     * @param currentMonotonicMs Current monotonic timestamp (SystemClock.elapsedRealtime()).
     * @param currentWallTimeMs  Current wall-clock timestamp (System.currentTimeMillis()).
     * @param zoneId             Time zone for date attribution.
     * @return SessionUpdate containing finalized tail seconds, or empty if timeout has not elapsed.
     */
    @NonNull
    public synchronized SessionUpdate checkInactivityTimeout(long currentMonotonicMs, long currentWallTimeMs, @Nullable ZoneId zoneId) {
        if (!candidateActive && !sessionActive) {
            return SessionUpdate.empty(false, false, 0L, 0L);
        }

        long idleMs = currentMonotonicMs - lastMovementMonotonicMs;
        if (idleMs < INACTIVITY_TIMEOUT_MS) {
            // Not timed out yet (e.g. at 29s)
            return SessionUpdate.empty(sessionActive, candidateActive, candidateStartMonotonicMs, lastMovementMonotonicMs);
        }

        // Timed out (>= 30s)
        ZoneId effectiveZoneId = (zoneId != null) ? zoneId : ZoneId.systemDefault();
        return handleTimeoutInternal(currentMonotonicMs, currentWallTimeMs, effectiveZoneId);
    }

    /**
     * Restores session state from persisted TrackingStateEntity on service restart.
     * Safely caps downtime gaps so long service downtime is never counted as walking.
     *
     * @param state              Persisted tracking state checkpoint.
     * @param currentMonotonicMs Current monotonic timestamp.
     * @param currentWallTimeMs  Current wall-clock timestamp.
     * @param zoneId             Time zone for date attribution.
     * @return SessionUpdate with any capped tail seconds finalized during downtime.
     */
    @NonNull
    public synchronized SessionUpdate restoreState(@Nullable TrackingStateEntity state,
                                                   long currentMonotonicMs,
                                                   long currentWallTimeMs,
                                                   @Nullable ZoneId zoneId) {
        if (state == null) {
            resetSession();
            return SessionUpdate.empty(false, false, 0L, 0L);
        }

        long savedLastMovement = state.getLastMovementMonotonicMs();
        if (savedLastMovement <= 0 || currentMonotonicMs < savedLastMovement) {
            // Device rebooted or invalid monotonic timestamp
            resetSession();
            return SessionUpdate.empty(false, false, 0L, 0L);
        }

        long idleMs = currentMonotonicMs - savedLastMovement;
        ZoneId effectiveZoneId = (zoneId != null) ? zoneId : ZoneId.systemDefault();

        if (idleMs >= INACTIVITY_TIMEOUT_MS) {
            // Service was down or inactive for >= 30s. Cap gaps at 30s maximum tail.
            if (state.isSessionActive()) {
                long tailDurationMs = MAX_TAIL_DURATION_MS;
                long deltaSeconds = tailDurationMs / 1000L;

                long tailStartWall = state.getUpdatedAtMillis() > 0
                        ? state.getUpdatedAtMillis()
                        : currentWallTimeMs - idleMs;
                long tailEndWall = tailStartWall + tailDurationMs;

                Map<String, Long> splits = splitDurationByDate(tailStartWall, tailEndWall, effectiveZoneId);
                accumulatedSessionSeconds = state.getAccumulatedSessionSeconds() + deltaSeconds;
                sessionActive = false;
                candidateActive = false;
                candidateStartMonotonicMs = 0L;
                lastMovementMonotonicMs = 0L;
                lastCreditedMonotonicMs = 0L;

                return new SessionUpdate(
                        deltaSeconds,
                        splits,
                        false,
                        false,
                        state.getSessionStartMonotonicMs(),
                        state.getLastMovementMonotonicMs(),
                        false,
                        true
                );
            } else {
                // Candidate never confirmed before service restart; discard
                resetSession();
                return SessionUpdate.empty(false, false, 0L, 0L);
            }
        } else {
            // Service restarted quickly (< 30s since last movement)
            sessionActive = state.isSessionActive();
            candidateActive = !state.isSessionActive() && (state.getSessionStartMonotonicMs() > 0);
            candidateStartMonotonicMs = state.getSessionStartMonotonicMs();
            lastMovementMonotonicMs = state.getLastMovementMonotonicMs();
            lastCreditedMonotonicMs = state.getLastMovementMonotonicMs();
            accumulatedSessionSeconds = state.getAccumulatedSessionSeconds();

            return SessionUpdate.empty(sessionActive, candidateActive, candidateStartMonotonicMs, lastMovementMonotonicMs);
        }
    }

    /**
     * Forcibly finalizes any ongoing session (e.g. when tracking is explicitly paused or stopped).
     */
    @NonNull
    public synchronized SessionUpdate forceEndSession(long currentMonotonicMs, long currentWallTimeMs, @Nullable ZoneId zoneId) {
        if (!candidateActive && !sessionActive) {
            return SessionUpdate.empty(false, false, 0L, 0L);
        }
        ZoneId effectiveZoneId = (zoneId != null) ? zoneId : ZoneId.systemDefault();
        return handleTimeoutInternal(currentMonotonicMs, currentWallTimeMs, effectiveZoneId);
    }

    /**
     * Synchronizes current session state into a TrackingStateEntity builder.
     */
    @NonNull
    public synchronized TrackingStateEntity syncToTrackingState(@NonNull TrackingStateEntity entity, long currentWallTimeMs) {
        return entity.toBuilder()
                .setSessionActive(this.sessionActive)
                .setSessionStartMonotonicMs(this.candidateStartMonotonicMs)
                .setLastMovementMonotonicMs(this.lastMovementMonotonicMs)
                .setAccumulatedSessionSeconds(this.accumulatedSessionSeconds)
                .setUpdatedAtMillis(currentWallTimeMs)
                .build();
    }

    /**
     * Splits an active time duration (milliseconds) across midnight boundaries into local calendar dates.
     *
     * @param startWallMs Epoch millisecond when interval started.
     * @param endWallMs   Epoch millisecond when interval ended.
     * @param zoneId      Local time zone.
     * @return Map of local date ('yyyy-MM-dd') to active seconds for that date.
     */
    @NonNull
    public static Map<String, Long> splitDurationByDate(long startWallMs, long endWallMs, @NonNull ZoneId zoneId) {
        if (endWallMs <= startWallMs) {
            return Collections.emptyMap();
        }

        LocalDate startDate = Instant.ofEpochMilli(startWallMs).atZone(zoneId).toLocalDate();
        LocalDate endDate = Instant.ofEpochMilli(endWallMs).atZone(zoneId).toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        if (startDate.equals(endDate)) {
            long seconds = (endWallMs - startWallMs) / 1000L;
            if (seconds == 0) {
                seconds = Math.round((endWallMs - startWallMs) / 1000.0);
            }
            if (seconds <= 0) {
                return Collections.emptyMap();
            }
            return Collections.singletonMap(startDate.format(formatter), seconds);
        }

        Map<String, Long> result = new LinkedHashMap<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            long dayStartMs = current.atStartOfDay(zoneId).toInstant().toEpochMilli();
            long dayEndMs = current.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();

            long portionStart = Math.max(startWallMs, dayStartMs);
            long portionEnd = Math.min(endWallMs, dayEndMs);

            if (portionEnd > portionStart) {
                long durationMs = portionEnd - portionStart;
                long seconds = durationMs / 1000L;
                if (seconds == 0) {
                    seconds = Math.round(durationMs / 1000.0);
                }
                if (seconds > 0) {
                    result.put(current.format(formatter), seconds);
                }
            }
            current = current.plusDays(1);
        }

        return result;
    }

    private void startCandidate(long eventMonotonicMs) {
        candidateActive = true;
        sessionActive = false;
        candidateStartMonotonicMs = eventMonotonicMs;
        lastMovementMonotonicMs = eventMonotonicMs;
        lastCreditedMonotonicMs = eventMonotonicMs;
        accumulatedSessionSeconds = 0L;
    }

    private SessionUpdate handleTimeoutInternal(long currentMonotonicMs, long currentWallTimeMs, ZoneId effectiveZoneId) {
        if (sessionActive) {
            long tailEndMonotonic = lastMovementMonotonicMs + MAX_TAIL_DURATION_MS;
            if (tailEndMonotonic > currentMonotonicMs) {
                tailEndMonotonic = currentMonotonicMs;
            }

            long uncreditedTailMs = tailEndMonotonic - lastCreditedMonotonicMs;
            long deltaSeconds = 0;
            Map<String, Long> splits = Collections.emptyMap();

            if (uncreditedTailMs > 0) {
                deltaSeconds = uncreditedTailMs / 1000L;
                if (deltaSeconds == 0) {
                    deltaSeconds = Math.round(uncreditedTailMs / 1000.0);
                }
                if (deltaSeconds > 0) {
                    accumulatedSessionSeconds += deltaSeconds;
                    long startMono = lastCreditedMonotonicMs;
                    long endMono = tailEndMonotonic;
                    long startWall = currentWallTimeMs - (currentMonotonicMs - startMono);
                    long endWall = currentWallTimeMs - (currentMonotonicMs - endMono);
                    splits = splitDurationByDate(startWall, endWall, effectiveZoneId);
                }
            }

            long oldStart = candidateStartMonotonicMs;
            long oldLast = lastMovementMonotonicMs;

            sessionActive = false;
            candidateActive = false;
            candidateStartMonotonicMs = 0L;
            lastMovementMonotonicMs = 0L;
            lastCreditedMonotonicMs = 0L;

            return new SessionUpdate(
                    deltaSeconds,
                    splits,
                    false,
                    false,
                    oldStart,
                    oldLast,
                    false,
                    true
            );
        } else if (candidateActive) {
            // Unconfirmed candidate (< 10s of movement); no tail credited
            long oldStart = candidateStartMonotonicMs;
            long oldLast = lastMovementMonotonicMs;
            resetSession();

            return new SessionUpdate(
                    0L,
                    Collections.emptyMap(),
                    false,
                    false,
                    oldStart,
                    oldLast,
                    false,
                    false
            );
        } else {
            return SessionUpdate.empty(false, false, 0L, 0L);
        }
    }
}
