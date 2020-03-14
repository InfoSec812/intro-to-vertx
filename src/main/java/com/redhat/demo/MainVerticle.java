package com.redhat.demo;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

public class MainVerticle extends AbstractVerticle {

    final JsonObject loadedConfig = new JsonObject();

    @Override
    public void start(Promise<Void> start) {

        // Sequential Composition - Do A, Then B, Then C . . . . Handle errors
        // https://vertx.io/docs/vertx-core/java/#_sequential_composition
        doConfig()
            .compose(this::storeConfig)
            .compose(this::doDatabaseMigrations)
            .compose(this::configureRouter)
            .compose(this::startHttpServer)
            .compose(this::deployOtherVerticles)
            .setHandler(start::handle);
    }

    /**
     * Deploy our other {@link io.vertx.core.Verticle}s in concurrently
     * https://vertx.io/docs/vertx-core/java/#_concurrent_composition
     * @param server The {@link HttpServer} instance (Not used in this method)
     * @return A {@link Future} which is resolved once both of the Verticles are deployed
     */
    Future<Void> deployOtherVerticles(HttpServer server) {
        Future<String> helloGroovy = Future.future(promise -> vertx.deployVerticle("Hello.groovy", promise));
        Future<String> helloJs = Future.future(promise -> vertx.deployVerticle("Hello.js", promise));

        return CompositeFuture.all(helloGroovy, helloJs).mapEmpty();
    }

    Future<HttpServer> startHttpServer(Router router) {
        JsonObject http = loadedConfig.getJsonObject("http");
        int httpPort = http.getInteger("port");
        HttpServer server = vertx.createHttpServer().requestHandler(router);

        return Future.<HttpServer>future(promise -> server.listen(httpPort, promise));
    }

    Future<Router> configureRouter(Void unused) {
        Router router = Router.router(vertx);

        SessionStore store = LocalSessionStore.create(vertx);
        router.route().handler(LoggerHandler.create());
        router.route().handler(SessionHandler.create(store));
        router.route().handler(CorsHandler.create("localhost"));
        router.route().handler(CSRFHandler.create("QBR2QTlCvBaAugUBYdd6uWHkx4qA5yaVyxX/GyIgX0xwD71U1KamTWfyBmSgt3VHefeaNrdqdbvh"));
        router.get("/api/v1/hello").handler(this::helloHandler);
        router.get("/api/v1/hello/:name").handler(this::helloByNameHandler);
        router.route().handler(StaticHandler.create("web"));

        return Promise.succeededPromise(router).future();
    }

    Future<Void> storeConfig(JsonObject config) {
        loadedConfig.mergeIn(config);
        return Promise.<Void>succeededPromise().future();
    }

    void handleMigrationResult(Promise<Void> start, AsyncResult<Void> result) {
        if (result.failed()) {
            start.fail(result.cause());
        }
    }

    Future<Void> doDatabaseMigrations(Void unused) {
        JsonObject dbConfig = loadedConfig.getJsonObject("db", new JsonObject());
        String url = dbConfig.getString("url", "jdbc:postgresql://127.0.0.1:5432/todo");
        String adminUser = dbConfig.getString("admin_user", "postgres");
        String adminPass = dbConfig.getString("admin_pass", "introduction");
        Flyway flyway = Flyway.configure().dataSource(url, adminUser, adminPass).load();

        try {
            flyway.migrate();
            return Promise.<Void>succeededPromise().future();
        } catch (FlywayException fe) {
            return Promise.<Void>failedPromise(fe).future();
        }
    }

    /**
     * Set up and execute the {@link ConfigRetriever} to load the configuration for the application
     * @param start The {@link Promise} which is to be resolved as this Verticle loads
     * @param router The {@link Router} for the REST API paths
     */
    Future<JsonObject> doConfig() {
        ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "config.json"));
        ConfigStoreOptions cliConfig = new ConfigStoreOptions()
                .setType("json")
                .setConfig(config());

        ConfigRetrieverOptions opts = new ConfigRetrieverOptions()
                .addStore(defaultConfig)
                .addStore(cliConfig);

        ConfigRetriever cfgRetriever = ConfigRetriever.create(vertx, opts);

        return Future.future(promise -> cfgRetriever.getConfig(promise));
    }

    /**
     * A handler for requests to the `/api/v1/hello` REST endpoint
     * @param ctx The {@link RoutingContext} of the request
     */
    void helloHandler(RoutingContext ctx) {
        vertx.eventBus().request("hello.vertx.addr", "", reply -> {
            ctx.request().response().end((String)reply.result().body());
        });
    }

    /**
     * A handler for requests to the `/api/v1/hello/:name` REST endpoint
     * @param ctx The {@link RoutingContext} of the request
     */
    void helloByNameHandler(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        vertx.eventBus().request("hello.named.addr", name, reply -> {
            ctx.request().response().end((String)reply.result().body());
        });
    }
}
