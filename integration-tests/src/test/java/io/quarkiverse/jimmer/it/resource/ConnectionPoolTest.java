package io.quarkiverse.jimmer.it.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

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

	private long activeBefore;

	@BeforeEach
	void snapshot() {
		activeBefore = agroalDataSource.getMetrics().activeCount();
	}

	@Test
	void singleConnectionPerTransactionUnderLoad() {
		// Pool max-size=8. If 3 ops within one JTA TX each acquired a separate connection
		// without returning it, we'd exhaust the pool after ~2 TXs (3 ops * 3 TXs = 9 > 8).
		// Completing 30 TXs without AcquisitionTimeoutException proves connection reuse and return.
		for (int i = 0; i < 30; i++) {
			QuarkusTransaction.requiringNew().run(() -> {
				bookRepository.findAll();
				bookStoreRepository.findAll();
				treeNodeRepository.findAll();
			});
		}
		assertEquals(activeBefore, agroalDataSource.getMetrics().activeCount(),
				"Active connection count must match baseline after 30 sequential 3-op transactions");
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
		// 50 TXs * 3 ops each. Pool max-size=8. Any leak would cause timeout by TX ~3.
		for (int i = 0; i < 50; i++) {
			QuarkusTransaction.requiringNew().run(() -> {
				bookRepository.findAll();
				bookStoreRepository.findAll();
				treeNodeRepository.findAll();
			});
		}

		assertEquals(activeBefore, agroalDataSource.getMetrics().activeCount(),
				"No connection leaks after 50 sequential multi-op transactions");
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
