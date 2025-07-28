package org.example;

import jakarta.persistence.EntityManager;
import org.example.config.JPAUtil;
import jakarta.persistence.TypedQuery;
import org.example.entity.Person;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Получаем EntityManager из утилиты, использующей пул соединений
        EntityManager em = JPAUtil.getEntityManager();

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
            JPAUtil.close();
        }
    }
}
