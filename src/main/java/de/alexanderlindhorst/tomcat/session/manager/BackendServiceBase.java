/*
 * This software is licensed under the GPL v2 (http://www.gnu.org/licenses/gpl-2.0.html).
 */
package de.alexanderlindhorst.tomcat.session.manager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static de.alexanderlindhorst.tomcat.session.manager.PersistableSessionUtils.deserializeSessionInto;
import static de.alexanderlindhorst.tomcat.session.manager.PersistableSessionUtils.serializeSession;
import static java.lang.String.join;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author alindhorst
 */
public abstract class BackendServiceBase implements BackendService {

    public static final long SESSIONS_NEVER_EXPIRE = -1;
    protected static final Logger LOGGER = LoggerFactory.getLogger(BackendServiceBase.class);
    private Logger sessionManagementLogger = LOGGER;
    private String backendAddress;
    private long sessionExpiryThreshold = SESSIONS_NEVER_EXPIRE;
    private long cleanUpRunIntervalSeconds = 10;
    private final ExecutorService cleanUpThreads = Executors.newSingleThreadExecutor();
    private boolean shuttingDown;

    @Override
    public void init() {
        Future<?> worker = cleanUpThreads.submit(new CleanUpWorker());
    }

    @Override
    public void shutdown() {
        shuttingDown = true;
        cleanUpThreads.shutdown();
    }

    @Override
    public final void persistSession(PersistableSession session) {
        persistSessionInternal(session.getPersistenceKey(), serializeSession(session));
        session.setDirty(false);
    }

    protected abstract void persistSessionInternal(String sessionId, byte[] bytes);

    @Override
    public final PersistableSession getSession(PersistableSession emptyShell, String id) {
        return deserializeSessionInto(emptyShell, getSessionInternal(id));
    }

    protected abstract byte[] getSessionInternal(String sessionId);

    @Override
    public final void deleteSession(PersistableSession session) {
        deleteSessionInternal(session.getPersistenceKey());
    }

    protected abstract void deleteSessionInternal(String sessionId);

    @Override
    public final void setBackendAddress(String backendAddress) {
        this.backendAddress = backendAddress;
    }

    public final String getBackendAddress() {
        return backendAddress;
    }

    @Override
    public final void setSessionExpiryThreshold(long sessionExpiryThresholdMilliSeconds) {
        this.sessionExpiryThreshold = sessionExpiryThresholdMilliSeconds;
    }

    public final long getSessionExpiryThreshold() {
        return sessionExpiryThreshold;
    }

    public final long getCleanUpRunIntervalSeconds() {
        return cleanUpRunIntervalSeconds;
    }

    @Override
    public final void setCleanUpRunIntervalSeconds(long cleanUpRunIntervalSeconds) {
        this.cleanUpRunIntervalSeconds = cleanUpRunIntervalSeconds;
    }

    public final Logger getSessionManagementLogger() {
        return sessionManagementLogger;
    }

    @Override
    public final void setSessionManagementLogger(Logger sessionManagementLogger) {
        this.sessionManagementLogger = sessionManagementLogger;
    }

    protected final boolean isShuttingDown() {
        return shuttingDown;
    }

    private class CleanUpWorker implements Runnable {

        @Override
        public void run() {
            LOGGER.debug("CleanUpWorker started");
            while (!shuttingDown) {
                LOGGER.debug("CleanUpWorker working");
                try {
                    List<String> removed = removeExpiredSessions();
                    logDeletedSessions(removed);
                    SECONDS.sleep(cleanUpRunIntervalSeconds);
                } catch (InterruptedException ex) {
                    LOGGER.warn("CleanUpWorker's sleep interrupted", ex);
                }
            }
        }

        private void logDeletedSessions(List<String> sessionIds) {
            sessionManagementLogger.info("Sessions deleted: {}", join(", ", sessionIds));
        }

    }
}
