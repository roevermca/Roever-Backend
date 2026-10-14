package com.ams.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url:${MYSQL_URL:${DATABASE_URL:}}}")
    private String rawUrl;

    @Value("${spring.datasource.username:${MYSQL_USER:root}}")
    private String username;

    @Value("${spring.datasource.password:${MYSQL_PASSWORD:root}}")
    private String password;

    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:25}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minIdle;

    private boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 600);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testConnection(String url, String user, String pass) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            DriverManager.setLoginTimeout(2);
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                return conn != null && !conn.isClosed();
            }
        } catch (Exception e) {
            log.warn("MySQL direct probe failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sanitizes and tunes the MySQL JDBC URL with ultra-high-speed optimizations:
     * - rewriteBatchedStatements=true: Rewrites individual inserts into multi-row packets (up to 10x faster)
     * - cachePrepStmts=true: Caches prepared statements in MySQL driver
     * - prepStmtCacheSize=250 & prepStmtCacheSqlLimit=2048: statement caching limits
     * - useServerPrepStmts=true: uses MySQL binary protocol
     * - useSSL=false & allowPublicKeyRetrieval=true: avoids SSL handshake latencies on local/private nets
     */
    public static String buildOptimizedJdbcUrl(String url, String host, String port, String database) {
        String base;
        if (url != null && !url.isBlank()) {
            String trimmed = url.trim();
            if (trimmed.startsWith("mysql://")) {
                try {
                    URI uri = URI.create(trimmed);
                    String h = uri.getHost();
                    int p = uri.getPort() > 0 ? uri.getPort() : 3306;
                    String d = (uri.getPath() != null && uri.getPath().length() > 1) ? uri.getPath().substring(1) : "ams_db";
                    base = "jdbc:mysql://" + h + ":" + p + "/" + d;
                } catch (Exception e) {
                    base = "jdbc:" + trimmed;
                }
            } else if (!trimmed.startsWith("jdbc:")) {
                base = "jdbc:mysql://" + trimmed;
            } else {
                base = trimmed;
            }
        } else {
            String h = (host != null && !host.isBlank()) ? host.trim() : "localhost";
            String p = (port != null && !port.isBlank()) ? port.trim() : "3306";
            String d = (database != null && !database.isBlank()) ? database.trim() : "ams_db";
            base = "jdbc:mysql://" + h + ":" + p + "/" + d;
        }

        StringBuilder sb = new StringBuilder(base);
        char separator = base.contains("?") ? '&' : '?';

        if (!base.contains("rewriteBatchedStatements")) {
            sb.append(separator).append("rewriteBatchedStatements=true");
            separator = '&';
        }
        if (!base.contains("cachePrepStmts")) {
            sb.append(separator).append("cachePrepStmts=true");
            separator = '&';
        }
        if (!base.contains("prepStmtCacheSize")) {
            sb.append(separator).append("prepStmtCacheSize=250");
            separator = '&';
        }
        if (!base.contains("prepStmtCacheSqlLimit")) {
            sb.append(separator).append("prepStmtCacheSqlLimit=2048");
            separator = '&';
        }
        if (!base.contains("useServerPrepStmts")) {
            sb.append(separator).append("useServerPrepStmts=true");
            separator = '&';
        }
        if (!base.contains("maintainTimeStats")) {
            sb.append(separator).append("maintainTimeStats=false");
            separator = '&';
        }
        if (!base.contains("createDatabaseIfNotExist")) {
            sb.append(separator).append("createDatabaseIfNotExist=true");
            separator = '&';
        }
        if (!base.contains("allowPublicKeyRetrieval")) {
            sb.append(separator).append("allowPublicKeyRetrieval=true");
            separator = '&';
        }
        if (!base.contains("useSSL")) {
            sb.append(separator).append("useSSL=false");
            separator = '&';
        }
        if (!base.contains("characterEncoding")) {
            sb.append(separator).append("characterEncoding=utf8");
            separator = '&';
        }

        return sb.toString();
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${MYSQL_HOST:localhost}") String host,
            @Value("${MYSQL_PORT:3306}") String port,
            @Value("${MYSQL_DATABASE:ams_db}") String database) {

        String targetDriver = driverClassName;
        String targetUser = username;
        String targetPass = password;
        String jdbcUrl;

        // If explicitly set to H2 in-memory test DB
        if (rawUrl != null && rawUrl.startsWith("jdbc:h2:")) {
            jdbcUrl = rawUrl;
            targetDriver = "org.h2.Driver";
            log.info("Using H2 test datasource: {}", jdbcUrl);
        } else {
            int portNum = 3306;
            try {
                portNum = Integer.parseInt(port.trim());
            } catch (Exception ignored) {
            }

            boolean isLocalTarget = "localhost".equalsIgnoreCase(host.trim()) || "127.0.0.1".equals(host.trim());
            String candidateMysqlUrl = buildOptimizedJdbcUrl(rawUrl, host, port, database);

            boolean isConnected = false;
            if (isPortListening(host.trim(), portNum)) {
                isConnected = testConnection(candidateMysqlUrl, targetUser, targetPass);
            }

            if (isConnected) {
                jdbcUrl = candidateMysqlUrl;
                log.info("✅ Connected to MySQL server ({}:{}) with high-speed HikariCP pool.", host, port);
            } else if (!isLocalTarget) {
                jdbcUrl = candidateMysqlUrl;
                log.info("Connecting to remote MySQL server ({}:{})...", host, port);
            } else {
                // Graceful local development fallback when MySQL authentication or service is pending on localhost
                jdbcUrl = "jdbc:h2:file:./data/ams_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";
                targetDriver = "org.h2.Driver";
                targetUser = "sa";
                targetPass = "";
                log.warn("================================================================================");
                log.warn("⚠️ MySQL is running but access for '{}:{}' requires authentication setup.", targetUser, host);
                log.warn("   Running with persistent local database: {}", jdbcUrl);
                log.warn("   To switch to MySQL, execute in your open MySQL prompt:");
                log.warn("   ALTER USER 'root'@'localhost' IDENTIFIED BY 'root'; FLUSH PRIVILEGES;");
                log.warn("================================================================================");
            }
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("AmsHikariPool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(targetUser);
        config.setPassword(targetPass);
        if (targetDriver != null && !targetDriver.isBlank()) {
            config.setDriverClassName(targetDriver);
        }

        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(300000); // 5 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setConnectionTimeout(20000); // 20 seconds
        config.setLeakDetectionThreshold(30000); // 30 seconds

        // MySQL JDBC Driver performance cache parameters
        if (!jdbcUrl.startsWith("jdbc:h2:")) {
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
        }

        return new HikariDataSource(config);
    }
}
