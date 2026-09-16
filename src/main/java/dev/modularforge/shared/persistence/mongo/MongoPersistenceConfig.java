package dev.modularforge.shared.persistence.mongo;

import org.bson.Document;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import java.time.LocalDateTime;

@Configuration(proxyBeanMethods = false)
@Profile("mongodb")
public class MongoPersistenceConfig {
    @Bean
    org.springframework.data.mongodb.core.mapping.MongoMappingContext mongoMappingContext(
            org.springframework.data.mongodb.MongoManagedTypes managedTypes,
            org.springframework.data.mongodb.core.convert.MongoCustomConversions conversions) {
        var context = new org.springframework.data.mongodb.core.mapping.MongoMappingContext() {
            @Override
            public org.springframework.data.mongodb.core.mapping.MongoPersistentProperty createPersistentProperty(
                    org.springframework.data.mapping.model.Property property,
                    org.springframework.data.mongodb.core.mapping.MongoPersistentEntity<?> owner,
                    org.springframework.data.mapping.model.SimpleTypeHolder simpleTypes) {
                return new org.springframework.data.mongodb.core.mapping.CachingMongoPersistentProperty(
                        property, owner, simpleTypes, org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy.INSTANCE) {
                    @Override
                    public boolean isVersionProperty() {
                        return isAnnotationPresent(jakarta.persistence.Version.class) || super.isVersionProperty();
                    }
                };
            }
        };
        context.setManagedTypes(managedTypes);
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        return context;
    }

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    BeforeConvertCallback<Object> mongoEntityLifecycle(com.mongodb.client.MongoClient client, MongoDatabaseFactory factory) {
        return (entity, collection) -> {
            if (entity instanceof Document) return entity;
            BeanWrapper bean = PropertyAccessorFactory.forBeanPropertyAccess(entity);
            if (bean.isWritableProperty("id") && bean.getPropertyValue("id") == null) {
                // Allocate outside the account transaction: rolled-back operations may leave gaps,
                // but unrelated logins must not contend on a transaction-held counter document.
                Document counter = client.getDatabase(factory.getMongoDatabase().getName())
                        .getCollection("repository_sequences").withWriteConcern(com.mongodb.WriteConcern.MAJORITY)
                        .findOneAndUpdate(com.mongodb.client.model.Filters.eq("_id", collection),
                                com.mongodb.client.model.Updates.inc("sequence", 1L),
                                new com.mongodb.client.model.FindOneAndUpdateOptions().upsert(true)
                                        .returnDocument(com.mongodb.client.model.ReturnDocument.AFTER));
                bean.setPropertyValue("id", ((Number) java.util.Objects.requireNonNull(counter).get("sequence")).longValue());
            }
            LocalDateTime now = LocalDateTime.now();
            for (String created : new String[]{"createdAt", "createdDate"}) {
                if (bean.isWritableProperty(created) && bean.getPropertyValue(created) == null) bean.setPropertyValue(created, now);
            }
            if (bean.isWritableProperty("updatedAt")) bean.setPropertyValue("updatedAt", now);
            return entity;
        };
    }
}
