package shop;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.StatisticsSettings;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.tool.schema.Action;

import java.util.UUID;

/** Creates a SessionFactory on a fresh H2 in-memory database. Statistics are enabled. */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static SessionFactory create(Class<?>... entities) {
        return new HibernatePersistenceConfiguration("test")
                .managedClasses(entities)
                .jdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .jdbcCredentials("sa", "")
                .schemaToolingAction(Action.CREATE_DROP)
                .property(StatisticsSettings.GENERATE_STATISTICS, true)
                .createEntityManagerFactory();
    }
}
