package io.quarkiverse.jimmer.it.repository;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestJavaRepositoryTestCase {

    @Inject
    BookJavaRepository bookJavaRepository;

    @Inject
    UserRoleJavaRepository userRoleJavaRepository;

    @Test
    void testJavaRepositoryBean() {
        BookJavaRepository bookJavaRepositoryFromArc = Arc.container().instance(BookJavaRepository.class).get();
        UserRoleJavaRepository userRoleJavaRepositoryFromArc = Arc.container().instance(UserRoleJavaRepository.class).get();
        Assertions.assertEquals(bookJavaRepository, bookJavaRepositoryFromArc);
        Assertions.assertEquals(userRoleJavaRepository, userRoleJavaRepositoryFromArc);
    }

    @Test
    void testBookJavaRepositoryFindById() {
        Book book = bookJavaRepository.findById(1L);
        Assertions.assertNotNull(book);
        Assertions.assertEquals(1L, book.id());
    }

    @Test
    void testUserRoleJavaRepositoryFindById() {
        UserRole userRole = userRoleJavaRepository.findById(UUID.fromString(Constant.USER_ROLE_ID));
        Assertions.assertNotNull(userRole);
        Assertions.assertEquals(UUID.fromString(Constant.USER_ROLE_ID), userRole.id());
    }

    @Test
    void testUserRoleJavaRepositoryUuidV7Paging() {
        var first = userRoleJavaRepository.findUuidV7Slice(2, null);
        Assertions.assertEquals(java.util.List.of(
                UUID.fromString("0191c205-0000-7000-8000-000000000001"),
                UUID.fromString("0191c205-0001-7000-8000-000000000002")),
                first.getRows().stream().map(UserRole::id).toList());
        Assertions.assertEquals(UUID.fromString("0191c205-0001-7000-8000-000000000002"), first.getNextCursor());
        Assertions.assertTrue(first.getHasNext());

        var second = userRoleJavaRepository.findUuidV7Slice(2, first.getNextCursor());
        Assertions.assertEquals(java.util.List.of(
                UUID.fromString("0191c205-0002-7000-8000-000000000003"),
                UUID.fromString("0191c205-0003-7000-8000-000000000004")),
                second.getRows().stream().map(UserRole::id).toList());
        Assertions.assertEquals(UUID.fromString("0191c205-0003-7000-8000-000000000004"), second.getNextCursor());
        Assertions.assertTrue(second.getHasNext());

        var last = userRoleJavaRepository.findUuidV7Slice(2, second.getNextCursor());
        Assertions.assertEquals(java.util.List.of(UUID.fromString(Constant.USER_ROLE_ID)),
                last.getRows().stream().map(UserRole::id).toList());
        Assertions.assertNull(last.getNextCursor());
        Assertions.assertFalse(last.getHasNext());

        var page = userRoleJavaRepository.findUuidV7Page(2, null);
        Assertions.assertEquals(5, page.getTotalRowCount());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userRoleJavaRepository.findUuidV7Slice(0, null));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userRoleJavaRepository.findUuidV7Slice(1, UUID.randomUUID()));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> bookJavaRepository.findUuidV7Slice(1, null));
    }

    @Test
    void testMethodInBookJavaRepositoryFindById() {
        Book book = bookJavaRepository.methodInBookJavaRepositoryFindById(1L);
        Assertions.assertNotNull(book);
        Assertions.assertEquals(1L, book.id());
    }
}
