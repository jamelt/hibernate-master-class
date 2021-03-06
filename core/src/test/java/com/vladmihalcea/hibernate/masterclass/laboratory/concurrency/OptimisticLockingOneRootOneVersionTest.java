package com.vladmihalcea.hibernate.masterclass.laboratory.concurrency;

import com.vladmihalcea.hibernate.masterclass.laboratory.util.AbstractTest;
import org.hibernate.Session;
import org.hibernate.StaleObjectStateException;
import org.junit.Test;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;

/**
 * OptimisticLockingOneRootOneVersionTest - Test to check optimistic checking on a single entity being updated by many threads
 *
 * @author Vlad Mihalcea
 */
public class OptimisticLockingOneRootOneVersionTest extends AbstractTest {

    private final CountDownLatch loadProductsLatch = new CountDownLatch(3);
    private final CountDownLatch aliceLatch = new CountDownLatch(1);

    public class AliceTransaction implements Runnable {

        @Override
        public void run() {
            try {
                doInTransaction(new TransactionCallable<Void>() {
                    @Override
                    public Void execute(Session session) {
                        try {
                            Product product = (Product) session.get(Product.class, 1L);
                            loadProductsLatch.countDown();
                            loadProductsLatch.await();
                            product.setQuantity(6L);
                            return null;
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            } catch (StaleObjectStateException expected) {
                LOGGER.info("Alice: Optimistic locking failure", expected);
            }
            aliceLatch.countDown();
        }
    }

    public class BobTransaction implements Runnable {

        @Override
        public void run() {
            try {
                doInTransaction(new TransactionCallable<Void>() {
                    @Override
                    public Void execute(Session session) {
                        try {
                            Product product = (Product) session.get(Product.class, 1L);
                            loadProductsLatch.countDown();
                            loadProductsLatch.await();
                            aliceLatch.await();
                            product.incrementLikes();
                            return null;
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            } catch (StaleObjectStateException expected) {
                LOGGER.info("Bob: Optimistic locking failure", expected);
            }
        }
    }

    public class VladTransaction implements Runnable {

        @Override
        public void run() {
            try {
                doInTransaction(new TransactionCallable<Void>() {
                    @Override
                    public Void execute(Session session) {
                        try {
                            Product product = (Product) session.get(Product.class, 1L);
                            loadProductsLatch.countDown();
                            loadProductsLatch.await();
                            aliceLatch.await();
                            product.setDescription("Plasma HDTV");
                            return null;
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            } catch (StaleObjectStateException expected) {
                LOGGER.info("Vlad: Optimistic locking failure", expected);
            }
        }
    }

    @Test
    public void testOptimisticLocking() throws InterruptedException {

        doInTransaction(new TransactionCallable<Void>() {
            @Override
            public Void execute(Session session) {
                Product product = new Product();
                product.setId(1L);
                product.setName("TV");
                product.setDescription("Plasma TV");
                product.setPrice(BigDecimal.valueOf(199.99));
                product.setQuantity(7L);
                session.persist(product);
                return null;
            }
        });

        Thread alice = new Thread(new AliceTransaction());
        Thread bob = new Thread(new BobTransaction());
        Thread vlad = new Thread(new VladTransaction());

        alice.start();
        bob.start();
        vlad.start();

        alice.join();
        bob.join();
        vlad.join();
    }

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Product.class
        };
    }

    /**
     * Product - Product
     *
     * @author Vlad Mihalcea
     */
    @Entity(name = "product")
    @Table(name = "product")
    public static class Product {

        @Id
        private Long id;

        @Column(unique = true, nullable = false)
        private String name;

        @Column(nullable = false)
        private String description;

        @Column(nullable = false)
        private BigDecimal price;

        private long quantity;

        private int likes;

        @Version
        private int version;

        public Product() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public long getQuantity() {
            return quantity;
        }

        public void setQuantity(long quantity) {
            this.quantity = quantity;
        }

        public int getLikes() {
            return likes;
        }

        public int incrementLikes() {
            return ++likes;
        }
    }
}
