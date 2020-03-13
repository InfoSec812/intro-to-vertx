package com.redhat.demo;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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

    @Override
    public void start(Promise<Void> start) {
        // vertx.deployVerticle(new HelloVerticle());
        vertx.deployVerticle("Hello.groovy");
        vertx.deployVerticle("Hello.js");

        Handler<AsyncResult<Void>> dbMigrationResultHandler = result -> this.handleMigrationResult(start, result);

        vertx.executeBlocking(this::doDatabaseMigrations, dbMigrationResultHandler);

        Router router = Router.router(vertx);

        SessionStore store = LocalSessionStore.create(vertx);
        router.route().handler(LoggerHandler.create());
        router.route().handler(SessionHandler.create(store));
        router.route().handler(CorsHandler.create("localhost"));
        router.route().handler(CSRFHandler.create("QBR2QTlCvBaAugUBYdd6uWHkx4qA5yaVyxX/GyIgX0xwD71U1KamTWfyBmSgt3VHefeaNrdqdbvh"));
        router.get("/api/v1/hello").handler(this::helloHandler);
        router.get("/api/v1/hello/:name").handler(this::helloByNameHandler);
        router.route().handler(StaticHandler.create("web"));

        doConfig(start, router);
    }

    void handleMigrationResult(Promise<Void> start, AsyncResult<Void> result) {
        if (result.failed()) {
            start.fail(result.cause());
        }
    }

    void doDatabaseMigrations(Promise<Void> promise) {
        Flyway flyway = Flyway.configure().dataSource("jdbc:postgresql://127.0.0.1:5432/todo", "postgres", "introduction").load();

        try {
            flyway.migrate();
            promise.complete();
        } catch (FlywayException fe) {
            promise.fail(fe);
        }
    }

    /**
     * Set up and execute the {@link ConfigRetriever} to load the configuration for the application
     * @param start The {@link Promise} which is to be resolved as this Verticle loads
     * @param router The {@link Router} for the REST API paths
     */
    private void doConfig(Promise<Void> start, Router router) {
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

        Handler<AsyncResult<JsonObject>> handler = asyncResult -> this.handleConfigResults(start, router, asyncResult);
        cfgRetriever.getConfig(handler);
    }

    /**
     * When the {@link ConfigRetriever} resolves, this method handles those results
     * @param start The {@link Promise} to be resolved either successfully or failed when the configuration is loaded and the HTTP server is created
     * @param router The {@link Router} which is configured to handle the HTTP requests
     * @param asyncResult The {@link AsyncResult}, potentially containing a {@link JsonObject} with the loaded configuration
     */
    void handleConfigResults(Promise<Void> start, Router router, AsyncResult<JsonObject> asyncResult) {

        if (asyncResult.succeeded()) {
            JsonObject config = asyncResult.result();
            JsonObject http = config.getJsonObject("http");
            int httpPort = http.getInteger("port");
            vertx.createHttpServer().requestHandler(router).listen(httpPort);
            start.complete();
        } else {
            start.fail("Unable to load configuration.");
        }
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
