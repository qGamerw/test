package org.example.dto;

import org.example.config.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;

@SuppressWarnings("unchecked")
public abstract class AbstractDAO<T, ID extends Serializable> {
    protected EntityManager em;
    private Class<T> entityClass;

    public AbstractDAO() {
        this.em = JPAUtil.getEntityManager();
        this.entityClass = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass())
                .getActualTypeArguments()[0];
    }

    public T findById(ID id) {
        return em.find(entityClass, id);
    }

    public List<T> findAll() {
        return em.createQuery("FROM " + entityClass.getSimpleName(), entityClass)
                .getResultList();
    }

    public void save(T entity) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.persist(entity);
        tx.commit();
    }

    public T update(T entity) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        T merged = em.merge(entity);
        tx.commit();
        return merged;
    }

    public void delete(ID id) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        T entity = em.find(entityClass, id);
        if (entity != null) {
            em.remove(entity);
        }
        tx.commit();
    }

    public void close() {
        if (em.isOpen()) {
            em.close();
        }
    }
}
