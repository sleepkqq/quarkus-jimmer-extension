package io.quarkiverse.jimmer.runtime.graal;

import org.babyfish.jimmer.impl.util.ClassCache;
import org.babyfish.jimmer.impl.util.PropCache;
import org.babyfish.jimmer.impl.util.StaticCache;
import org.babyfish.jimmer.impl.util.TypeCache;
import org.babyfish.jimmer.jackson.ConverterMetadata;
import org.babyfish.jimmer.meta.impl.Metadata;
import org.babyfish.jimmer.sql.association.meta.AssociationType;
import org.babyfish.jimmer.sql.ast.impl.table.AssociationTableProxyImpl;
import org.babyfish.jimmer.sql.ast.impl.table.TableProxies;
import org.babyfish.jimmer.sql.cache.CacheLoader;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;
import org.babyfish.jimmer.sql.runtime.ScalarProvider;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

/**
 * Jimmer's metadata value types (DtoMetadata, ConverterMetadata, AssociationType, ...) are
 * instantiated while Quarkus initializes the generated DTO/entity classes at build time, so their
 * instances end up in the image heap. The shared cache holders they reach (StaticCache and
 * siblings) must therefore be build-time initialized too, otherwise native-image rejects the
 * heap objects ("found in the image heap ... marked for initialization at image run time").
 *
 * Referenced via {@code .class} on purpose: a Jimmer rename surfaces as a compile error here
 * instead of a silent native-image regression.
 */
public final class JimmerNativeInitFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime(
                StaticCache.class,
                ClassCache.class,
                TypeCache.class,
                PropCache.class,
                Metadata.class,
                ConverterMetadata.class,
                DtoMetadata.class,
                AssociationType.class,
                ScalarProvider.class,
                CacheLoader.class,
                AssociationTableProxyImpl.class,
                TableProxies.class);
    }
}
