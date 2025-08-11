package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Утилитный класс для инициализации Hibernate + HikariCP без использования persistence.xml.
 * Настройки подключения и Hibernate указываются полностью программно.
 */
public class HibernateUtil {

    // Глобальный фабричный объект JPA для создания EntityManager
    private static final EntityManagerFactory ENTITY_MANAGER_FACTORY;

    static {
        try {
            // 1. Конфигурация пула соединений HikariCP
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres"); // URL подключения к БД
            hikariConfig.setUsername("postgres"); // Логин
            hikariConfig.setPassword("postgres"); // Пароль
            hikariConfig.setDriverClassName("org.postgresql.Driver"); // JDBC-драйвер

            // Настройки пула
            hikariConfig.setMaximumPoolSize(10);     // макс. количество соединений
            hikariConfig.setMinimumIdle(2);          // мин. количество простаивающих соединений
            hikariConfig.setIdleTimeout(300000);     // время до закрытия простаивающего соединения (мс)
            hikariConfig.setMaxLifetime(1800000);    // макс. время жизни соединения в пуле (мс)
            hikariConfig.setPoolName("MyHikariCP");  // имя пула (для логов)

            // Создаём DataSource из настроек HikariCP
            DataSource dataSource = new HikariDataSource(hikariConfig);

            // 2. Настройки Hibernate (вместо persistence.xml)
            Properties hibernateProps = new Properties();
            hibernateProps.put(AvailableSettings.DATASOURCE, dataSource); // Источник соединений — наш пул
            hibernateProps.put(AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQLDialect"); // SQL-диалект
            hibernateProps.put(AvailableSettings.HBM2DDL_AUTO, "validate"); // проверка схемы
            hibernateProps.put(AvailableSettings.SHOW_SQL, "true");       // показывать SQL в логах
            hibernateProps.put(AvailableSettings.FORMAT_SQL, "true");     // форматировать SQL в логах

            // 3. Создаём SessionFactory и регистрируем Entity вручную
            Configuration configuration = new Configuration()
                    .addProperties(hibernateProps)
                    .addAnnotatedClass(User.class); // Добавляем каждую сущность вручную

            // Создаём Hibernate SessionFactory
            SessionFactory sessionFactory = configuration.buildSessionFactory();

            // Преобразуем в JPA EntityManagerFactory
            ENTITY_MANAGER_FACTORY = sessionFactory.unwrap(EntityManagerFactory.class);

        } catch (Exception e) {
            // Если на этапе инициализации что-то пошло не так — падаем сразу
            throw new RuntimeException("Ошибка инициализации Hibernate", e);
        }
    }

    /**
     * Получить новый EntityManager.
     * Каждый вызов возвращает независимый менеджер (нужно закрывать вручную).
     */
    public static EntityManager getEntityManager() {
        return ENTITY_MANAGER_FACTORY.createEntityManager();
    }

    /**
     * Закрыть фабрику EntityManager (например, при завершении приложения).
     */
    public static void close() {
        ENTITY_MANAGER_FACTORY.close();
    }
}
