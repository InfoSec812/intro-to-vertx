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

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> start) {
        vertx.deployVerticle(new HelloVerticle());

        Router router = Router.router(vertx);

        router.get("/api/v1/hello").handler(this::helloHandler);
        router.get("/api/v1/hello/:name").handler(this::helloByNameHandler);

        ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "config.json"));

        ConfigRetrieverOptions opts = new ConfigRetrieverOptions()
                .addStore(defaultConfig);

        ConfigRetriever cfgRetriever = ConfigRetriever.create(vertx, opts);

        Handler<AsyncResult<JsonObject>> handler = asyncResult -> this.handleConfigResults(start, router, asyncResult);
        cfgRetriever.getConfig(handler);
    }

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

    void helloHandler(RoutingContext ctx) {
        vertx.eventBus().request("hello.vertx.addr", "", reply -> {
            ctx.request().response().end((String)reply.result().body());
        });
    }

    void helloByNameHandler(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        vertx.eventBus().request("hello.named.addr", name, reply -> {
            ctx.request().response().end((String)reply.result().body());
        });
    }
}
