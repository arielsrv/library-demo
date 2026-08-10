package app.support;

import org.flywaydb.core.Flyway;
import org.testcontainers.mysql.MySQLContainer;

/**
 * A single MySQL container shared by every integration test in the build.
 * <p>
 * It uses the same image and credentials as {@code docker-compose.yml}, so the tests run against the
 * database the application really targets (MySQL {@code ENUM}, {@code LONGTEXT}, {@code AUTO_INCREMENT}
 * and the Flyway migrations all behave as in production).
 * </p>
 */
public final class TestDatabase {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8")
            .withDatabaseName("library")
            .withUsername("library_admin")
            .withPassword("secure_library_pass");

    private static boolean migrated;

    private TestDatabase() {
    }

    /**
     * Boots the container once per JVM. Testcontainers stops it when the JVM exits.
     */
    public static synchronized MySQLContainer start() {
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        return MYSQL;
    }

    /**
     * Boots the container and applies the project migrations, including the sample data.
     * Use this when the test drives the repository directly, without the application booting Flyway.
     */
    public static synchronized MySQLContainer startMigrated() {
        start();
        if (!migrated) {
            Flyway.configure()
                    .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            migrated = true;
        }
        return MYSQL;
    }

    /**
     * Points the application's {@code db.*} configuration at the container.
     * System properties win over {@code application.conf}, so the app connects to the container
     * instead of a local MySQL on port 3306.
     */
    public static synchronized void exportAsSystemProperties() {
        start();
        System.setProperty("db.url", MYSQL.getJdbcUrl());
        System.setProperty("db.user", MYSQL.getUsername());
        System.setProperty("db.password", MYSQL.getPassword());
    }
}