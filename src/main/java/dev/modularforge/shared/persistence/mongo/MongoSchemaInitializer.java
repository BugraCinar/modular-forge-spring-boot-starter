package dev.modularforge.shared.persistence.mongo;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Creates collections before transactions and checks that transactions are supported. */
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@Component
@Profile("mongodb")
public class MongoSchemaInitializer implements ApplicationRunner {
    private final MongoTemplate template;
    private final MongoMappingContext context;

    public MongoSchemaInitializer(MongoTemplate template, MongoMappingContext context) {
        this.template = template;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Document hello = template.executeCommand(new Document("hello", 1));
        if (!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) {
            throw new IllegalStateException("MongoDB requires a replica set or sharded cluster for account/token transactions");
        }
        var resolver = new MongoPersistentEntityIndexResolver(context);
        for (var entity : context.getPersistentEntities()) {
            if (!entity.isAnnotationPresent(org.springframework.data.mongodb.core.mapping.Document.class)) continue;
            String collection = entity.getCollection();
            if (!template.collectionExists(collection)) template.createCollection(collection);
            var indexes = template.indexOps(entity.getType());
            resolver.resolveIndexFor(entity.getType()).forEach(indexes::ensureIndex);
            if (entity.getPersistentProperty("expiryDate") != null) indexes.ensureIndex(new Index().on("expiryDate", Sort.Direction.ASC));
            if (entity.getPersistentProperty("createdAt") != null) indexes.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC));
            if (entity.getPersistentProperty("userId") != null && entity.getPersistentProperty("role") != null) {
                indexes.ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("role", Sort.Direction.ASC));
            }
            Document last = template.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1), Document.class, collection);
            long sequence = last != null && last.get("_id") instanceof Number id ? id.longValue() : 0L;
            template.upsert(Query.query(Criteria.where("_id").is(collection)), new Update().max("sequence", sequence), "repository_sequences");
        }
    }
}
