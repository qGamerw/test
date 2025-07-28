package org.example;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import org.example.entity.Person;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Создаём фабрику EntityManager по имени persistence-unit
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("example-unit");
        EntityManager em = emf.createEntityManager();

        try {
            // Начинаем транзакцию (для RESOURCE_LOCAL обязателен)
            em.getTransaction().begin();

            // Пример: получить всех Person из БД
            TypedQuery<Person> query = em.createQuery("SELECT p FROM Person p", Person.class);
            List<Person> people = query.getResultList();

            // Выводим в консоль
            for (Person p : people) {
                System.out.println(p);
            }

            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            em.close();
            emf.close();
        }
    }
}
