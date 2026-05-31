package io.quarkiverse.jimmer.it.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.it.repository.TreeNodeRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ConnectionPoolTest {

	@Inject
	AgroalDataSource agroalDataSource;

	@Inject
	BookRepository bookRepository;

	@Inject
	BookStoreRepository bookStoreRepository;

	@Inject
	TreeNodeRepository treeNodeRepository;

	@Inject
	TransactionManager transactionManager;

	private long acquireBefore;
	private long activeBefore;

	@BeforeEach
	void snapshot() {
		acquireBefore = agroalDataSource.getMetrics().acquireCount();
		activeBefore = agroalDataSource.getMetrics().activeCount();
	}

	@Test
	void singleConnectionAcquiredForMultipleOpsInOneTransaction() {
		QuarkusTransaction.requiringNew().run(() -> {
			bookRepository.findAll();
			bookStoreRepository.findAll();
			treeNodeRepository.findAll();
		});

		long delta = agroalDataSource.getMetrics().acquireCount() - acquireBefore;
		assertEquals(1L, delta,
				"3 Jimmer ops in one JTA transaction must acquire exactly 1 connection, got " + delta);
	}

	@Test
	void connectionReturnedToPoolAfterCommit() {
		QuarkusTransaction.requiringNew().run(() -> {
			bookRepository.findAll();
			bookStoreRepository.findAll();
		});

		assertEquals(activeBefore, agroalDataSource.getMetrics().activeCount(),
				"Active count must return to baseline after commit");
	}

	@Test
	void connectionReturnedToPoolAfterRollback() {
		try {
			QuarkusTransaction.requiringNew().run(() -> {
				bookRepository.findAll();
				throw new RuntimeException("forced rollback");
			});
		} catch (RuntimeException ignored) {
		}

		assertEquals(activeBefore, agroalDataSource.getMetrics().activeCount(),
				"Active count must return to baseline after rollback");
	}

	@Test
	void noConnectionLeakOverManySequentialTransactions() {
		for (int i = 0; i < 20; i++) {
			QuarkusTransaction.requiringNew().run(() -> {
				bookRepository.findAll();
				bookStoreRepository.findAll();
				treeNodeRepository.findAll();
			});
		}

		assertEquals(activeBefore, agroalDataSource.getMetrics().activeCount(),
				"No connection leaks after 20 sequential multi-op transactions");
		long acquired = agroalDataSource.getMetrics().acquireCount() - acquireBefore;
		assertEquals(20L, acquired,
				"Expected exactly 20 acquisitions (1 per transaction), got " + acquired);
	}

	@Test
	void noConnectionLeakUnderConcurrentTransactions() throws InterruptedException {
		int threads = 4;
		int txPerThread = 5;
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<String> error = new AtomicReference<>();
		ExecutorService executor = Executors.newFixedThreadPool(threads);

		for (int i = 0; i < threads; i++) {
			executor.submit(() -> {
				try {
					for (int j = 0; j < txPerThread; j++) {
						QuarkusTransaction.requiringNew().run(() -> {
							bookRepository.findAll();
							bookStoreRepository.findAll();
						});
					}
				} catch (Exception e) {
					error.set(e.getMessage());
				} finally {
					done.countDown();
				}
			});
		}

		assertTrue(done.await(30, TimeUnit.SECONDS), "Concurrent transactions timed out");
		executor.shutdown();

		if (error.get() != null) {
			throw new AssertionError("Error in concurrent thread: " + error.get());
		}

		assertEquals(activeBefore, agroalDataSource.getMetrics().activeCount(),
				"No connection leaks after " + threads * txPerThread + " concurrent transactions");
	}
}
