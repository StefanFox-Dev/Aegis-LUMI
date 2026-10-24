package aegis.lumi.config;

import cn.nukkit.utils.Config;

public final class AegisConfig {

    private String host = "127.0.0.1";
    private int port = 19140;
    private String secret = "CHANGE_ME_IN_PRODUCTION";
    private long reconnectDelayMs = 2000L;
    private int timeoutMs = 5000;

    private int sampleIntervalTicks = 5;
    private int maxSamplesPerTick = 8;
    private long inputBudgetNanos = 1_500_000L;
    private long sampleBudgetNanos = 1_500_000L;

    private boolean punishmentsEnabled = true;
    private long banDurationSeconds = 0L;
    private boolean logAlerts = true;
    private long alertCooldownMs = 1000L;
    private boolean alertsEnabledByDefault = true;

    public void load(Config config) {
        if (config == null) {
            return;
        }

        this.host = config.getString("connection.host", config.getString("host", this.host));
        this.port = config.getInt("connection.port", config.getInt("port", this.port));
        this.secret = config.getString("connection.secret", config.getString("secret", this.secret));
        this.reconnectDelayMs = config.getLong("connection.reconnect-delay-ms", config.getLong("reconnect-delay-ms", this.reconnectDelayMs));
        this.timeoutMs = config.getInt("connection.timeout-ms", config.getInt("timeout-ms", this.timeoutMs));

        this.sampleIntervalTicks = Math.max(1, config.getInt("telemetry.sample-interval-ticks", config.getInt("sample-interval-ticks", this.sampleIntervalTicks)));
        this.maxSamplesPerTick = Math.max(1, config.getInt("telemetry.max-samples-per-tick", config.getInt("max-samples-per-tick", this.maxSamplesPerTick)));
        this.inputBudgetNanos = config.getLong("telemetry.input-budget-nanos", config.getLong("input-budget-nanos", this.inputBudgetNanos));
        this.sampleBudgetNanos = config.getLong("telemetry.sample-budget-nanos", config.getLong("sample-budget-nanos", this.sampleBudgetNanos));

        this.punishmentsEnabled = config.getBoolean("punishments.enabled", config.getBoolean("punishments-enabled", this.punishmentsEnabled));
        this.banDurationSeconds = config.getLong("punishments.ban-duration-seconds", config.getLong("ban-duration-seconds", this.banDurationSeconds));
        this.logAlerts = config.getBoolean("alerts.log", config.getBoolean("log-alerts", this.logAlerts));
        this.alertCooldownMs = config.getLong("alerts.cooldown-milliseconds", config.getLong("alert-cooldown-ms", this.alertCooldownMs));
        this.alertsEnabledByDefault = config.getBoolean("alerts.enabled-by-default", config.getBoolean("alerts-enabled-by-default", this.alertsEnabledByDefault));
    }

    public long getRetryIntervalTicks() {
        return Math.max(20L, reconnectDelayMs / 50L);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getReconnectDelayMs() {
        return reconnectDelayMs;
    }

    public void setReconnectDelayMs(long reconnectDelayMs) {
        this.reconnectDelayMs = reconnectDelayMs;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getSampleIntervalTicks() {
        return sampleIntervalTicks;
    }

    public void setSampleIntervalTicks(int sampleIntervalTicks) {
        this.sampleIntervalTicks = sampleIntervalTicks;
    }

    public int getMaxSamplesPerTick() {
        return maxSamplesPerTick;
    }

    public void setMaxSamplesPerTick(int maxSamplesPerTick) {
        this.maxSamplesPerTick = maxSamplesPerTick;
    }

    public long getInputBudgetNanos() {
        return inputBudgetNanos;
    }

    public void setInputBudgetNanos(long inputBudgetNanos) {
        this.inputBudgetNanos = inputBudgetNanos;
    }

    public long getSampleBudgetNanos() {
        return sampleBudgetNanos;
    }

    public void setSampleBudgetNanos(long sampleBudgetNanos) {
        this.sampleBudgetNanos = sampleBudgetNanos;
    }

    public boolean isPunishmentsEnabled() {
        return punishmentsEnabled;
    }

    public void setPunishmentsEnabled(boolean punishmentsEnabled) {
        this.punishmentsEnabled = punishmentsEnabled;
    }

    public long getBanDurationSeconds() {
        return banDurationSeconds;
    }

    public void setBanDurationSeconds(long banDurationSeconds) {
        this.banDurationSeconds = banDurationSeconds;
    }

    public boolean isLogAlerts() {
        return logAlerts;
    }

    public void setLogAlerts(boolean logAlerts) {
        this.logAlerts = logAlerts;
    }

    public long getAlertCooldownMs() {
        return alertCooldownMs;
    }

    public void setAlertCooldownMs(long alertCooldownMs) {
        this.alertCooldownMs = alertCooldownMs;
    }

    public boolean isAlertsEnabledByDefault() {
        return alertsEnabledByDefault;
    }

    public void setAlertsEnabledByDefault(boolean alertsEnabledByDefault) {
        this.alertsEnabledByDefault = alertsEnabledByDefault;
    }
}
