package org.example;

import jakarta.persistence.EntityManager;

public class Main {
    public static void main(String[] args) {
        EntityManager em = HibernateUtil.getEntityManager();
        em.getTransaction().begin();

        User user = new User("Alice");
        em.persist(user);

        em.getTransaction().commit();
        em.close();

        HibernateUtil.close();
    }
}
