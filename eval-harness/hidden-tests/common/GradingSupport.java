package shop.grading;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.QuerySettings;
import org.hibernate.cfg.StatisticsSettings;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.tool.schema.Action;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Hidden grading support: finds the run's entities and builds a SessionFactory without run-specific settings. */
final class GradingSupport {

    private GradingSupport() {
    }

    static List<Class<?>> entities() {
        Path root = Path.of("target", "classes");
        try (Stream<Path> files = Files.walk(root)) {
            List<Class<?>> result = new ArrayList<>();
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace('/', '.').replaceAll("\\.class$", "");
                Class<?> type = Class.forName(name);
                if (type.isAnnotationPresent(Entity.class)) {
                    result.add(type);
                }
            }
            return result;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    static SessionFactory sessionFactory() {
        return new HibernatePersistenceConfiguration("grading")
                .managedClasses(entities())
                .jdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .jdbcCredentials("sa", "")
                .schemaToolingAction(Action.CREATE_DROP)
                .property(StatisticsSettings.GENERATE_STATISTICS, true)
                .property(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true)
                .createEntityManagerFactory();
    }

    static long statements(SessionFactory sessionFactory, Runnable action) {
        sessionFactory.getStatistics().clear();
        action.run();
        return sessionFactory.getStatistics().getPrepareStatementCount();
    }

    static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            result.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return result;
    }

    static boolean hasCollectionField(Class<?> type) {
        return fields(type).stream().anyMatch(f ->
                Collection.class.isAssignableFrom(f.getType()) || Map.class.isAssignableFrom(f.getType()));
    }

    /** Problems: to-one not LAZY, any EAGER collection. Empty list means fine. */
    static List<String> eagerAssociations() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : entities()) {
            for (Field f : fields(entity)) {
                ManyToOne m2o = f.getAnnotation(ManyToOne.class);
                OneToOne o2o = f.getAnnotation(OneToOne.class);
                OneToMany o2m = f.getAnnotation(OneToMany.class);
                ManyToMany m2m = f.getAnnotation(ManyToMany.class);
                if (m2o != null && m2o.fetch() != FetchType.LAZY
                        || o2o != null && o2o.fetch() != FetchType.LAZY && o2o.mappedBy().isEmpty()
                        || o2m != null && o2m.fetch() == FetchType.EAGER
                        || m2m != null && m2m.fetch() == FetchType.EAGER) {
                    problems.add(entity.getSimpleName() + "." + f.getName());
                }
            }
        }
        return problems;
    }

    /** Problems: cascade ALL/REMOVE on a @ManyToOne. */
    static List<String> cascadingToOne() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : entities()) {
            for (Field f : fields(entity)) {
                ManyToOne m2o = f.getAnnotation(ManyToOne.class);
                if (m2o != null && Arrays.stream(m2o.cascade())
                        .anyMatch(c -> c == CascadeType.ALL || c == CascadeType.REMOVE)) {
                    problems.add(entity.getSimpleName() + "." + f.getName());
                }
            }
        }
        return problems;
    }

    static String mainSources() {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            StringBuilder all = new StringBuilder();
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                all.append(Files.readString(f)).append('\n');
            }
            return all.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
