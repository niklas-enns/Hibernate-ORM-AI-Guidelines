package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.Publisher;
import org.hibernate.SessionFactory;
import org.hibernate.Version;
import org.hibernate.cfg.QuerySettings;
import org.hibernate.cfg.StatisticsSettings;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.tool.schema.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import java.util.UUID;

/**
 * Boots a fresh H2 in-memory database per test and exposes a SQL statement counter.
 * Subclasses can change the managed entities or add settings.
 */
public abstract class HibernateTestBase {

    protected SessionFactory sessionFactory;

    protected Class<?>[] entities() {
        return new Class<?>[]{Author.class, Book.class, Publisher.class};
    }

    protected Map<String, Object> settings() {
        return Map.of();
    }

    @BeforeEach
    void createSessionFactory() {
        var configuration = new HibernatePersistenceConfiguration("test")
                .managedClasses(entities())
                .jdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .jdbcCredentials("sa", "")
                .schemaToolingAction(Action.CREATE_DROP)
                .property(StatisticsSettings.GENERATE_STATISTICS, true)
                .property(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true)
                .property("hibernate.show_sql", Boolean.getBoolean("showSql"));
        settings().forEach(configuration::property);
        sessionFactory = configuration.createEntityManagerFactory();
    }

    @AfterEach
    void closeSessionFactory() {
        sessionFactory.close();
    }

    /** True if the Hibernate version on the classpath is at least {@code major.minor}. */
    protected static boolean hibernateAtLeast(int major, int minor) {
        String[] parts = Version.getVersionString().split("\\.");
        int actualMajor = Integer.parseInt(parts[0]);
        int actualMinor = Integer.parseInt(parts[1]);
        return actualMajor > major || (actualMajor == major && actualMinor >= minor);
    }

    /** Number of JDBC statements prepared while running the action. */
    protected long statementsDuring(Runnable action) {
        var statistics = sessionFactory.getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }
}
