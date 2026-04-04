# Quarkus Jimmer Extension

[![](https://jitpack.io/v/sleepkqq/quarkus-jimmer-extension.svg)](https://jitpack.io/#sleepkqq/quarkus-jimmer-extension)

Quarkus extension for [Jimmer](https://github.com/babyfish-ct/jimmer) ORM. Supports both Java and Kotlin.

Based on [flynndi/quarkus-jimmer-extension](https://github.com/flynndi/quarkus-jimmer-extension).

## Dependency

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.sleepkqq.quarkus-jimmer-extension:quarkus-jimmer:1.0.0")
    annotationProcessor("org.babyfish.jimmer:jimmer-apt:0.10.6")
}
```

### Gradle (Groovy)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.sleepkqq.quarkus-jimmer-extension:quarkus-jimmer:1.0.0'
    annotationProcessor 'org.babyfish.jimmer:jimmer-apt:0.10.6'
}
```

## Versions

| Extension | Quarkus | Jimmer | Kotlin |
|-----------|---------|--------|--------|
| 1.0.0     | 3.32.3  | 0.10.6 | 2.3.20 |

## Usage

Refer to Jimmer documentation: https://github.com/babyfish-ct/jimmer

The Quarkus integration is mostly identical to Spring, with minor differences in DI annotations.

### Java

```java
// Repository
public interface BookRepository extends JRepository<Book, Long> {
}

// Service
@ApplicationScoped
public class BookService {

    @Inject
    BookRepository bookRepository;

    public Book findById(long id) {
        return bookRepository.findNullable(id);
    }
}

// Direct SqlClient usage
@Inject
JSqlClient jSqlClient;
```

### Kotlin

```kotlin
// Repository
@ApplicationScoped
class BookRepository : KRepository<Book, Long>

// Service
@ApplicationScoped
class BookService {

    @Inject
    @field:Default
    lateinit var bookRepository: BookRepository

    fun findById(id: Long): Book? {
        return bookRepository.findNullable(id)
    }
}

// Direct SqlClient usage
@Inject
@field:Default
lateinit var kSqlClient: KSqlClient
```

## License

Apache License 2.0
