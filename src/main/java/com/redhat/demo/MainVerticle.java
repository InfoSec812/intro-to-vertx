package com.redhat.demo;

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
import io.vertx.ext.web.handler.StaticHandler;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> start) {
        // vertx.deployVerticle(new HelloVerticle());
        vertx.deployVerticle("Hello.groovy");
        vertx.deployVerticle("Hello.js");

        Router router = Router.router(vertx);

        router.get("/api/v1/hello").handler(this::helloHandler);
        router.get("/api/v1/hello/:name").handler(this::helloByNameHandler);
        router.route().handler(StaticHandler.create("web"));

        doConfig(start, router);
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
